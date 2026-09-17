package dev.behradhz.meowzix.playback.persistence

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode

data class PersistedPlaybackItem(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val durationMs: Long,
)

data class PersistedPlaybackSession(
    val items: List<PersistedPlaybackItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val playbackMode: PlaybackMode,
    val repeatMode: RepeatMode,
) {
    companion object {
        val Empty = PersistedPlaybackSession(
            items = emptyList(),
            currentIndex = 0,
            positionMs = 0,
            playbackMode = PlaybackMode.ORDERED,
            repeatMode = RepeatMode.OFF,
        )
    }
}
