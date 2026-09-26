package dev.behradhz.meowzix.feature.library

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun LibraryRoute(
    onSwipePastEnd: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onOpenTelegram: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    LibraryRouteV3(
        onOpenNowPlaying = onOpenNowPlaying,
        viewModel = viewModel,
    )
}
