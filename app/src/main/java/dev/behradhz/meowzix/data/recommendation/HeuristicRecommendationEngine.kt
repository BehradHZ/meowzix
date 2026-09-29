package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.data.db.AudioFeatureVectorEntity
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.db.RecommendationDao
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.recommendation.AdaptiveScorer
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureVectorCodec
import dev.behradhz.meowzix.domain.recommendation.AudioVectorFormat
import dev.behradhz.meowzix.domain.recommendation.CandidateGenerator
import dev.behradhz.meowzix.domain.recommendation.PersonalizationFeatureVectorizer
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import dev.behradhz.meowzix.domain.recommendation.PreferenceSnapshot
import dev.behradhz.meowzix.domain.recommendation.RecommendationContext
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import dev.behradhz.meowzix.domain.recommendation.SmartCandidate
import dev.behradhz.meowzix.domain.recommendation.SmartQueue
import dev.behradhz.meowzix.domain.recommendation.SmartSelector
import dev.behradhz.meowzix.domain.recommendation.TrackPersonalizationFeatures
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class HeuristicRecommendationEngine @Inject constructor(
    private val recommendationDao: RecommendationDao,
    private val historyDao: HistoryDao,
    private val audioFeatureExtractor: AudioFeatureExtractor,
    private val audioFeatureExtractionCoordinator: AudioFeatureExtractionCoordinator,
    private val settings: SettingsRepository,
    private val personalizationModel: PersonalizationModel,
    private val personalizationTrainer: PersonalizationTrainer,
) : RecommendationEngine {
    private val scorer = AdaptiveScorer()

    override suspend fun generate(
        allowedTrackIds: List<UUID>?,
        currentTrackId: UUID?,
        timeBucket: TimeBucket,
        seed: Long,
    ): SmartQueue {
        val now = Instant.now()
        val localNow = ZonedDateTime.now()
        val recentEvents = historyDao.recentSelectionEvents(RECENT_EVENT_LIMIT)

        val priorityIds = buildList {
            recentEvents.mapNotNullTo(this) { it.trackId.toUuidOrNull() }
            recommendationDao.favoriteTrackIds(PRIORITY_BUCKET_LIMIT)
                .mapNotNullTo(this) { it.toUuidOrNull() }
            recommendationDao.preferredTrackIds(PRIORITY_BUCKET_LIMIT)
                .mapNotNullTo(this) { it.toUuidOrNull() }
        }
        val allowed = allowedTrackIds ?: buildList {
            addAll(priorityIds)
            recommendationDao.discoveryTrackIds(DISCOVERY_POOL_LIMIT)
                .mapNotNullTo(this) { it.toUuidOrNull() }
        }
        val candidateIds = reduceRecommendationCandidateIds(
            allowedTrackIds = allowed,
            priorityTrackIds = priorityIds,
            limit = CANDIDATE_LIMIT,
            seed = seed,
        )
        if (candidateIds.isEmpty()) return SmartQueue(emptyList(), emptyMap())

        val candidateIdStrings = candidateIds.map(UUID::toString)
        val tracks = recommendationDao.tracksByIds(candidateIdStrings)
        val sources = recommendationDao.activeSourcesForTracks(candidateIdStrings).groupBy { it.trackId }
        val global = recommendationDao.trackStatsForTracks(candidateIdStrings).associateBy { it.trackId }
        val time = recommendationDao.timeStatsForTracks(candidateIdStrings, timeBucket.name).associateBy { it.trackId }

        val recentTrackIds = recentEvents
            .filter { it.type == ListeningEventType.PLAY_STARTED.name }
            .map { it.trackId }
        val trackById = tracks.associateBy { it.id }
        val recentArtists = recentTrackIds.mapNotNull { trackById[it]?.normalizedArtist }.take(5)
        val sessionCutoff = now.minusSeconds(30 * 60).toEpochMilli()
        val skips = recentEvents.filter {
            it.type == ListeningEventType.SKIPPED_EARLY.name && it.occurredAtEpochMs >= sessionCutoff
        }.groupingBy { it.trackId }.eachCount()

        val candidates = tracks.map { track ->
            val trackSources = sources[track.id].orEmpty()
            val trackStats = global[track.id]
            val bucketStats = time[track.id]
            SmartCandidate(
                trackId = UUID.fromString(track.id),
                artist = track.normalizedArtist,
                favorite = track.favorite,
                hidden = track.hidden,
                usable = trackSources.isNotEmpty(),
                offline = trackSources.any { it.availability == SourceAvailability.AVAILABLE_LOCAL },
                global = PreferenceSnapshot(
                    starts = trackStats?.totalStarts ?: 0,
                    completions = trackStats?.totalCompletions ?: 0,
                    earlySkips = trackStats?.earlySkips ?: 0,
                    manualSelections = trackStats?.manualSelections ?: 0,
                    replays = trackStats?.replays ?: 0,
                ),
                time = PreferenceSnapshot(
                    starts = bucketStats?.starts ?: 0,
                    completions = bucketStats?.completions ?: 0,
                    earlySkips = bucketStats?.earlySkips ?: 0,
                    manualSelections = bucketStats?.manualSelections ?: 0,
                ),
                lastPlayedAt = trackStats?.lastPlayedAtEpochMs?.let(Instant::ofEpochMilli),
                recentArtistDistance = track.normalizedArtist?.let(recentArtists::indexOf)?.takeIf { it >= 0 },
                sessionEarlySkips = skips[track.id] ?: 0,
            )
        }
        val offlineOnly = settings.networkPlaybackSettings.first().offlineMode
        val valid = CandidateGenerator.generate(candidates, currentTrackId, offlineOnly)
        if (valid.isEmpty()) return SmartQueue(emptyList(), emptyMap())

        // Missing acoustic features are optional. Extraction is queued in a serial background lane
        // and this generation uses whatever compatible vectors already exist right now.
        audioFeatureExtractionCoordinator.schedule(valid.map { it.trackId })
        val validIds = valid.map { it.trackId.toString() }
        val audio = recommendationDao.compatibleAudioVectorsForTracks(
            validIds,
            audioFeatureExtractor.extractorName,
            audioFeatureExtractor.extractorVersion,
            audioFeatureExtractor.schemaVersion,
        ).mapNotNull { row -> row.decodeValues()?.let { row.trackId to it } }.toMap()

        // A stale/missing learned model never blocks queue creation. Rebuild happens off the
        // playback path, and this generation simply falls back to the deterministic heuristic.
        personalizationTrainer.refreshIfStale()
        val context = RecommendationContext(
            localHour = localNow.hour,
            dayOfWeek = localNow.dayOfWeek.value,
            isWeekend = localNow.dayOfWeek == DayOfWeek.SATURDAY || localNow.dayOfWeek == DayOfWeek.SUNDAY,
            timeBucket = timeBucket,
        )
        val featureVectors = valid.mapNotNull { candidate ->
            val track = trackById[candidate.trackId.toString()] ?: return@mapNotNull null
            candidate.trackId to PersonalizationFeatureVectorizer.vectorize(
                context,
                TrackPersonalizationFeatures(
                    trackId = candidate.trackId,
                    normalizedArtist = track.normalizedArtist,
                    favorite = track.favorite,
                    durationMs = track.durationMs,
                    audioFeatures = audio[track.id],
                ),
            )
        }.toMap()
        val learned = runCatching { personalizationModel.scoreBatch(featureVectors) }.getOrDefault(emptyMap())
        val scores = valid.associate { candidate ->
            candidate.trackId to scorer.score(candidate, now, learned[candidate.trackId])
        }
        return SmartQueue(SmartSelector.order(scores, seed), scores)
    }

    private fun AudioFeatureVectorEntity.decodeValues(): DoubleArray? = runCatching {
        AudioFeatureVectorCodec.decode(vectorBlob, AudioVectorFormat.valueOf(vectorFormat))
    }.getOrNull()

    private fun String.toUuidOrNull(): UUID? = runCatching(UUID::fromString).getOrNull()

    private companion object {
        const val CANDIDATE_LIMIT = 800
        const val PRIORITY_BUCKET_LIMIT = 96
        const val RECENT_EVENT_LIMIT = 100
        const val DISCOVERY_POOL_LIMIT = 2_400
    }
}
