package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.LocalLibraryRefreshResult
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
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
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicLibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTracks()
                .catch { error -> _state.update { it.copy(errorMessage = error.message ?: "Unable to load music") } }
                .collect { tracks -> _state.update { it.copy(tracks = tracks, errorMessage = null) } }
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching { repository.refreshLocalMusic() }
                .onSuccess { result -> _state.update { it.copy(isRefreshing = false, lastRefresh = result) } }
                .onFailure { error -> _state.update { it.copy(isRefreshing = false, errorMessage = error.message ?: "Unable to scan device music") } }
        }
    }
}
