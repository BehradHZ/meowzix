package dev.behradhz.meowzix.feature.nowplaying

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.playback.PlaybackController
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController,
) : ViewModel() {
    val state = playbackController.state

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun previous() = playbackController.skipToPrevious()
    fun next() = playbackController.skipToNext()
}
