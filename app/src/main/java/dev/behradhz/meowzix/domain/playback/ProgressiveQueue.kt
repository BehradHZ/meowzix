package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the complete lightweight logical queue while Media3 only materializes a small sliding
 * window. Canonical Track.id identity is the duplicate boundary; file hashes are intentionally not
 * involved here.
 */
@Singleton
class ProgressiveQueue @Inject constructor() {
    private val tracks = mutableListOf<PlayableTrack>()
    private val trackIds = hashSetOf<UUID>()
    private var currentIndex = -1
    private var materializedStartIndex = 0
    private var materializedEndExclusive = 0
    private var playbackMode = PlaybackMode.ORDERED
    private var repeatMode = RepeatMode.OFF
    private var shuffleSeed: Long? = null

    // Playback position updates ask for a queue snapshot frequently. The logical queue usually does
    // not change between those ticks, so keep one immutable snapshot and invalidate it only when a
    // queue field actually changes. This prevents repeatedly copying a large queue during playback.
    private var cachedSnapshot: ProgressiveQueueSnapshot? = null

    @Synchronized
    fun start(
        orderedTracks: List<PlayableTrack>,
        requestedStartIndex: Int,
        mode: PlaybackMode,
        repeat: RepeatMode,
        seed: Long? = null,
    ): QueueWindowPlan {
        val unique = orderedTracks.distinctBy(PlayableTrack::id)
        require(unique.isNotEmpty()) { "Progressive queue requires at least one track" }
        tracks.clear()
        tracks.addAll(unique)
        trackIds.clear()
        trackIds.addAll(unique.map(PlayableTrack::id))
        currentIndex = requestedStartIndex.coerceIn(tracks.indices)
        playbackMode = mode
        repeatMode = repeat
        shuffleSeed = seed
        invalidateSnapshot()
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
    ): Boolean {
        val restored = orderedTrackIds.distinct().mapNotNull(availableTracks::get)
        if (restored.isEmpty()) {
            clearLocked()
            return false
        }
        tracks.clear()
        tracks.addAll(restored)
        trackIds.clear()
        trackIds.addAll(restored.map(PlayableTrack::id))
        currentIndex = requestedCurrentIndex.coerceIn(tracks.indices)
        materializedStartIndex = requestedMaterializedStartIndex.coerceIn(0, currentIndex)
        materializedEndExclusive = requestedMaterializedEndExclusive
            .coerceIn(currentIndex + 1, tracks.size)
        playbackMode = mode
        repeatMode = repeat
        shuffleSeed = seed
        invalidateSnapshot()
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
        val index = tracks.indexOfFirst { it.id == id }
        if (index < 0) return false
        if (index != currentIndex) {
            currentIndex = index
            invalidateSnapshot()
        }
        return true
    }

    /** Returns and reserves the next forward batch when the refill threshold is reached. */
    @Synchronized
    fun takeForwardBatchIfNeeded(): List<PlayableTrack> {
        if (tracks.isEmpty() || currentIndex < 0) return emptyList()
        val remainingAhead = materializedEndExclusive - currentIndex - 1
        if (remainingAhead > REFILL_THRESHOLD || materializedEndExclusive >= tracks.size) {
            return emptyList()
        }
        val oldEnd = materializedEndExclusive
        val newEnd = (oldEnd + REFILL_BATCH_SIZE).coerceAtMost(tracks.size)
        materializedEndExclusive = newEnd
        invalidateSnapshot()
        return tracks.subList(oldEnd, newEnd).toList()
    }

    /** Drops materialized history older than the bounded Previous window. */
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

    /** Returns and reserves an earlier batch so repeated Previous presses can cross window edges. */
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
            invalidateSnapshot()
        }
        return resetWindowLocked()
    }

    @Synchronized
    fun insertNext(track: PlayableTrack): Boolean {
        if (tracks.isEmpty()) {
            tracks += track
            trackIds += track.id
            currentIndex = 0
            invalidateSnapshot()
            return true
        }

        val currentId = tracks.getOrNull(currentIndex)?.id ?: return false
        val existingIndex = tracks.indexOfFirst { it.id == track.id }
        if (existingIndex == currentIndex) return false

        val item = if (existingIndex >= 0) {
            tracks.removeAt(existingIndex)
        } else {
            trackIds += track.id
            track
        }
        currentIndex = tracks.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return false
        tracks.add((currentIndex + 1).coerceAtMost(tracks.size), item)
        currentIndex = tracks.indexOfFirst { it.id == currentId }
        invalidateSnapshot()
        return true
    }

    @Synchronized
    fun append(track: PlayableTrack): Boolean {
        if (tracks.isEmpty()) {
            tracks += track
            trackIds += track.id
            currentIndex = 0
            invalidateSnapshot()
            return true
        }

        val currentId = tracks.getOrNull(currentIndex)?.id ?: return false
        val existingIndex = tracks.indexOfFirst { it.id == track.id }
        if (existingIndex == currentIndex) return false

        val item = if (existingIndex >= 0) {
            tracks.removeAt(existingIndex)
        } else {
            trackIds += track.id
            track
        }
        tracks += item
        currentIndex = tracks.indexOfFirst { it.id == currentId }
        invalidateSnapshot()
        return true
    }

    @Synchronized
    fun removeAt(index: Int): Boolean {
        if (index !in tracks.indices) return false
        val currentId = tracks.getOrNull(currentIndex)?.id
        val removed = tracks.removeAt(index)
        trackIds.remove(removed.id)
        if (tracks.isEmpty()) {
            clearLocked()
            return true
        }
        currentIndex = currentId
            ?.let { id -> tracks.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: index.coerceAtMost(tracks.lastIndex)
        invalidateSnapshot()
        return true
    }

    @Synchronized
    fun move(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return false
        val currentId = tracks.getOrNull(currentIndex)?.id
        val item = tracks.removeAt(fromIndex)
        tracks.add(toIndex, item)
        currentIndex = currentId?.let { id -> tracks.indexOfFirst { it.id == id } } ?: currentIndex
        invalidateSnapshot()
        return true
    }

    @Synchronized
    fun retainCurrentOnly(): Boolean {
        val current = tracks.getOrNull(currentIndex) ?: return false
        tracks.clear()
        tracks += current
        trackIds.clear()
        trackIds += current.id
        currentIndex = 0
        materializedStartIndex = 0
        materializedEndExclusive = 1
        playbackMode = PlaybackMode.ORDERED
        repeatMode = RepeatMode.OFF
        shuffleSeed = null
        invalidateSnapshot()
        return true
    }

    @Synchronized
    fun replaceFuture(
        future: List<PlayableTrack>,
        mode: PlaybackMode,
        seed: Long? = null,
    ) {
        if (tracks.isEmpty() || currentIndex < 0) return
        val fixed = tracks.take(currentIndex + 1)
        val fixedIds = fixed.mapTo(hashSetOf(), PlayableTrack::id)
        val uniqueFuture = future.asSequence()
            .filterNot { it.id in fixedIds }
            .distinctBy(PlayableTrack::id)
            .toList()
        tracks.clear()
        tracks.addAll(fixed)
        tracks.addAll(uniqueFuture)
        trackIds.clear()
        trackIds.addAll(tracks.map(PlayableTrack::id))
        playbackMode = mode
        shuffleSeed = seed
        // Keep the already materialized history/current item stable. Only the future portion of
        // the Media3 window needs to be replaced when shuffle mode changes, so preserve the window
        // start and reserve a fresh bounded forward window without forcing setMediaItems/prepare.
        materializedStartIndex = materializedStartIndex.coerceIn(0, currentIndex)
        materializedEndExclusive = (currentIndex + 1 + INITIAL_FORWARD_COUNT).coerceAtMost(tracks.size)
        invalidateSnapshot()
    }

    @Synchronized
    fun updateRepeatMode(mode: RepeatMode) {
        if (repeatMode != mode) {
            repeatMode = mode
            invalidateSnapshot()
        }
    }

    @Synchronized
    fun updatePlaybackMode(mode: PlaybackMode, seed: Long? = shuffleSeed) {
        if (playbackMode != mode || shuffleSeed != seed) {
            playbackMode = mode
            shuffleSeed = seed
            invalidateSnapshot()
        }
    }

    @Synchronized
    fun contains(trackId: UUID): Boolean = containsLocked(trackId)

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
            currentIndex = currentIndex,
            materializedStartIndex = materializedStartIndex,
            materializedEndExclusive = materializedEndExclusive,
            playbackMode = playbackMode,
            repeatMode = repeatMode,
            shuffleSeed = shuffleSeed,
        )
    }

    private fun containsLocked(trackId: UUID): Boolean = trackId in trackIds

    private fun clearLocked() {
        tracks.clear()
        trackIds.clear()
        currentIndex = -1
        materializedStartIndex = 0
        materializedEndExclusive = 0
        playbackMode = PlaybackMode.ORDERED
        repeatMode = RepeatMode.OFF
        shuffleSeed = null
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
    }
}

data class QueueWindowPlan(
    val tracks: List<PlayableTrack>,
    val startIndexInWindow: Int,
)

data class ProgressiveQueueSnapshot(
    val tracks: List<PlayableTrack>,
    val currentIndex: Int,
    val materializedStartIndex: Int,
    val materializedEndExclusive: Int,
    val playbackMode: PlaybackMode,
    val repeatMode: RepeatMode,
    val shuffleSeed: Long?,
)
