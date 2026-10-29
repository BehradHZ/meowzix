package dev.behradhz.meowzix.feature.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.model.Track
import java.util.UUID

private const val FAVORITES_KEY = "favorites"

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
    onOpenNowPlaying: () -> Unit,
    onGoToArtist: (String) -> Unit,
    requestedPlaylistId: UUID? = null,
    onPlaylistRequestConsumed: () -> Unit = {},
    reduceMotion: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    val selectedId = selectedKey?.let(UUID::fromString)

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

    BackHandler(enabled = selectedId != null || showFavorites) { closeDetail() }

    PlaylistContentTransition(
        selectedKey = if (showFavorites) FAVORITES_KEY else selectedKey,
        reduceMotion = reduceMotion,
        onGridSettled = viewModel::resetNavigationState,
        gridContent = {
            PlaylistsSectionV3(
                playlists = state.playlists,
                playlistArtwork = state.playlistArtwork,
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
                onCreate = { viewModel.createPlaylist { id -> selectedKey = id.toString() } },
                onSaveQueue = { viewModel.saveQueueToPlaylist { id -> selectedKey = id.toString() } },
            )
        },
        detailContent = { contentKey ->
            val favorites = contentKey == FAVORITES_KEY
            val playlistId = if (favorites) null else UUID.fromString(contentKey)
            val playlist = state.playlists.firstOrNull { it.id == playlistId }
            val tracks = if (favorites) state.tracks.filter(Track::favorite) else state.selectedPlaylistTracks
            val telegramManaged = playlistId != null && state.playlistArtwork.containsKey(playlistId)

            PlaylistDetailScreenV3(
                playlist = playlist,
                title = if (favorites) "Favorites" else playlist?.title ?: "Playlist",
                tracks = tracks,
                artworkRef = playlist?.artworkRef
                    ?: playlistId?.let(state.playlistArtwork::get)
                    ?: tracks.firstOrNull()?.artworkRef,
                editable = !favorites && !telegramManaged,
                canEditMetadata = !favorites,
                isFavorites = favorites,
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
                onFavorite = viewModel::setFavorite,
                onAddToPlaylist = viewModel::addToPlaylist,
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
            )
        },
    )
}
