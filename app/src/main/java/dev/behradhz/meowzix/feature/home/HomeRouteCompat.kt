package dev.behradhz.meowzix.feature.home

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.chrisbanes.haze.HazeState

/**
 * Navigation still owns collection playback, while direct track playback on Home is now
 * recommendation-driven inside [HomeViewModel]. Keep this overload until the navigation
 * signature is next consolidated so existing callers do not bypass Home's vibe queue.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeRoute(
    hazeState: HazeState,
    currentTrack: Track?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayCollection: (List<Track>) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    HomeRoute(
        hazeState = hazeState,
        currentTrack = currentTrack,
        onPlayCollection = onPlayCollection,
        onOpenLibrary = onOpenLibrary,
        viewModel = viewModel,
    )
}
