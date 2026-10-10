package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.behradhz.meowzix.feature.recommendation.RecommendationActionsViewModel
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel

@Composable
fun LibraryRoute(
    onSwipePastEnd: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onOpenTelegram: () -> Unit = {},
    searchQuery: String = "",
    requestedArtist: String? = null,
    onArtistRequestConsumed: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    tracksViewModel: LibraryTracksViewModel = hiltViewModel(),
    recommendationActions: RecommendationActionsViewModel = hiltViewModel(),
    libraryTools: LibraryToolsViewModel = hiltViewModel(),
    rulePlaylists: RulePlaylistsViewModel = hiltViewModel(),
    forwardViewModel: NowPlayingViewModel = hiltViewModel(),
) {
    var forwardSelection by remember { mutableStateOf<TrackForwardSelection?>(null) }
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
        LocalTrackForwardAction provides { selection ->
            forwardSelection = selection
            forwardViewModel.openForwardPickerForTrack(selection.id)
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            LibraryRouteV4(
                onOpenNowPlaying = onOpenNowPlaying,
                viewModel = viewModel,
                tracksViewModel = tracksViewModel,
                requestedArtist = requestedArtist,
                onArtistRequestConsumed = onArtistRequestConsumed,
            )
            SmartPlaylistsOverlay(
                ruleViewModel = rulePlaylists,
                libraryViewModel = viewModel,
                onOpenNowPlaying = onOpenNowPlaying,
            )
        }
        dev.behradhz.meowzix.feature.recommendation.RecommendationActionDialogs(recommendationActions)
        LibraryToolsDialogs(libraryTools)
        TrackForwardOverlay(
            selection = forwardSelection,
            viewModel = forwardViewModel,
            onDismiss = { forwardSelection = null },
        )
    }
}