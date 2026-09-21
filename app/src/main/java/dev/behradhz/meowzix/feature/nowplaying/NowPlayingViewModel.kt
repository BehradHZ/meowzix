package dev.behradhz.meowzix.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.playback.RepeatMode
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val queueRepository: QueueRepository,
    private val audioVisualizerRepository: AudioVisualizerRepository,
    private val libraryRepository: MusicLibraryRepository,
) : ViewModel() {
    val state = combine(playbackController.state, libraryRepository.observeTracks()) { playback, tracks ->
        val current = playback.currentTrack ?: return@combine playback
        val liveTrack = tracks.firstOrNull { it.id == current.id } ?: return@combine playback
        playback.copy(
            currentTrack = current.copy(
                title = liveTrack.title,
                artist = liveTrack.artist,
                artworkRef = liveTrack.artworkRef ?: current.artworkRef,
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        playbackController.state.value,
    )
    val queueState = queueRepository.queueState
    val spectrum = audioVisualizerRepository.spectrum
    val isFavorite = combine(state, libraryRepository.observeTracks()) { playback, tracks ->
        val id = playback.currentTrack?.id
        id != null && tracks.firstOrNull { it.id == id }?.favorite == true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun previous() = playbackController.skipToPrevious()
    fun next() = playbackController.skipToNext()

    fun toggleFavorite() {
        val id = state.value.currentTrack?.id ?: return
        viewModelScope.launch { libraryRepository.setFavorite(id, !isFavorite.value) }
    }

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
