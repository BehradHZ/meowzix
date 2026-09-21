package dev.behradhz.meowzix.feature.nowplaying

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.playback.RepeatMode
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val queueRepository: QueueRepository,
    private val audioVisualizerRepository: AudioVisualizerRepository,
) : ViewModel() {
    val state = playbackController.state
    val queueState = queueRepository.queueState
    val spectrum = audioVisualizerRepository.spectrum

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun previous() = playbackController.skipToPrevious()
    fun next() = playbackController.skipToNext()

    fun setSpectrumCaptureEnabled(enabled: Boolean) =
        audioVisualizerRepository.setCaptureEnabled(enabled)

    fun togglePlaybackMode() = queueRepository.setPlaybackMode(
        when (state.value.playbackMode) {
            PlaybackMode.ORDERED -> PlaybackMode.PURE_SHUFFLE
            PlaybackMode.PURE_SHUFFLE -> PlaybackMode.SMART_SHUFFLE
            PlaybackMode.SMART_SHUFFLE -> PlaybackMode.ORDERED
        },
    )

    fun cycleRepeatMode() = queueRepository.setRepeatMode(
        when (state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        },
    )
}
