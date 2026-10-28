package dev.behradhz.meowzix.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.model.Track
import java.util.UUID

/** An independent top-level playlist destination; Library no longer owns playlist navigation. */
@Composable
fun PlaylistsRoute(
    viewModel: LibraryViewModel,
    onOpenNowPlaying: () -> Unit,
    onGoToArtist: (String) -> Unit,
    requestedPlaylistId: UUID? = null,
    onPlaylistRequestConsumed: () -> Unit = {},
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
        viewModel.resetNavigationState()
    }

    BackHandler(enabled = selectedId != null || showFavorites) { closeDetail() }

    if (selectedId != null || showFavorites) {
        val playlist = state.playlists.firstOrNull { it.id == selectedId }
        val tracks = if (showFavorites) state.tracks.filter(Track::favorite) else state.selectedPlaylistTracks
        val telegramManaged = selectedId != null && state.playlistArtwork.containsKey(selectedId)
        key(selectedKey ?: "favorites") {
            PlaylistDetailScreenV3(
                playlist = playlist,
                title = if (showFavorites) "Favorites" else playlist?.title ?: "Playlist",
                tracks = tracks,
                artworkRef = playlist?.artworkRef
                    ?: selectedId?.let(state.playlistArtwork::get)
                    ?: tracks.firstOrNull()?.artworkRef,
                editable = !showFavorites && !telegramManaged,
                isFavorites = showFavorites,
                currentTrackId = state.playback.currentTrack?.id,
                availability = state.availability,
                downloads = state.downloads,
                playlists = state.playlists,
                onBack = ::closeDetail,
                onSaveMetadata = { title, description, artwork ->
                    selectedId?.let { viewModel.updatePlaylistMetadata(it, title, description, artwork) }
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
                    track.artist?.takeIf(String::isNotBlank)?.let(onGoToArtist)
                },
                onMove = viewModel::movePlaylistTrack,
                onRemove = viewModel::removePlaylistTrack,
                onEnsureArtwork = viewModel::ensureArtwork,
                onDelete = {
                    selectedId?.let { id ->
                        viewModel.deletePlaylist(id) {
                            if (selectedKey == id.toString()) closeDetail()
                        }
                    }
                },
                deleting = selectedId != null && state.deletingPlaylistId == selectedId,
                errorMessage = state.errorMessage,
            )
        }
        return
    }

    PlaylistsSectionV3(
        playlists = state.playlists,
        playlistArtwork = state.playlistArtwork,
        favoriteTracks = state.tracks.filter(Track::favorite),
        onOpenFavorites = {
            viewModel.resetNavigationState()
            showFavorites = true
        },
        onOpenPlaylist = { id ->
            viewModel.selectPlaylist(id)
            selectedKey = id.toString()
        },
        onEnsureArtwork = viewModel::ensureArtwork,
        onCreate = { viewModel.createPlaylist { id -> selectedKey = id.toString() } },
        onSaveQueue = { viewModel.saveQueueToPlaylist { id -> selectedKey = id.toString() } },
    )
}
