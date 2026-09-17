package com.behradhz.meowzix.feature.library

import com.behradhz.meowzix.core.model.Track

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasCompletedRefresh: Boolean = false,
    val error: LibraryUiError? = null,
)

enum class LibraryUiError {
    REFRESH_FAILED,
}
