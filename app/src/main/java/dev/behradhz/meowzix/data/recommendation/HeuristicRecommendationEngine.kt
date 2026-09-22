package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.recommendation.AdaptiveScorer
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
    private val libraryDao: LibraryDao,
    private val historyDao: HistoryDao,
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
        val allowed = allowedTrackIds?.mapTo(mutableSetOf(), UUID::toString)
        val tracks = libraryDao.allTracks().filter { allowed == null || it.id in allowed }
        val sources = libraryDao.allSources().groupBy { it.trackId }
        val global = historyDao.allTrackStats().associateBy { it.trackId }
        val time = historyDao.timeStatsForBucket(timeBucket.name).associateBy { it.trackId }
        val recentEvents = historyDao.recentSelectionEvents(50)
        val recentTrackIds = recentEvents.filter { it.type == ListeningEventType.PLAY_STARTED.name }.map { it.trackId }
        val trackById = tracks.associateBy { it.id }
        val recentArtists = recentTrackIds.mapNotNull { trackById[it]?.normalizedArtist }.take(5)
        val sessionCutoff = now.minusSeconds(30 * 60).toEpochMilli()
        val skips = recentEvents.filter {
            it.type == ListeningEventType.SKIPPED_EARLY.name && it.occurredAtEpochMs >= sessionCutoff
        }.groupingBy { it.trackId }.eachCount()
        val candidates = tracks.map { track ->
            val trackSources = sources[track.id].orEmpty().filter { it.availability != SourceAvailability.MISSING }
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
                ),
            )
        }.toMap()
        val learned = runCatching { personalizationModel.scoreBatch(featureVectors) }.getOrDefault(emptyMap())
        val scores = valid.associate { candidate ->
            candidate.trackId to scorer.score(candidate, now, learned[candidate.trackId])
        }
        return SmartQueue(SmartSelector.order(scores, seed), scores)
    }
}
