package dev.behradhz.meowzix.feature.library

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import dev.behradhz.meowzix.core.permissions.AudioPermission
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.feature.search.matchesGlobalSearch
import java.util.UUID

internal enum class PlaylistDockReselectAction { CLOSE_DETAIL, SCROLL_GRID_TOP }

internal fun playlistDockReselectAction(showingDetails: Boolean): PlaylistDockReselectAction =
    if (showingDetails) PlaylistDockReselectAction.CLOSE_DETAIL
    else PlaylistDockReselectAction.SCROLL_GRID_TOP

private const val FAVORITES_KEY = "favorites"
private const val CREATE_PLAYLIST_KEY = "create-playlist"

/**
 * Keep both surfaces composed while opening/closing, then dispose the outgoing screen.
 * The selected key is also the content identity, so a restored or externally requested
 * playlist receives the same transition as a tap on a collection card.
 */
@Composable
internal fun PlaylistContentTransition(
    selectedKey: String?,
    reduceMotion: Boolean,
    onGridSettled: () -> Unit,
    gridContent: @Composable () -> Unit,
    detailContent: @Composable (String) -> Unit,
) {
    val transition = updateTransition(targetState = selectedKey, label = "playlist-detail-transition")

    // Do not clear the selected track collection while the old detail is still animating out.
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (transition.currentState == null && transition.targetState == null) onGridSettled()
    }

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (targetState != null) {
                (
                    fadeIn(animationSpec = tween(290)) +
                        scaleIn(initialScale = 0.965f, animationSpec = tween(390, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(
                            animationSpec = tween(390, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> width / 9 },
                        )
                    ) togetherWith (
                    fadeOut(animationSpec = tween(190)) +
                        scaleOut(targetScale = 0.985f, animationSpec = tween(230))
                    )
            } else {
                (
                    fadeIn(animationSpec = tween(260)) +
                        slideInHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> -width / 14 },
                        )
                    ) togetherWith (
                    fadeOut(animationSpec = tween(210)) +
                        slideOutHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            targetOffsetX = { width -> width / 8 },
                        )
                    )
            }
        },
    ) { contentKey ->
        if (contentKey == null) gridContent() else detailContent(contentKey)
    }
}

/** An independent top-level playlist destination; Library no longer owns playlist navigation. */
@Composable
fun PlaylistsRoute(
    viewModel: LibraryViewModel,
    isActive: Boolean = true,
    onOpenNowPlaying: () -> Unit,
    onGoToArtist: (String) -> Unit,
    searchQuery: String = "",
    requestedPlaylistId: UUID? = null,
    reselectRequest: Int = 0,
    onPlaylistRequestConsumed: () -> Unit = {},
    reduceMotion: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val collapsedTelegramSections by viewModel.collapsedTelegramPlaylistSections.collectAsStateWithLifecycle(
        initialValue = emptySet(),
    )
    val playlistGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioPermission = AudioPermission.requiredPermission()
    var pendingLocalRefresh by rememberSaveable { mutableStateOf<String?>(null) }
    var showPermissionHelp by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingLocalRefresh
        if (granted) {
            pendingLocalRefresh = null
            pending?.let { viewModel.refreshPlaylist(UUID.fromString(it)) }
        } else if (pending != null) {
            showPermissionHelp = true
        }
    }
    // A permanent denial opens Android Settings; resume the requested scan after granting there.
    DisposableEffect(lifecycleOwner, audioPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                pendingLocalRefresh != null &&
                ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
            ) {
                val pending = pendingLocalRefresh
                pendingLocalRefresh = null
                showPermissionHelp = false
                pending?.let { viewModel.refreshPlaylist(UUID.fromString(it)) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (showPermissionHelp) {
        AlertDialog(
            onDismissRequest = { showPermissionHelp = false; pendingLocalRefresh = null },
            title = { Text("Audio access required") },
            text = { Text("Allow access to device audio to rescan local music. You can enable it in Android settings if the permission prompt is unavailable.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionHelp = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionHelp = false; pendingLocalRefresh = null }) { Text("Cancel") }
            },
        )
    }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var newPlaylistTitle by rememberSaveable { mutableStateOf("New playlist") }
    var creatingPlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistCreationError by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedId = selectedKey?.takeUnless { it == CREATE_PLAYLIST_KEY }?.let(UUID::fromString)

    LaunchedEffect(requestedPlaylistId) {
        if (requestedPlaylistId != null) {
            selectedKey = requestedPlaylistId.toString()
            showFavorites = false
            onPlaylistRequestConsumed()
        }
    }
    LaunchedEffect(selectedId) {
        selectedId?.let(viewModel::selectPlaylist)
    }

    fun closeDetail() {
        selectedKey = null
        showFavorites = false
    }

    var gridScrollToTopRequest by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Host this effect above the detail transition: the grid can be disposed and
    // remounted, but a previous reselect must not scroll it on the first tap.
    LaunchedEffect(gridScrollToTopRequest) {
        if (gridScrollToTopRequest > 0) playlistGridState.animateScrollToItem(0)
    }
    LaunchedEffect(reselectRequest) {
        if (reselectRequest <= 0) return@LaunchedEffect
        when (playlistDockReselectAction(selectedKey != null || showFavorites)) {
            PlaylistDockReselectAction.CLOSE_DETAIL -> if (!creatingPlaylist) closeDetail()
            PlaylistDockReselectAction.SCROLL_GRID_TOP -> gridScrollToTopRequest++
        }
    }

    BackHandler(enabled = isActive && (selectedKey != null || showFavorites)) {
        if (!creatingPlaylist) closeDetail()
    }

    PlaylistContentTransition(
        selectedKey = if (showFavorites) FAVORITES_KEY else selectedKey,
        reduceMotion = reduceMotion,
        onGridSettled = viewModel::resetNavigationState,
        gridContent = {
            PlaylistsSectionV3(
                gridState = playlistGridState,
                playlists = if (searchQuery.isBlank()) state.playlists else state.playlists.filter {
                    matchesGlobalSearch(searchQuery, it.title, it.description)
                },
                playlistArtwork = state.playlistArtwork,
                newTracksByPlaylist = if (state.highlightNewTelegramTracks) state.newTelegramTracksByPlaylist else emptyMap(),
                reduceMotion = reduceMotion,
                favoriteTracks = state.tracks.filter(Track::favorite),
                onOpenFavorites = {
                    viewModel.resetNavigationState()
                    selectedKey = null
                    showFavorites = true
                },
                onOpenPlaylist = { id ->
                    viewModel.selectPlaylist(id)
                    showFavorites = false
                    selectedKey = id.toString()
                },
                onCreate = {
                    newPlaylistTitle = "Playlist ${state.playlists.count { !it.localManaged } + 1}"
                    playlistCreationError = null
                    creatingPlaylist = false
                    showFavorites = false
                    selectedKey = CREATE_PLAYLIST_KEY
                },
                onSaveQueue = { viewModel.saveQueueToPlaylist { id -> selectedKey = id.toString() } },
            )
        },
        detailContent = { contentKey ->
            if (contentKey == CREATE_PLAYLIST_KEY) {
                PlaylistEditScreenV3(
                    initialTitle = newPlaylistTitle,
                    initialDescription = null,
                    initialArtworkRef = null,
                    telegramManaged = false,
                    isModified = false,
                    onRestore = {},
                    creating = true,
                    isSaving = creatingPlaylist,
                    saveError = playlistCreationError,
                    onBack = { if (!creatingPlaylist) closeDetail() },
                    onSave = { title, description, artwork ->
                        if (!creatingPlaylist) {
                            creatingPlaylist = true
                            playlistCreationError = null
                            viewModel.createPlaylist(
                                title = title,
                                description = description,
                                artworkRef = artwork,
                                onCreated = { id ->
                                    creatingPlaylist = false
                                    selectedKey = id.toString()
                                },
                                onFailed = { message ->
                                    creatingPlaylist = false
                                    playlistCreationError = message
                                },
                            )
                        }
                    },
                )
            } else {
            val favorites = contentKey == FAVORITES_KEY
            val playlistId = if (favorites) null else UUID.fromString(contentKey)
            val playlist = state.playlists.firstOrNull { it.id == playlistId }
            val tracks = if (favorites) state.tracks.filter(Track::favorite) else state.selectedPlaylistTracks
            val telegramManaged = playlist?.telegramManaged == true

            PlaylistDetailScreenV3(
                isActive = isActive,
                playlist = playlist,
                title = if (favorites) "Favorites" else playlist?.title ?: "Playlist",
                tracks = tracks,
                searchQuery = searchQuery,
                artworkRef = playlist?.artworkRef
                    ?: playlistId?.let(state.playlistArtwork::get)
                    ?: tracks.firstOrNull()?.artworkRef,
                editable = !favorites && !telegramManaged && playlist?.localManaged != true,
                canEditMetadata = !favorites && playlist?.localManaged != true,
                onRestoreTelegramMetadata = { playlistId?.let(viewModel::restoreTelegramPlaylistMetadata) },
                isFavorites = favorites,
                newTrackIds = if (state.highlightNewTelegramTracks && playlist?.newTrackHighlightEnabled == true)
                    playlistId?.let { state.newTelegramTracksByPlaylist[it] }.orEmpty() else emptySet(),
                reduceMotion = reduceMotion,
                onNewSongsToggle = { enabled -> playlistId?.let { viewModel.setNewTrackHighlightEnabled(it, enabled) } },
                // Only PlaybackService acknowledges an unheard song when audio starts.
                currentTrackId = state.playback.currentTrack?.id,
                availability = state.availability,
                downloads = state.downloads,
                playlists = state.playlists,
                onBack = ::closeDetail,
                onSaveMetadata = { title, description, artwork ->
                    playlistId?.let { viewModel.updatePlaylistMetadata(it, title, description, artwork) }
                },
                onPlay = { collection, mode -> viewModel.playPlaylistCollection(collection, mode) },
                onPlayTrack = { track, collection ->
                    viewModel.playTrack(track, collection)
                    onOpenNowPlaying()
                },
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onPinOffline = viewModel::pinOffline,
                onRemoveOfflineCopy = viewModel::removeOfflineCopy,
                onRemoveTrack = viewModel::removeTrack,
                onFavorite = viewModel::setFavorite,
                onAddToPlaylist = viewModel::addToPlaylist,
                libraryTrackIds = if (favorites) tracks.map(Track::id).toSet() else state.selectedPlaylistLibraryTrackIds,
                onAddToLibrary = viewModel::addToLibrary,
                onBulkAddToLibrary = viewModel::addTracksToLibrary,
                onBulkRemoveFromLibrary = viewModel::removeTracksFromLibrary,
                onBulkAddToPlaylist = viewModel::addTracksToPlaylist,
                onBulkAddToQueue = viewModel::addTracksToQueue,
                onBulkLike = viewModel::likeTracks,
                onBulkRemove = viewModel::removePlaylistTracks,
                telegramPerson = state.selectedPlaylistIsPerson,
                profileTrackIds = state.selectedPlaylistProfileTrackIds,
                messageTrackIds = state.selectedPlaylistChatTrackIds,
                profileRefreshing = state.isRefreshingPersonProfile,
                profileRefreshStatus = state.personProfileRefreshStatus,
                profileRefreshError = state.personProfileRefreshError,
                onRefreshProfile = viewModel::refreshSelectedPersonProfile,
                collapsedTelegramSections = collapsedTelegramSections,
                onTelegramSectionExpandedChange = { section, expanded ->
                    playlistId?.let { viewModel.setTelegramPlaylistSectionExpanded(it, section, expanded) }
                },
                onGoToArtist = { track ->
                    closeDetail()
                    viewModel.resetNavigationState()
                    track.artist?.takeIf(String::isNotBlank)?.let(onGoToArtist)
                },
                onMove = viewModel::movePlaylistTrack,
                onRemove = viewModel::removePlaylistTrack,
                onEnsureArtwork = viewModel::ensureArtwork,
                onDelete = {
                    playlistId?.let { id ->
                        viewModel.deletePlaylist(id) {
                            if (selectedKey == id.toString()) closeDetail()
                        }
                    }
                },
                deleting = playlistId != null && state.deletingPlaylistId == playlistId,
                errorMessage = state.errorMessage,
                refreshing = state.refreshingPlaylistId == playlistId,
                refreshError = state.playlistRefreshError,
                refreshStatus = state.playlistRefreshStatus,
                onRefresh = playlistId?.let { id ->
                    {
                        if (telegramManaged) {
                            viewModel.refreshPlaylist(id)
                        } else if (ContextCompat.checkSelfPermission(context, audioPermission) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.refreshPlaylist(id)
                        } else {
                            pendingLocalRefresh = id.toString()
                            permissionLauncher.launch(audioPermission)
                        }
                    }
                },
            )
            }
        },
    )
}
