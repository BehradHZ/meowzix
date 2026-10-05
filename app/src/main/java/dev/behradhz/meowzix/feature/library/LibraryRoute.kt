package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.behradhz.meowzix.feature.recommendation.RecommendationActionsViewModel

@Composable
fun LibraryRoute(
    onSwipePastEnd: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onOpenTelegram: () -> Unit = {},
    searchQuery: String = "",
    viewModel: LibraryViewModel = hiltViewModel(),
    tracksViewModel: LibraryTracksViewModel = hiltViewModel(),
    recommendationActions: RecommendationActionsViewModel = hiltViewModel(),
    libraryTools: LibraryToolsViewModel = hiltViewModel(),
    rulePlaylists: RulePlaylistsViewModel = hiltViewModel(),
) {
    LaunchedEffect(searchQuery) {
        tracksViewModel.setSearchQuery(searchQuery)
    }

    CompositionLocalProvider(
        LocalLibraryRecommendationActions provides LibraryRecommendationActions(
            continueVibe = recommendationActions::continueVibe,
            why = recommendationActions::why,
        ),
        LocalLibraryTrackToolsActions provides LibraryTrackToolsActions(
            editMetadata = libraryTools::openMetadata,
            manageDuplicates = libraryTools::openDuplicates,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            LibraryRouteV4(
                onOpenNowPlaying = onOpenNowPlaying,
                viewModel = viewModel,
                tracksViewModel = tracksViewModel,
            )
            SmartPlaylistsOverlay(
                ruleViewModel = rulePlaylists,
                libraryViewModel = viewModel,
                onOpenNowPlaying = onOpenNowPlaying,
            )
        }
        dev.behradhz.meowzix.feature.recommendation.RecommendationActionDialogs(recommendationActions)
        LibraryToolsDialogs(libraryTools)
    }
}