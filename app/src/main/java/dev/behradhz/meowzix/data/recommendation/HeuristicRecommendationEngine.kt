package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.data.db.*
import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.recommendation.*
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Historical binding retained. All Smart queues and sections share the same local model/ranking. */
@Singleton
class HeuristicRecommendationEngine @Inject constructor(
    private val recommendationDao: RecommendationDao,
    private val historyDao: HistoryDao,
    private val audioFeatureExtractor: AudioFeatureExtractor,
    private val audioFeatureExtractionCoordinator: AudioFeatureExtractionCoordinator,
    private val settings: SettingsRepository,
    private val personalizationModel: PersonalizationModel,
    private val feedbackRepository: RecommendationFeedbackRepository,
) : RecommendationEngine {
    private val sectionGenerator = RecommendationSectionGenerator()
    private var explanationModelStamp: Pair<Long, Long>? = null
    private fun invalidateExplanationsIfChanged(state: PersonalizationModelState) = synchronized(explanationCache) {
        val stamp = state.historyFloorVersion to state.trainedAtEpochMs
        if (explanationModelStamp != stamp) { explanationCache.clear(); explanationModelStamp = stamp }
    }
    private val explanationCache = object : LinkedHashMap<UUID, Pair<Instant, List<RecommendationReason>>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Pair<Instant, List<RecommendationReason>>>?): Boolean = size > 300
    }

    override suspend fun generate(allowedTrackIds: List<UUID>?, currentTrackId: UUID?, timeBucket: TimeBucket, seed: Long): SmartQueue =
        withContext(Dispatchers.Default) {
            val ranked = rank(allowedTrackIds, currentTrackId, timeBucket, seed)
            val items = sectionGenerator.diverseOrder(ranked.pool, seed, ranked.pool.size)
            remember(items)
            val finalScores = items.associate { it.trackId to it.score.finalScore }
            SmartQueue(items.map { it.trackId }, ranked.pool.associate { candidate ->
                candidate.candidate.trackId to candidate.breakdown.copy(total = finalScores.getValue(candidate.candidate.trackId))
            }, ranked.eligibleTrackIds)
        }

    override suspend fun sections(currentTrackId: UUID?, seed: Long): List<RecommendationSection> = withContext(Dispatchers.Default) {
        val ranked = rank(null, currentTrackId, null, seed)
        val anchors = ranked.anchors.sortedByDescending { it.recent.strength + if (it.candidate.trackId == currentTrackId) 1.0 else 0.0 }.take(2)
        val sections = sectionGenerator.sections(ranked.pool, ranked.context, seed, anchors)
        remember(sections.flatMap { it.items })
        sections
    }

    override suspend fun continueTheVibe(anchorTrackId: UUID, limit: Int): List<RecommendationItem> = withContext(Dispatchers.Default) {
        val ranked = rank(null, anchorTrackId, null, System.nanoTime(), anchorTrackId)
        val anchor = ranked.anchors.firstOrNull { it.candidate.trackId == anchorTrackId } ?: return@withContext emptyList()
        sectionGenerator.continueTheVibe(ranked.pool, anchor, ranked.context, System.nanoTime(), limit).also(::remember)
    }

    override suspend fun whyThisSong(trackId: UUID): List<RecommendationReason> = withContext(Dispatchers.Default) {
        val currentState = safely { personalizationModel.state() }
        if (currentState != null) invalidateExplanationsIfChanged(currentState)
        synchronized(explanationCache) { explanationCache[trackId] }?.takeIf { Instant.now().epochSecond - it.first.epochSecond < 1_800 }?.let { return@withContext it.second }
        val ranked = rank(listOf(trackId), null, null, 0L)
        ranked.pool.firstOrNull { it.candidate.trackId == trackId }?.item?.score?.reasons.orEmpty()
    }

    private suspend fun rank(allowedTrackIds: List<UUID>?, currentTrackId: UUID?, bucketOverride: TimeBucket?, seed: Long, anchorId: UUID? = null): RankedPool {
        val now = Instant.now()
        val feedbackEffects = safely { feedbackRepository.snapshot(now) }?.effects(now).orEmpty()
        val time = ListeningEventSemantics.timeContext(now, ZoneId.systemDefault())
        val state = safely { personalizationModel.state() } ?: PersonalizationModelState()
        invalidateExplanationsIfChanged(state)
        val recent = historyDao.recommendationWindow(now.minusSeconds(28L * 86_400).toEpochMilli(), state.historyFloorVersion, 5_000)
        val starts = recent.filter { it.type == "PLAY_STARTED" }
        val recentIds = starts.take(20).mapNotNull { it.trackId.uuid() }
        val positiveIds = recent.filter { it.type in setOf("MANUAL_SELECTED", "REPLAYED", "PLAY_COMPLETED") }
            .mapNotNull { it.trackId.uuid() }.distinct().take(12)
        val feedbackPriorities = feedbackEffects.filterValues { it.moreLikeThis }.keys.toList()
        val priorities = (feedbackPriorities + recentIds + positiveIds + recommendationDao.favoriteTrackIds(96).mapNotNull { it.uuid() } +
            recommendationDao.preferredTrackIds(96).mapNotNull { it.uuid() }).distinct()
        // IDs are cheap to enumerate; only <=800 complete candidates/features are ever hydrated/scored.
        val offlineOnly = settings.networkPlaybackSettings.first().offlineMode
        val eligible = recommendationDao.eligibleTrackIds(offlineOnly).mapNotNull { it.uuid() }.toHashSet()
        val scoped = (allowedTrackIds ?: eligible.toList()).distinct().filter { it in eligible }
        val allowed = (if (scoped.size > 1) scoped.filterNot { it == currentTrackId } else scoped)
            .filterNot { feedbackEffects[it]?.excluded == true }
        val ids = reduceRecommendationCandidateIds(allowed, priorities, RecommendationConfig.CANDIDATE_LIMIT, seed)
        val hydrationIds = (ids + recentIds + listOfNotNull(currentTrackId, anchorId) + positiveIds.take(2) + feedbackPriorities.take(8)).distinct()
        if (hydrationIds.isEmpty()) return RankedPool(emptyList(), emptyList(), RecommendationContext(time.localHour, time.dayOfWeek.value, time.isWeekend, time.bucket, now))
        val strings = hydrationIds.map(UUID::toString)
        val tracks = recommendationDao.tracksByIds(strings)
        val trackById = tracks.associateBy { it.id }
        val sources = recommendationDao.activeSourcesForTracks(strings).groupBy { it.trackId }
        val global = recommendationDao.trackStatsForTracks(strings).associateBy { it.trackId }
        val bucket = bucketOverride ?: time.bucket
        val temporal = recommendationDao.timeStatsForTracks(strings, bucket.name).associateBy { it.trackId }
        val aggregates = recommendationDao.artistAlbumStats()
        val artistStats = aggregates.filter { !it.artist.isNullOrBlank() }.groupBy { it.artist!! }.mapValues { (_, rows) -> rows.fold(PreferenceSnapshot()) { stats, row -> stats + row.snapshot() } }
        val albumStats = aggregates.mapNotNull { row -> CausalTrainingDataset.albumKey(row.artist, row.album)?.let { it to row.snapshot() } }.toMap()
        // Match causal training: sequence features describe one active session, not yesterday's skips.
        val sessionId = recent.firstOrNull()?.takeIf {
            now.toEpochMilli() - it.occurredAtEpochMs in 0L..ListeningEventSemantics.SESSION_TIMEOUT_MS
        }?.sessionId
        val sessionStarts = starts.filter { it.sessionId == sessionId }.take(20)
        val outcomes = recent.filter { it.sessionId == sessionId && it.type in CausalTrainingDataset.finalTypes }
        val context = RecommendationContext(time.localHour, time.dayOfWeek.value, time.isWeekend, bucket, now,
            sessionId?.uuid(), sessionId?.let { historyDao.sessionPosition(it, state.historyFloorVersion) } ?: 0,
            sessionStarts.mapNotNull { it.trackId.uuid() }, sessionStarts.map { trackById[it.trackId]?.normalizedArtist ?: "" },
            outcomes.takeWhile { it.type == "SKIPPED_EARLY" }.size, currentTrackId, anchorId, offlineOnly = offlineOnly)
        val audio = recommendationDao.compatibleAudioVectorsForTracks(strings, audioFeatureExtractor.extractorName,
            audioFeatureExtractor.extractorVersion, audioFeatureExtractor.schemaVersion).mapNotNull { row ->
            val vector = try { AudioFeatureVectorCodec.decode(row.vectorBlob, AudioVectorFormat.valueOf(row.vectorFormat)) } catch (_: Exception) { null }
            vector?.takeIf(AudioFeatureSchema::isCompatible)?.let { row.trackId to it }
        }.toMap()
        val candidates = tracks.map { track ->
            val trackSources = sources[track.id].orEmpty()
            val stats = global[track.id]
            val timeStats = temporal[track.id]
            SmartCandidate(UUID.fromString(track.id), track.normalizedArtist, track.favorite, track.hidden,
                trackSources.isNotEmpty(), trackSources.any { it.availability == SourceAvailability.AVAILABLE_LOCAL && (!it.localPath.isNullOrBlank() || !it.contentUri.isNullOrBlank()) },
                stats?.snapshot() ?: PreferenceSnapshot(), timeStats?.snapshot() ?: PreferenceSnapshot(),
                stats?.lastPlayedAtEpochMs?.let(Instant::ofEpochMilli), track.normalizedArtist?.let(context.recentArtistIds::indexOf)?.takeIf { it >= 0 },
                recent.count { it.sessionId == sessionId && it.trackId == track.id && it.type == "SKIPPED_EARLY" && it.occurredAtEpochMs >= now.minusSeconds(1_800).toEpochMilli() })
        }
        val valid = CandidateGenerator.generate(candidates.filter { it.trackId in ids }, currentTrackId, offlineOnly)
        val anchorCandidates = candidates.filter { it.trackId == anchorId || it.trackId in positiveIds.take(2) || it.trackId == currentTrackId }
        val scoring = (valid + anchorCandidates).distinctBy { it.trackId }
        val featureVectors = scoring.map { candidate ->
            val track = trackById.getValue(candidate.trackId.toString())
            TrackFeatures(candidate.trackId, PersonalizationFeatureVectorizer.vectorize(context, TrackPersonalizationFeatures(
                candidate.trackId, track.normalizedArtist, track.favorite, track.durationMs, audio[track.id],
                candidate.global, candidate.time, candidate.lastPlayedAt, track.normalizedArtist?.let(artistStats::get),
                CausalTrainingDataset.albumKey(track.normalizedArtist, track.album)?.let(albumStats::get))))
        }
        val learned = safely { personalizationModel.score(context, featureVectors) }?.associateBy { it.trackId }.orEmpty()
        val recentEvidence = recent.groupBy { it.trackId }.mapValues { (_, events) ->
            fun decayed(type: String) = events.filter { it.type == type }.sumOf { exp(-(now.toEpochMilli() - it.occurredAtEpochMs).coerceAtLeast(0L) / (7.0 * 86_400_000.0)) }
            RecentPositiveEvidence(decayed("MANUAL_SELECTED"), decayed("REPLAYED"), decayed("PLAY_COMPLETED"))
        }
        val ranked = scoring.map { candidate ->
            val track = trackById.getValue(candidate.trackId.toString())
            val (score, breakdown) = RecommendationRanking.score(
                candidate,
                context,
                learned[candidate.trackId],
                state.sampleCount,
                feedbackEffects[candidate.trackId] ?: RecommendationFeedbackEffect(),
            )
            RankedRecommendationCandidate(candidate, RecommendationItem(candidate.trackId, score),
                SimilarityProfile(track.normalizedArtist, track.album, audio[track.id]), track.title,
                recentEvidence[track.id] ?: RecentPositiveEvidence(),
                track.normalizedArtist?.let(artistStats::get)?.let { PersonalizationFeatureVectorizer.affinity(it) } ?: 0.0, breakdown)
        }
        val validSet = valid.mapTo(hashSetOf()) { it.trackId }
        // Small opportunistic work is bounded; bulk analysis is an explicit maintenance action.
        // Optional background analysis must never prevent a usable recommendation/Smart queue.
        safely { audioFeatureExtractionCoordinator.schedule(valid.take(20).map { it.trackId }) }
        return RankedPool(ranked.filter { it.candidate.trackId in validSet }, ranked.filter { it.candidate in anchorCandidates }, context, allowed.toSet())
    }

    private fun remember(items: List<RecommendationItem>) = synchronized(explanationCache) {
        val now = Instant.now()
        items.forEach { explanationCache[it.trackId] = now to it.score.reasons }
    }
    private suspend fun <T> safely(block: suspend () -> T): T? = try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
    private fun String.uuid(): UUID? = try { UUID.fromString(this) } catch (_: IllegalArgumentException) { null }
    private data class RankedPool(val pool: List<RankedRecommendationCandidate>, val anchors: List<RankedRecommendationCandidate>, val context: RecommendationContext, val eligibleTrackIds: Set<UUID> = emptySet())
}

private fun TrackPreferenceStatsEntity.snapshot() = PreferenceSnapshot(totalStarts, totalCompletions, earlySkips, manualSelections, replays, lateSkips)
private fun TrackTimePreferenceEntity.snapshot() = PreferenceSnapshot(starts, completions, earlySkips, manualSelections)
private fun ArtistAlbumPreferenceRow.snapshot() = PreferenceSnapshot(starts, completions, earlySkips, manualSelections, replays, lateSkips)
private operator fun PreferenceSnapshot.plus(other: PreferenceSnapshot) = PreferenceSnapshot(starts + other.starts, completions + other.completions,
    earlySkips + other.earlySkips, manualSelections + other.manualSelections, replays + other.replays, lateSkips + other.lateSkips)
