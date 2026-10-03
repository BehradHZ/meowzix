package dev.behradhz.meowzix.domain.recommendation

import java.time.Instant
import java.util.UUID

data class TrainingEvent(
    val sequence: Long,
    val event: dev.behradhz.meowzix.domain.history.ListeningEvent,
    val sessionId: UUID,
    val playbackInstanceId: UUID,
)
data class TrainingTrack(val trackId: UUID, val artist: String?, val album: String?, val durationMs: Long)
data class TrainingAudio(val values: DoubleArray, val generatedAt: Instant, val sourceId: UUID, val extractorVersion: String)

/** Replays insertion-ordered events. Every vector is frozen BEFORE its decision's first event. */
object CausalTrainingDataset {
    val finalTypes = setOf("PLAY_COMPLETED", "PLAY_STOPPED", "SKIPPED_EARLY", "SKIPPED_LATE", "QUEUE_REMOVED")

    private val decisionTypes = setOf("MANUAL_SELECTED", "AUTO_SELECTED", "PLAY_STARTED", "REPLAYED", "FAVORITED", "UNFAVORITED", "QUEUE_REMOVED")

    /** Pure list adapter used by tests. Production feeds insertion-ordered pages to Accumulator. */
    fun build(events: List<TrainingEvent>, tracks: Map<UUID, TrainingTrack>, audio: Map<UUID, TrainingAudio> = emptyMap(), floor: Long = 0L, after: Long = 0L): List<TrainingSample> {
        val ordered = events.sortedBy { it.sequence }
        val firstTerminals = ordered.filter { it.event.type.name in finalTypes }.distinctBy { it.playbackInstanceId }
        val eligible = firstTerminals.filter { it.sequence > maxOf(floor, after) }.mapTo(hashSetOf()) { it.playbackInstanceId }
        val starts = ordered.distinctBy { it.playbackInstanceId }.associateBy { it.playbackInstanceId }
        eligible.removeAll { id -> starts[id]?.let { it.sequence <= floor || it.event.type.name !in decisionTypes } != false }
        val terminalSequences = firstTerminals.mapTo(hashSetOf()) { it.sequence }
        val accumulator = Accumulator(tracks, audio, eligible, floor)
        ordered.filter { it.event.type.name !in finalTypes || it.sequence in terminalSequences }.forEach(accumulator::accept)
        return accumulator.samples()
    }

    /** Events must be ordered and terminal rows deduplicated. State is proportional to tracks/sessions,
     * active decisions, and the bounded eligible outcome set, rather than total event count. */
    class Accumulator(
        private val tracks: Map<UUID, TrainingTrack>,
        private val audio: Map<UUID, TrainingAudio>,
        private val eligibleInstances: Set<UUID>,
        private val floor: Long,
    ) {
        private val global = mutableMapOf<UUID, PreferenceSnapshot>()
        private val bucket = mutableMapOf<Pair<UUID, dev.behradhz.meowzix.domain.history.TimeBucket>, PreferenceSnapshot>()
        private val artists = mutableMapOf<String, PreferenceSnapshot>()
        private val albums = mutableMapOf<String, PreferenceSnapshot>()
        private val lastPlayed = mutableMapOf<UUID, Instant>()
        private val favorites = mutableSetOf<UUID>()
        private val knownFavorites = mutableSetOf<UUID>()
        private val sessions = mutableMapOf<UUID, Session>()
        private val snapshots = mutableMapOf<UUID, Snapshot>()
        private val actions = mutableMapOf<UUID, MutableList<TrainingEvent>>()
        private val finalized = mutableSetOf<UUID>()
        private val samples = ArrayList<TrainingSample>()
        private val decisions = mutableMapOf<UUID, dev.behradhz.meowzix.domain.history.ListeningEvent>()

        fun accept(row: TrainingEvent) {
            if (row.sequence <= floor) return
            val event = row.event
            val track = tracks[event.trackId] ?: return
            val type = event.type.name
            if (type in finalTypes && row.playbackInstanceId in finalized) return
            val first = decisions[row.playbackInstanceId] ?: event.also {
                // Neutral markers after finalization must not retain a phantom active decision.
                if (type in decisionTypes) decisions[row.playbackInstanceId] = it
            }
            val session = sessions.getOrPut(row.sessionId) { Session() }
            val albumKey = albumKey(track.artist, track.album)
            if (row.playbackInstanceId in eligibleInstances && row.playbackInstanceId !in snapshots && row.playbackInstanceId !in finalized && first.type.name in decisionTypes) {
                val context = RecommendationContext(
                    event.localHour, event.dayOfWeek.value,
                    event.dayOfWeek.value in setOf(6, 7), event.timeBucket,
                    timestamp = event.occurredAt, sessionId = row.sessionId,
                    sessionPosition = session.position,
                    recentTrackIds = session.recent.toList(),
                    recentArtistIds = session.recent.map { tracks[it]?.artist ?: "" },
                    recentSkipStreak = session.skipStreak,
                    currentTrackId = session.recent.firstOrNull(),
                )
                // Old audio is usable only if it existed at the decision time; no future feature leakage.
                val acoustic = audio[event.trackId]?.takeIf { !it.generatedAt.isAfter(event.occurredAt) }
                snapshots[row.playbackInstanceId] = Snapshot(
                    PersonalizationFeatureVectorizer.vectorize(context, TrackPersonalizationFeatures(
                        track.trackId, track.artist, track.trackId in favorites, track.durationMs,
                        acoustic?.values, global[track.trackId] ?: PreferenceSnapshot(),
                        bucket[track.trackId to event.timeBucket] ?: PreferenceSnapshot(), lastPlayed[track.trackId],
                        track.artist?.let(artists::get), albumKey?.let(albums::get), favoriteKnown = track.trackId in knownFavorites,
                    )), context, acoustic,
                )
            }
            if (row.playbackInstanceId in eligibleInstances && row.playbackInstanceId !in finalized) actions.getOrPut(row.playbackInstanceId) { ArrayList() }.add(row)
            if (type in finalTypes) {
                if (row.playbackInstanceId in eligibleInstances) finalized.add(row.playbackInstanceId)
                val rows = actions.remove(row.playbackInstanceId).orEmpty()
                val snapshot = snapshots.remove(row.playbackInstanceId)
                val reward = PersonalizationRewardBuilder.reward(PlaybackOutcome(row.playbackInstanceId,
                    rows.mapTo(linkedSetOf()) { it.event.type.name }, event.completionRatio,
                    rows.lastOrNull { it.event.type.name in setOf("FAVORITED", "UNFAVORITED") }?.let { it.event.type.name == "FAVORITED" }))
                if (snapshot != null && reward != 0.0) samples += TrainingSample(
                    track.trackId, snapshot.values, reward, dataVersion = row.sequence,
                    playbackInstanceId = row.playbackInstanceId, eventIds = rows.map { it.event.id },
                    context = snapshot.context, sourceIdUsedForAudio = snapshot.audio?.sourceId,
                    audioExtractorVersion = snapshot.audio?.extractorVersion,
                )
                if (type == "SKIPPED_EARLY") session.skipStreak++ else if (type == "PLAY_COMPLETED") session.skipStreak = 0
            }
            global[track.trackId] = increment(global[track.trackId] ?: PreferenceSnapshot(), type)
            val key = track.trackId to first.timeBucket
            bucket[key] = increment(bucket[key] ?: PreferenceSnapshot(), type)
            track.artist?.let { artists[it] = increment(artists[it] ?: PreferenceSnapshot(), type) }
            albumKey?.let { albums[it] = increment(albums[it] ?: PreferenceSnapshot(), type) }
            if (type == "PLAY_STARTED") {
                lastPlayed[track.trackId] = event.occurredAt
                session.position++
                session.recent.add(0, track.trackId)
                if (session.recent.size > 20) session.recent.removeAt(session.recent.lastIndex)
            }
            if (type in setOf("FAVORITED", "UNFAVORITED")) knownFavorites += track.trackId
            if (type == "FAVORITED") favorites += track.trackId
            if (type == "UNFAVORITED") favorites -= track.trackId
            if (type in finalTypes) decisions.remove(row.playbackInstanceId)
        }
        fun samples(): List<TrainingSample> = samples.toList()
    }

    fun increment(stats: PreferenceSnapshot, type: String) = stats.copy(
        starts = stats.starts + if (type == "PLAY_STARTED") 1 else 0,
        completions = stats.completions + if (type == "PLAY_COMPLETED") 1 else 0,
        earlySkips = stats.earlySkips + if (type == "SKIPPED_EARLY") 1 else 0,
        lateSkips = stats.lateSkips + if (type == "SKIPPED_LATE") 1 else 0,
        manualSelections = stats.manualSelections + if (type == "MANUAL_SELECTED") 1 else 0,
        replays = stats.replays + if (type == "REPLAYED") 1 else 0,
    )
    fun albumKey(artist: String?, album: String?): String? = album?.trim()?.takeIf(String::isNotBlank)?.let { "$artist|${it.lowercase(java.util.Locale.ROOT)}" }
    private data class Snapshot(val values: DoubleArray, val context: RecommendationContext, val audio: TrainingAudio?)
    private class Session(val recent: MutableList<UUID> = ArrayList(), var position: Int = 0, var skipStreak: Int = 0)
}
