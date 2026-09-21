package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.LocalLibraryRefreshResult
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.QueueRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastRefresh: LocalLibraryRefreshResult? = null,
    val errorMessage: String? = null,
    val playback: PlaybackState = PlaybackState(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicLibraryRepository,
    private val playbackController: PlaybackController,
    private val queueRepository: QueueRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTracks()
                .catch { error -> _state.update { it.copy(errorMessage = error.message ?: "Unable to load Telegram music") } }
                .collect { tracks -> _state.update { it.copy(tracks = tracks, errorMessage = null) } }
        }
        viewModelScope.launch {
            playbackController.state.collect { playback ->
                _state.update { it.copy(playback = playback) }
            }
        }
    }

    /** Telegram-only mode: local MediaStore scanning is intentionally disabled. */
    fun refresh() = Unit

    fun playTrack(track: Track) {
        playbackController.playTrack(track.id)
    }

    fun playNext(track: Track) = queueRepository.playNext(track.id)
    fun addToQueue(track: Track) = queueRepository.addToQueue(track.id)
}
