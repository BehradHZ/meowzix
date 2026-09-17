package com.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behradhz.meowzix.domain.library.ObserveTracksUseCase
import com.behradhz.meowzix.domain.library.RefreshLocalLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val refreshLocalLibrary: RefreshLocalLibraryUseCase,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val hasCompletedRefresh = MutableStateFlow(false)
    private val error = MutableStateFlow<LibraryUiError?>(null)

    val uiState = combine(
        observeTracks(),
        isRefreshing,
        hasCompletedRefresh,
        error,
    ) { tracks, refreshing, completed, currentError ->
        LibraryUiState(
            tracks = tracks,
            isRefreshing = refreshing,
            hasCompletedRefresh = completed,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun refresh() {
        if (isRefreshing.value) return

        viewModelScope.launch {
            isRefreshing.value = true
            error.value = null

            runCatching { refreshLocalLibrary() }
                .onSuccess { hasCompletedRefresh.value = true }
                .onFailure { error.value = LibraryUiError.REFRESH_FAILED }

            isRefreshing.value = false
        }
    }

    fun consumeError() {
        error.value = null
    }
}
