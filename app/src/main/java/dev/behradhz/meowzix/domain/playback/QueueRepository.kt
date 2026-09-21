package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

data class QueueItem(
    val id: UUID,
    val title: String,
    val artist: String?,
    val artworkRef: String?,
)

data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val playbackMode: PlaybackMode = PlaybackMode.ORDERED,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

interface QueueRepository {
    val queueState: StateFlow<QueueState>

    fun playNow(trackId: UUID)
    fun playNext(trackId: UUID)
    fun addToQueue(trackId: UUID)
    fun replaceAndPlay(trackIds: List<UUID>, mode: PlaybackMode)
    fun replaceAndPlay(trackIds: List<UUID>, startTrackId: UUID, mode: PlaybackMode)
    fun removeAt(index: Int)
    fun move(fromIndex: Int, toIndex: Int)
    fun clear()
    fun setPlaybackMode(mode: PlaybackMode)
    fun setRepeatMode(mode: RepeatMode)
}
