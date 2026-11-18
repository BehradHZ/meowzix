package dev.behradhz.meowzix.feature.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.domain.library.LOCAL_PLAYLIST_ID
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
    isActive: Boolean = true,
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
    var showSelectionDelete by remember { mutableStateOf(false) }
    var showSelectionPlaylists by remember { mutableStateOf(false) }
    val selectionAction by tracksViewModel.selectionAction.collectAsStateWithLifecycle()
    val selectedTracks by tracksViewModel.selectedTracks.collectAsStateWithLifecycle()
    val selectionError by tracksViewModel.selectionError.collectAsStateWithLifecycle()
    val libraryState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(selectionAction) {
        val action = selectionAction ?: return@LaunchedEffect
        when (action) {
            LibrarySelectionAction.DELETE -> showSelectionDelete = true
            LibrarySelectionAction.ADD_TO_PLAYLIST -> showSelectionPlaylists = true
            LibrarySelectionAction.SHARE -> {
                // Share a portable list even for Telegram-only tracks without local file access.
                val text = selectedTracks.values.joinToString("\n") { track ->
                    "${track.title} — ${track.artist ?: "Unknown artist"}"
                }
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Meowzix selection")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }, "Share selected songs"))
                }.onSuccess { tracksViewModel.cancelSelection() }
                    .onFailure { Toast.makeText(context, "Unable to share selection", Toast.LENGTH_LONG).show() }
            }
            else -> tracksViewModel.performSelectionAction(action)
        }
        tracksViewModel.consumeSelectionAction()
    }
    LaunchedEffect(selectionError) {
        selectionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            tracksViewModel.consumeSelectionError()
        }
    }
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
            originLookup = libraryTools::telegramChatOrigin,
            refreshOrigin = libraryTools::refreshTelegramChatOrigin,
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
                isActive = isActive,
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
        if (showSelectionDelete) AlertDialog(
            onDismissRequest = { showSelectionDelete = false },
            title = { Text("Remove ${selectedTracks.size} songs from Library?") },
            text = { Text("The songs disappear from your Library. Original device files and Telegram messages are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showSelectionDelete = false
                    tracksViewModel.performSelectionAction(LibrarySelectionAction.DELETE)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showSelectionDelete = false }) { Text("Cancel") }
            },
        )
        if (showSelectionPlaylists) AlertDialog(
            onDismissRequest = { showSelectionPlaylists = false },
            title = { Text("Add ${selectedTracks.size} songs to playlist") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    val destinations = libraryState.playlists.filter {
                        it.id != LOCAL_PLAYLIST_ID && !it.telegramManaged
                    }
                    if (destinations.isEmpty()) Text("Create a playlist first to add these songs.")
                    destinations.forEach { playlist ->
                        Text(
                            playlist.title,
                            modifier = Modifier.fillMaxSize().clickable {
                                showSelectionPlaylists = false
                                tracksViewModel.performSelectionAction(
                                    LibrarySelectionAction.ADD_TO_PLAYLIST, playlist.id,
                                )
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectionPlaylists = false }) { Text("Cancel") }
            },
        )
    }
}
