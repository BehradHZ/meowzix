package dev.behradhz.meowzix.feature.library

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun LibraryRoute(
    onSwipePastEnd: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onOpenTelegram: () -> Unit = {},
    searchQuery: String = "",
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    if (searchQuery.isBlank()) {
        LibraryRouteV4(
            onOpenNowPlaying = onOpenNowPlaying,
            viewModel = viewModel,
        )
    } else {
        LibraryInlineSearch(
            query = searchQuery,
            onOpenNowPlaying = onOpenNowPlaying,
            viewModel = viewModel,
        )
    }
}
