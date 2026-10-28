package dev.behradhz.meowzix.feature.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
    requestedAlbum: dev.behradhz.meowzix.data.repository.AlbumSummary? = null,
    onAlbumRequestConsumed: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    tracksViewModel: LibraryTracksViewModel = hiltViewModel(),
    recommendationActions: RecommendationActionsViewModel = hiltViewModel(),
    libraryTools: LibraryToolsViewModel = hiltViewModel(),
    forwardViewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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
        LocalTrackChatLinkActions provides TrackChatLinkActions(
            lookup = libraryTools::telegramMessageLink,
            open = { link ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                runCatching {
                    context.startActivity(Intent(intent).setPackage("org.telegram.messenger"))
                }.recoverCatching {
                    // Support other installed Telegram clients as well.
                    context.startActivity(intent)
                }.onFailure {
                    Toast.makeText(context, "No app available to open the Telegram message", Toast.LENGTH_LONG).show()
                }
            },
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            LibraryRouteV4(
                onOpenNowPlaying = onOpenNowPlaying,
                viewModel = viewModel,
                tracksViewModel = tracksViewModel,
                requestedArtist = requestedArtist,
                onArtistRequestConsumed = onArtistRequestConsumed,
                requestedAlbum = requestedAlbum,
                onAlbumRequestConsumed = onAlbumRequestConsumed,
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
