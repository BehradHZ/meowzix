package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps the complete logical queue lightweight while exposing only a small sliding window to
 * Media3. The logical queue is canonical Track IDs + lightweight playback metadata; expensive
 * MediaItem materialization is deliberately bounded.
 */
@Singleton
class WindowedPlaybackQueue @Inject constructor() {
    private var tracks: List<PlayableTrack> = emptyList()
    private var indexById: Map<UUID, Int> = emptyMap()
    private var currentTrackId: UUID? = null
    private var playbackMode: PlaybackMode = PlaybackMode.ORDERED
    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffleSeed: Long? = null
    private var revision: Long = 0L

    @Synchronized
    fun reset(
        tracks: List<PlayableTrack>,
        currentIndex: Int,
        playbackMode: PlaybackMode,
        repeatMode: RepeatMode,
        shuffleSeed: Long? = null,
    ) {
        val unique = dedupeByTrackId(tracks)
        this.tracks = unique
        rebuildIndex()
        this.currentTrackId = if (unique.isEmpty()) null else unique[currentIndex.coerceIn(unique.indices)].id
        this.playbackMode = playbackMode
        this.repeatMode = repeatMode
        this.shuffleSeed = shuffleSeed
        revision += 1
    }

    @Synchronized
    fun resetAtTrack(
        tracks: List<PlayableTrack>,
        currentTrackId: UUID?,
        playbackMode: PlaybackMode,
        repeatMode: RepeatMode,
        shuffleSeed: Long? = null,
    ) {
        val unique = dedupeByTrackId(tracks)
        this.tracks = unique
        rebuildIndex()
        this.currentTrackId = currentTrackId?.takeIf(indexById::containsKey) ?: unique.firstOrNull()?.id
        this.playbackMode = playbackMode
        this.repeatMode = repeatMode
        this.shuffleSeed = shuffleSeed
        revision += 1
    }

    @Synchronized
    fun clear() {
        tracks = emptyList()
        indexById = emptyMap()
        currentTrackId = null
        playbackMode = PlaybackMode.ORDERED
        repeatMode = RepeatMode.OFF
        shuffleSeed = null
        revision += 1
    }

    @Synchronized
    fun snapshot(): WindowedQueueSnapshot {
        val index = currentTrackId?.let(indexById::get) ?: -1
        return WindowedQueueSnapshot(
            tracks = tracks,
            currentIndex = index,
            playbackMode = playbackMode,
            repeatMode = repeatMode,
            shuffleSeed = shuffleSeed,
            revision = revision,
        )
    }

    @Synchronized
    fun isActive(): Boolean = tracks.isNotEmpty() && currentTrackId != null

    @Synchronized
    fun contains(trackId: UUID): Boolean = indexById.containsKey(trackId)

    @Synchronized
    fun updateCurrent(mediaId: String?): Boolean {
        val id = mediaId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return false
        if (!indexById.containsKey(id)) return false
        currentTrackId = id
        return true
    }

    @Synchronized
    fun materializedWindow(): MaterializedQueueWindow {
        if (tracks.isEmpty()) return MaterializedQueueWindow(emptyList(), 0)
        val currentIndex = currentTrackId?.let(indexById::get)?.coerceIn(tracks.indices) ?: 0
        val start = max(0, currentIndex - HISTORY_LIMIT)
        val endExclusive = min(tracks.size, currentIndex + 1 + INITIAL_AHEAD_COUNT)
        return MaterializedQueueWindow(
            tracks = tracks.subList(start, endExclusive),
            currentIndex = currentIndex - start,
        )
    }

    @Synchronized
    fun nextBatchAfter(lastLoadedMediaId: String?): List<PlayableTrack> {
        val id = lastLoadedMediaId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return emptyList()
        val index = indexById[id] ?: return emptyList()
        if (index >= tracks.lastIndex) return emptyList()
        val start = index + 1
        return tracks.subList(start, min(tracks.size, start + REFILL_BATCH_SIZE))
    }

    @Synchronized
    fun previousBatchBefore(firstLoadedMediaId: String?): List<PlayableTrack> {
        val id = firstLoadedMediaId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return emptyList()
        val index = indexById[id] ?: return emptyList()
        if (index <= 0) return emptyList()
        val start = max(0, index - REFILL_BATCH_SIZE)
        return tracks.subList(start, index)
    }

    fun shouldRefillAhead(currentPlayerIndex: Int, playerItemCount: Int): Boolean =
        playerItemCount > 0 && playerItemCount - currentPlayerIndex - 1 <= REFILL_THRESHOLD

    fun shouldBackfillHistory(currentPlayerIndex: Int): Boolean =
        currentPlayerIndex in 0..REFILL_THRESHOLD

    fun trimBeforeCount(currentPlayerIndex: Int): Int =
        (currentPlayerIndex - HISTORY_LIMIT).coerceAtLeast(0)

    @Synchronized
    fun insertNext(track: PlayableTrack): Boolean {
        if (indexById.containsKey(track.id)) return false
        val currentIndex = currentTrackId?.let(indexById::get) ?: -1
        val insertionIndex = (currentIndex + 1).coerceIn(0, tracks.size)
        tracks = tracks.toMutableList().apply { add(insertionIndex, track) }
        rebuildIndex()
        revision += 1
        return true
    }

    @Synchronized
    fun append(track: PlayableTrack): Boolean {
        if (indexById.containsKey(track.id)) return false
        tracks = tracks + track
        rebuildIndex()
        if (currentTrackId == null) currentTrackId = track.id
        revision += 1
        return true
    }

    @Synchronized
    fun removeAt(index: Int): Boolean {
        if (index !in tracks.indices) return false
        val oldCurrentId = currentTrackId
        val mutable = tracks.toMutableList()
        val removed = mutable.removeAt(index)
        tracks = mutable
        rebuildIndex()
        if (removed.id == oldCurrentId) {
            currentTrackId = tracks.getOrNull(index.coerceAtMost(tracks.lastIndex))?.id
                ?: tracks.lastOrNull()?.id
        }
        revision += 1
        return true
    }

    @Synchronized
    fun move(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return false
        val mutable = tracks.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        tracks = mutable
        rebuildIndex()
        revision += 1
        return true
    }

    @Synchronized
    fun setRepeatMode(mode: RepeatMode) {
        if (repeatMode == mode) return
        repeatMode = mode
        revision += 1
    }

    @Synchronized
    fun setPlaybackMode(mode: PlaybackMode, seed: Long? = shuffleSeed) {
        if (playbackMode == mode && shuffleSeed == seed) return
        playbackMode = mode
        shuffleSeed = seed
        revision += 1
    }

    private fun rebuildIndex() {
        indexById = HashMap<UUID, Int>(tracks.size).apply {
            tracks.forEachIndexed { index, track -> put(track.id, index) }
        }
    }

    private fun dedupeByTrackId(input: List<PlayableTrack>): List<PlayableTrack> {
        if (input.size < 2) return input.toList()
        val seen = HashSet<UUID>(input.size)
        return input.filter { seen.add(it.id) }
    }

    companion object {
        const val INITIAL_AHEAD_COUNT = 10
        const val REFILL_BATCH_SIZE = 10
        const val REFILL_THRESHOLD = 3
        const val HISTORY_LIMIT = 10
    }
}

data class MaterializedQueueWindow(
    val tracks: List<PlayableTrack>,
    val currentIndex: Int,
)

data class WindowedQueueSnapshot(
    val tracks: List<PlayableTrack>,
    val currentIndex: Int,
    val playbackMode: PlaybackMode,
    val repeatMode: RepeatMode,
    val shuffleSeed: Long?,
    val revision: Long,
)
