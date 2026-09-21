package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackStatus { IDLE, PREPARING, DOWNLOADING, BUFFERING, PLAYING, PAUSED, ERROR }

enum class PlaybackMode { ORDERED, PURE_SHUFFLE, SMART_SHUFFLE }

enum class RepeatMode { OFF, ONE, ALL }

data class NowPlayingTrack(
    val id: UUID,
    val title: String,
    val artist: String?,
    val artworkRef: String?,
)

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentTrack: NowPlayingTrack? = null,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playbackMode: PlaybackMode = PlaybackMode.ORDERED,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val errorMessage: String? = null,
)

interface PlaybackController {
    val state: StateFlow<PlaybackState>

    fun playTrack(trackId: UUID)
    fun resume()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipToPrevious()
    fun skipToNext()
}
