package dev.behradhz.meowzix.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
) {
    LaunchedEffect(searchQuery) {
        tracksViewModel.setSearchQuery(searchQuery)
    }

    CompositionLocalProvider(
        LocalLibraryRecommendationActions provides LibraryRecommendationActions(
            continueVibe = recommendationActions::continueVibe,
            why = recommendationActions::why,
        ),
    ) {
        dev.behradhz.meowzix.feature.recommendation.RecommendationActionDialogs(recommendationActions)
        LibraryRouteV4(
            onOpenNowPlaying = onOpenNowPlaying,
            viewModel = viewModel,
            tracksViewModel = tracksViewModel,
        )
    }
}
