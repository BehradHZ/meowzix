package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class QueueItemOrigin {
    GENERATED,
    MANUAL,
}

enum class SmartFutureRevisionResult {
    APPLIED,
    NO_CHANGE,
    STALE,
    NOT_SMART,
}

/**
 * Owns the complete lightweight logical queue while Media3 only materializes a small sliding
 * window. Track ids identify canonical tracks, while the logical queue may intentionally contain
 * more than one occurrence of the same track (for example Play Next on the current/next item).
 *
 * Smart Shuffle provenance lives beside the logical queue, not in Compose/Media3. This lets a live
 * rerank mutate only automatically generated future entries while explicit user edits remain fixed.
 * [revision] changes only for logical queue/context mutations; Media3 window refill bookkeeping does
 * not invalidate an otherwise valid ranking result.
 */
@Singleton
class ProgressiveQueue @Inject constructor() {
    private val tracks = mutableListOf<PlayableTrack>()
    private val origins = mutableListOf<QueueItemOrigin>()
    private val trackIds = hashSetOf<UUID>()
    private var currentIndex = -1
    private var materializedStartIndex = 0
    private var materializedEndExclusive = 0
    private var playbackMode = PlaybackMode.ORDERED
    private var repeatMode = RepeatMode.OFF
    private var shuffleSeed: Long? = null
    private var revision = 0L

    private var cachedSnapshot: ProgressiveQueueSnapshot? = null

    @Synchronized
    fun start(
        orderedTracks: List<PlayableTrack>,
        requestedStartIndex: Int,
        mode: PlaybackMode,
        repeat: RepeatMode,
        seed: Long? = null,
    ): QueueWindowPlan {
        require(orderedTracks.isNotEmpty()) { "Progressive queue requires at least one track" }
        tracks.clear()
        tracks.addAll(orderedTracks)
        origins.clear()
        val generated = mode == PlaybackMode.SMART_SHUFFLE
        origins.addAll(List(orderedTracks.size) { if (generated) QueueItemOrigin.GENERATED else QueueItemOrigin.MANUAL })
        trackIds.clear()
        trackIds.addAll(orderedTracks.map(PlayableTrack::id))
        currentIndex = requestedStartIndex.coerceIn(tracks.indices)
        origins[currentIndex] = QueueItemOrigin.MANUAL
        playbackMode = mode
        repeatMode = repeat
        shuffleSeed = seed
        bumpRevision()
        return resetWindowLocked()
    }

    @Synchronized
    fun restore(
        orderedTrackIds: List<UUID>,
        availableTracks: Map<UUID, PlayableTrack>,
        requestedCurrentIndex: Int,
        requestedMaterializedStartIndex: Int,
        requestedMaterializedEndExclusive: Int,
        mode: PlaybackMode,
        repeat: RepeatMode,
        seed: Long?,
        requestedOrigins: List<QueueItemOrigin>? = null,
    ): Boolean {
        val restoredOrigins = requestedOrigins ?: QueueProvenanceRestoreHints.consume(orderedTrackIds)
        val fallbackOrigin = if (mode == PlaybackMode.SMART_SHUFFLE) QueueItemOrigin.GENERATED else QueueItemOrigin.MANUAL
        val restored = orderedTrackIds.mapIndexedNotNull { index, id ->
            availableTracks[id]?.let { it to (restoredOrigins?.getOrNull(index) ?: fallbackOrigin) }
        }
        if (restored.isEmpty()) {
            clearLocked()
            return false
        }
        tracks.clear()
        tracks.addAll(restored.map { it.first })
        origins.clear()
        origins.addAll(restored.map { it.second })
        trackIds.clear()
        trackIds.addAll(tracks.map(PlayableTrack::id))
        currentIndex = requestedCurrentIndex.coerceIn(tracks.indices)
        origins[currentIndex] = QueueItemOrigin.MANUAL
        materializedStartIndex = requestedMaterializedStartIndex.coerceIn(0, currentIndex)
        materializedEndExclusive = requestedMaterializedEndExclusive
            .coerceIn(currentIndex + 1, tracks.size)
        playbackMode = mode
        repeatMode = repeat
        shuffleSeed = seed
        bumpRevision()
        return true
    }

    @Synchronized
    fun clear() = clearLocked()

    @Synchronized
    fun snapshot(): ProgressiveQueueSnapshot? = cachedSnapshot ?: snapshotLocked()?.also {
        cachedSnapshot = it
    }

    @Synchronized
    fun updateCurrent(mediaId: String?): Boolean {
        val id = mediaId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return false
        if (tracks.getOrNull(currentIndex)?.id == id) return true
        val forwardIndex = (currentIndex + 1 until tracks.size).firstOrNull { tracks[it].id == id }
        val backwardIndex = (currentIndex - 1 downTo 0).firstOrNull { tracks[it].id == id }
        val index = forwardIndex ?: backwardIndex ?: return false
        if (index != currentIndex) {
            currentIndex = index
            origins[currentIndex] = QueueItemOrigin.MANUAL
            bumpRevision()
        }
        return true
    }

    @Synchronized
    fun updateCurrentFromWindowIndex(windowIndex: Int): Boolean {
        if (windowIndex < 0) return false
        val logicalIndex = materializedStartIndex + windowIndex
        if (logicalIndex !in tracks.indices || logicalIndex >= materializedEndExclusive) return false
        if (logicalIndex != currentIndex) {
            currentIndex = logicalIndex
            origins[currentIndex] = QueueItemOrigin.MANUAL
            bumpRevision()
        }
        return true
    }

    @Synchronized
    fun takeForwardBatchIfNeeded(): List<PlayableTrack> {
        if (tracks.isEmpty() || currentIndex < 0) return emptyList()
        val remainingAhead = materializedEndExclusive - currentIndex - 1
        if (remainingAhead > REFILL_THRESHOLD || materializedEndExclusive >= tracks.size) return emptyList()
        val oldEnd = materializedEndExclusive
        val newEnd = (oldEnd + REFILL_BATCH_SIZE).coerceAtMost(tracks.size)
        materializedEndExclusive = newEnd
        invalidateSnapshot()
        return tracks.subList(oldEnd, newEnd).toList()
    }

    @Synchronized
    fun trimMaterializedHistory(): Int {
        if (tracks.isEmpty() || currentIndex < 0) return 0
        val desiredStart = (currentIndex - PREVIOUS_WINDOW_SIZE).coerceAtLeast(0)
        if (desiredStart <= materializedStartIndex) return 0
        val removed = desiredStart - materializedStartIndex
        materializedStartIndex = desiredStart
        invalidateSnapshot()
        return removed
    }

    @Synchronized
    fun takeBackwardBatchIfNeeded(): List<PlayableTrack> {
        if (tracks.isEmpty() || currentIndex < 0 || materializedStartIndex == 0) return emptyList()
        val remainingBehind = currentIndex - materializedStartIndex
        if (remainingBehind > BACKWARD_REFILL_THRESHOLD) return emptyList()
        val oldStart = materializedStartIndex
        val newStart = (oldStart - REFILL_BATCH_SIZE).coerceAtLeast(0)
        materializedStartIndex = newStart
        invalidateSnapshot()
        return tracks.subList(newStart, oldStart).toList()
    }

    @Synchronized
    fun resetWindow(): QueueWindowPlan {
        check(tracks.isNotEmpty() && currentIndex >= 0) { "No active progressive queue" }
        return resetWindowLocked()
    }

    @Synchronized
    fun jumpTo(index: Int): QueueWindowPlan? {
        if (index !in tracks.indices) return null
        if (currentIndex != index) {
            currentIndex = index
            origins[currentIndex] = QueueItemOrigin.MANUAL
            bumpRevision()
        }
        return resetWindowLocked()
    }

    @Synchronized
    fun insertNext(track: PlayableTrack): Boolean {
        if (tracks.isEmpty()) {
            tracks += track
            origins += QueueItemOrigin.MANUAL
            trackIds += track.id
            currentIndex = 0
            bumpRevision()
            return true
        }
        if (currentIndex !in tracks.indices) return false

        val insertionIndex = currentIndex + 1
        val duplicateRequested =
            tracks[currentIndex].id == track.id || tracks.getOrNull(insertionIndex)?.id == track.id
        if (duplicateRequested) {
            tracks.add(insertionIndex.coerceAtMost(tracks.size), track)
            origins.add(insertionIndex.coerceAtMost(origins.size), QueueItemOrigin.MANUAL)
            trackIds += track.id
            bumpRevision()
            return true
        }

        val existingIndex = tracks.indexOfFirst { it.id == track.id }
        val item = if (existingIndex >= 0) {
            origins.removeAt(existingIndex)
            tracks.removeAt(existingIndex).also {
                if (existingIndex < currentIndex) currentIndex -= 1
            }
        } else {
            trackIds += track.id
            track
        }
        val destination = (currentIndex + 1).coerceAtMost(tracks.size)
        tracks.add(destination, item)
        origins.add(destination, QueueItemOrigin.MANUAL)
        bumpRevision()
        return true
    }

    @Synchronized
    fun append(track: PlayableTrack): Boolean {
        if (tracks.isEmpty()) {
            tracks += track
            origins += QueueItemOrigin.MANUAL
            trackIds += track.id
            currentIndex = 0
            bumpRevision()
            return true
        }
        if (currentIndex !in tracks.indices || tracks[currentIndex].id == track.id) return false

        val existingIndex = tracks.indexOfFirst { it.id == track.id }
        val item = if (existingIndex >= 0) {
            origins.removeAt(existingIndex)
            tracks.removeAt(existingIndex).also {
                if (existingIndex < currentIndex) currentIndex -= 1
            }
        } else {
            trackIds += track.id
            track
        }
        tracks += item
        origins += QueueItemOrigin.MANUAL
        bumpRevision()
        return true
    }

    @Synchronized
    fun removeAt(index: Int): Boolean {
        if (index !in tracks.indices) return false
        val removingCurrent = index == currentIndex
        val removed = tracks.removeAt(index)
        origins.removeAt(index)
        if (tracks.none { it.id == removed.id }) trackIds.remove(removed.id)
        if (tracks.isEmpty()) {
            clearLocked()
            return true
        }
        currentIndex = when {
            removingCurrent -> index.coerceAtMost(tracks.lastIndex)
            index < currentIndex -> currentIndex - 1
            else -> currentIndex
        }
        origins[currentIndex] = QueueItemOrigin.MANUAL
        bumpRevision()
        return true
    }

    @Synchronized
    fun move(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return false
        val movingCurrent = fromIndex == currentIndex
        val item = tracks.removeAt(fromIndex)
        origins.removeAt(fromIndex)
        tracks.add(toIndex, item)
        origins.add(toIndex, QueueItemOrigin.MANUAL)
        currentIndex = if (movingCurrent) {
            toIndex
        } else {
            var adjusted = currentIndex
            if (fromIndex < adjusted) adjusted -= 1
            if (toIndex <= adjusted) adjusted += 1
            adjusted
        }
        origins[currentIndex] = QueueItemOrigin.MANUAL
        bumpRevision()
        return true
    }

    @Synchronized
    fun retainCurrentOnly(): Boolean {
        val current = tracks.getOrNull(currentIndex) ?: return false
        tracks.clear()
        tracks += current
        origins.clear()
        origins += QueueItemOrigin.MANUAL
        trackIds.clear()
        trackIds += current.id
        currentIndex = 0
        materializedStartIndex = 0
        materializedEndExclusive = 1
        playbackMode = PlaybackMode.ORDERED
        repeatMode = RepeatMode.OFF
        shuffleSeed = null
        bumpRevision()
        return true
    }

    @Synchronized
    fun replaceFuture(
        future: List<PlayableTrack>,
        mode: PlaybackMode,
        seed: Long? = null,
    ) {
        if (tracks.isEmpty() || currentIndex < 0) return
        val fixedTracks = tracks.take(currentIndex + 1)
        val fixedOrigins = origins.take(currentIndex + 1)
        tracks.clear()
        tracks.addAll(fixedTracks)
        tracks.addAll(future)
        origins.clear()
        origins.addAll(fixedOrigins)
        val futureOrigin = if (mode == PlaybackMode.SMART_SHUFFLE) QueueItemOrigin.GENERATED else QueueItemOrigin.MANUAL
        origins.addAll(List(future.size) { futureOrigin })
        trackIds.clear()
        trackIds.addAll(tracks.map(PlayableTrack::id))
        playbackMode = mode
        shuffleSeed = seed
        materializedStartIndex = materializedStartIndex.coerceIn(0, currentIndex)
        materializedEndExclusive = (currentIndex + 1 + INITIAL_FORWARD_COUNT).coerceAtMost(tracks.size)
        bumpRevision()
    }

    @Synchronized
    fun rerankGeneratedFuture(
        rankedTrackIds: List<UUID>,
        expectedRevision: Long,
        windowLimit: Int = SMART_RERANK_WINDOW,
    ): SmartFutureRevisionResult {
        if (playbackMode != PlaybackMode.SMART_SHUFFLE) return SmartFutureRevisionResult.NOT_SMART
        if (revision != expectedRevision) return SmartFutureRevisionResult.STALE
        if (currentIndex !in tracks.indices || windowLimit <= 0) return SmartFutureRevisionResult.NO_CHANGE

        val generatedPositions = (currentIndex + 1 until tracks.size)
            .filter { origins[it] == QueueItemOrigin.GENERATED }
            .take(windowLimit)
        if (generatedPositions.size <= 1) return SmartFutureRevisionResult.NO_CHANGE

        val currentGenerated = generatedPositions.map { tracks[it] }
        val byId = currentGenerated.associateBy(PlayableTrack::id)
        val ranked = buildList(currentGenerated.size) {
            rankedTrackIds.distinct().forEach { id -> byId[id]?.let { add(it) } }
            val selected = mapTo(hashSetOf(), PlayableTrack::id)
            currentGenerated.forEach { if (it.id !in selected) add(it) }
        }
        if (ranked.map(PlayableTrack::id) == currentGenerated.map(PlayableTrack::id)) {
            return SmartFutureRevisionResult.NO_CHANGE
        }

        generatedPositions.forEachIndexed { index, position -> tracks[position] = ranked[index] }
        bumpRevision()
        return SmartFutureRevisionResult.APPLIED
    }

    @Synchronized
    fun updateRepeatMode(mode: RepeatMode) {
        if (repeatMode != mode) {
            repeatMode = mode
            bumpRevision()
        }
    }

    @Synchronized
    fun updatePlaybackMode(mode: PlaybackMode, seed: Long? = shuffleSeed) {
        if (playbackMode != mode || shuffleSeed != seed) {
            playbackMode = mode
            shuffleSeed = seed
            if (mode != PlaybackMode.SMART_SHUFFLE) {
                for (index in origins.indices) origins[index] = QueueItemOrigin.MANUAL
            }
            bumpRevision()
        }
    }

    @Synchronized
    fun contains(trackId: UUID): Boolean = trackId in trackIds

    @Synchronized
    fun hasPrevious(): Boolean = currentIndex > 0

    @Synchronized
    fun hasNext(): Boolean = currentIndex >= 0 && currentIndex < tracks.lastIndex

    private fun resetWindowLocked(): QueueWindowPlan {
        val newStart = (currentIndex - PREVIOUS_WINDOW_SIZE).coerceAtLeast(0)
        val newEnd = (currentIndex + 1 + INITIAL_FORWARD_COUNT).coerceAtMost(tracks.size)
        if (materializedStartIndex != newStart || materializedEndExclusive != newEnd) {
            materializedStartIndex = newStart
            materializedEndExclusive = newEnd
            invalidateSnapshot()
        }
        return QueueWindowPlan(
            tracks = tracks.subList(materializedStartIndex, materializedEndExclusive).toList(),
            startIndexInWindow = currentIndex - materializedStartIndex,
        )
    }

    private fun snapshotLocked(): ProgressiveQueueSnapshot? {
        if (tracks.isEmpty() || currentIndex !in tracks.indices) return null
        return ProgressiveQueueSnapshot(
            tracks = tracks.toList(),
            origins = origins.toList(),
            currentIndex = currentIndex,
            materializedStartIndex = materializedStartIndex,
            materializedEndExclusive = materializedEndExclusive,
            playbackMode = playbackMode,
            repeatMode = repeatMode,
            shuffleSeed = shuffleSeed,
            revision = revision,
        )
    }

    private fun clearLocked() {
        val hadState = tracks.isNotEmpty() || currentIndex >= 0
        tracks.clear()
        origins.clear()
        trackIds.clear()
        currentIndex = -1
        materializedStartIndex = 0
        materializedEndExclusive = 0
        playbackMode = PlaybackMode.ORDERED
        repeatMode = RepeatMode.OFF
        shuffleSeed = null
        QueueProvenanceRestoreHints.clear()
        if (hadState) bumpRevision() else invalidateSnapshot()
    }

    private fun bumpRevision() {
        revision = if (revision == Long.MAX_VALUE) 1L else revision + 1L
        invalidateSnapshot()
    }

    private fun invalidateSnapshot() {
        cachedSnapshot = null
    }

    companion object {
        const val INITIAL_FORWARD_COUNT = 10
        const val REFILL_THRESHOLD = 3
        const val REFILL_BATCH_SIZE = 10
        const val PREVIOUS_WINDOW_SIZE = 3
        const val BACKWARD_REFILL_THRESHOLD = 1
        const val SMART_RERANK_WINDOW = 20
    }
}

data class QueueWindowPlan(
    val tracks: List<PlayableTrack>,
    val startIndexInWindow: Int,
)

data class ProgressiveQueueSnapshot(
    val tracks: List<PlayableTrack>,
    val origins: List<QueueItemOrigin>,
    val currentIndex: Int,
    val materializedStartIndex: Int,
    val materializedEndExclusive: Int,
    val playbackMode: PlaybackMode,
    val repeatMode: RepeatMode,
    val shuffleSeed: Long?,
    val revision: Long,
)
