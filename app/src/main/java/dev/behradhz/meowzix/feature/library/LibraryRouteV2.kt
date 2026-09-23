package dev.behradhz.meowzix.feature.library

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistAddCircle
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.core.permissions.AudioPermission
import dev.behradhz.meowzix.core.permissions.AudioPermissionStatus
import dev.behradhz.meowzix.core.permissions.audioPermissionStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.navigation.horizontalSwipeNavigation
import dev.behradhz.meowzix.ui.components.ChatAvatar
import dev.behradhz.meowzix.ui.components.DownloadableTrackArtwork
import dev.behradhz.meowzix.ui.components.TrackArtwork
import java.util.UUID

private const val FAVORITES_KEY = "__favorites__"

private enum class LibrarySectionV2(val label: String) {
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
}

private enum class AvailabilityFilterV2(val label: String) {
    ALL("All"),
    OFFLINE("Offline"),
    CLOUD("Cloud"),
}

private enum class PlaylistSortMode(val label: String) {
    PLAYLIST_ORDER("Playlist order"),
    ARTIST("Artist"),
}

private data class AlbumKeyV2(val name: String, val artist: String)

@Composable
internal fun LibraryRouteV2(
    onSwipePastEnd: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenTelegram: () -> Unit,
    viewModel: LibraryViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = AudioPermission.requiredPermission()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasPermission()) }
    var permissionRequestAttempted by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequestAttempted = true
        permissionGranted = granted
    }

    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentlyGranted = hasPermission()
                if (permissionGranted && !currentlyGranted) permissionRequestAttempted = true
                permissionGranted = currentlyGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.refresh()
    }

    LibraryScreenV2(
        state = state,
        permissionStatus = audioPermissionStatus(permissionGranted, permissionRequestAttempted),
        onRequestPermission = { permissionLauncher.launch(permission) },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
        onRefresh = viewModel::refresh,
        onPlayTrack = { track, queue ->
            viewModel.playTrack(track, queue)
            onOpenNowPlaying()
        },
        onPlayCollection = { tracks, mode ->
            viewModel.playCollection(tracks, mode)
            if (tracks.isNotEmpty()) onOpenNowPlaying()
        },
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onPinOffline = viewModel::pinOffline,
        onFavorite = viewModel::setFavorite,
        onAddToPlaylist = viewModel::addToPlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        onSelectPlaylist = viewModel::selectPlaylist,
        onRenamePlaylist = viewModel::renamePlaylist,
        onMovePlaylistTrack = viewModel::movePlaylistTrack,
        onRemovePlaylistTrack = viewModel::removePlaylistTrack,
        onSaveQueue = viewModel::saveQueueToPlaylist,
        onEnsureArtwork = viewModel::ensureArtwork,
        onOpenTelegram = onOpenTelegram,
        onSwipePastEnd = onSwipePastEnd,
    )
}

@Composable
private fun LibraryScreenV2(
    state: LibraryUiState,
    permissionStatus: AudioPermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayCollection: (List<Track>, PlaybackMode) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, UUID) -> Unit,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (UUID) -> Unit,
    onRenamePlaylist: (UUID, String) -> Unit,
    onMovePlaylistTrack: (Int, Int) -> Unit,
    onRemovePlaylistTrack: (Track) -> Unit,
    onSaveQueue: () -> Unit,
    onEnsureArtwork: (Track) -> Unit,
    onOpenTelegram: () -> Unit,
    onSwipePastEnd: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var availabilityFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var openedPlaylistKey by rememberSaveable { mutableStateOf<String?>(null) }

    val onGoToArtist: (Track) -> Unit = { track ->
        openedPlaylistKey = null
        selectedAlbumName = null
        selectedAlbumArtist = null
        selectedArtist = artistNameV2(track)
    }

    val availabilityFilter = AvailabilityFilterV2.entries[availabilityFilterIndex]
    val searchedTracks = remember(state.tracks, query, availabilityFilter) {
        val needle = query.trim()
        state.tracks.asSequence()
            .filter { track ->
                needle.isEmpty() ||
                    track.title.contains(needle, ignoreCase = true) ||
                    track.artist?.contains(needle, ignoreCase = true) == true ||
                    track.album?.contains(needle, ignoreCase = true) == true
            }
            .filter { matchesAvailabilityV2(it, state.availability, availabilityFilter) }
            .toList()
    }

    if (openedPlaylistKey != null) {
        val isFavorites = openedPlaylistKey == FAVORITES_KEY
        val playlistId = openedPlaylistKey
            ?.takeUnless { isFavorites }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val summary = playlistId?.let { id -> state.playlists.firstOrNull { it.id == id } }
        val tracks = if (isFavorites) {
            state.tracks.filter(Track::favorite)
        } else {
            state.selectedPlaylistTracks
        }
        val title = if (isFavorites) "Favorites" else summary?.title ?: "Playlist"
        val telegramManaged = playlistId != null && state.playlistArtwork.containsKey(playlistId)

        PlaylistDetailScreen(
            title = title,
            tracks = tracks,
            playlistArtworkRef = playlistId?.let(state.playlistArtwork::get)
                ?: tracks.firstOrNull()?.artworkRef,
            editable = playlistId != null && !telegramManaged,
            isFavorites = isFavorites,
            currentTrackId = state.playback.currentTrack?.id,
            availability = state.availability,
            downloads = state.downloads,
            playlists = state.playlists,
            onBack = { openedPlaylistKey = null },
            onRename = { newTitle -> if (playlistId != null) onRenamePlaylist(playlistId, newTitle) },
            onPlay = onPlayCollection,
            onPlayTrack = onPlayTrack,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onPinOffline = onPinOffline,
            onFavorite = onFavorite,
            onAddToPlaylist = onAddToPlaylist,
            onGoToArtist = onGoToArtist,
            onMove = onMovePlaylistTrack,
            onRemove = onRemovePlaylistTrack,
            onEnsureArtwork = onEnsureArtwork,
        )
        return
    }

    if (selectedArtist != null) {
        val artist = selectedArtist.orEmpty()
        val artistTracks = remember(state.tracks, artist, availabilityFilter) {
            state.tracks.filter {
                artistNameV2(it) == artist &&
                    matchesAvailabilityV2(it, state.availability, availabilityFilter)
            }
        }
        GroupDetailScreen(
            title = artist,
            subtitle = trackCountLabelV2(artistTracks.size),
            icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            tracks = artistTracks,
            currentTrackId = state.playback.currentTrack?.id,
            availability = state.availability,
            downloads = state.downloads,
            playlists = state.playlists,
            onBack = { selectedArtist = null },
            onPlayTrack = onPlayTrack,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onPinOffline = onPinOffline,
            onFavorite = onFavorite,
            onAddToPlaylist = onAddToPlaylist,
            onGoToArtist = onGoToArtist,
            onEnsureArtwork = onEnsureArtwork,
        )
        return
    }

    if (selectedAlbumName != null && selectedAlbumArtist != null) {
        val albumName = selectedAlbumName.orEmpty()
        val albumArtist = selectedAlbumArtist.orEmpty()
        val albumTracks = remember(state.tracks, albumName, albumArtist, availabilityFilter) {
            state.tracks.filter {
                albumNameV2(it) == albumName &&
                    artistNameV2(it) == albumArtist &&
                    matchesAvailabilityV2(it, state.availability, availabilityFilter)
            }
        }
        GroupDetailScreen(
            title = albumName,
            subtitle = "$albumArtist · ${trackCountLabelV2(albumTracks.size)}",
            icon = { Icon(Icons.Rounded.Album, contentDescription = null) },
            tracks = albumTracks,
            currentTrackId = state.playback.currentTrack?.id,
            availability = state.availability,
            downloads = state.downloads,
            playlists = state.playlists,
            onBack = {
                selectedAlbumName = null
                selectedAlbumArtist = null
            },
            onPlayTrack = onPlayTrack,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onPinOffline = onPinOffline,
            onFavorite = onFavorite,
            onAddToPlaylist = onAddToPlaylist,
            onGoToArtist = onGoToArtist,
            onEnsureArtwork = onEnsureArtwork,
        )
        return
    }

    val selectedSection = LibrarySectionV2.entries[selectedSectionIndex]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalSwipeNavigation(
                onSwipeLeft = {
                    if (selectedSectionIndex < LibrarySectionV2.entries.lastIndex) {
                        selectedSectionIndex++
                    } else {
                        onSwipePastEnd()
                    }
                },
                onSwipeRight = {
                    if (selectedSectionIndex > 0) selectedSectionIndex--
                },
            )
            .statusBarsPadding(),
    ) {
        LibraryHeaderV2(
            trackCount = state.tracks.size,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            onOpenTelegram = onOpenTelegram,
        )

        if (permissionStatus == AudioPermissionStatus.GRANTED || state.tracks.isNotEmpty()) {
            FrostedSearchFieldV2(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            LibrarySectionSwitcherV2(
                selectedIndex = selectedSectionIndex,
                onSelected = { selectedSectionIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvailabilityFilterV2.entries.forEachIndexed { index, filter ->
                    Surface(
                        modifier = Modifier.clickable { availabilityFilterIndex = index },
                        color = if (availabilityFilterIndex == index) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(filter.label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }

        if (state.isRefreshing && state.tracks.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        when {
            permissionStatus == AudioPermissionStatus.REQUIRED && state.tracks.isEmpty() ->
                LibraryMessagePanelV2(
                    title = "Your music, in one place",
                    message = "Allow audio access so Meowzix can build the local side of your library.",
                    action = "Allow music access",
                    onAction = onRequestPermission,
                )

            permissionStatus == AudioPermissionStatus.DENIED && state.tracks.isEmpty() ->
                LibraryMessagePanelV2(
                    title = "Music access is off",
                    message = "Turn audio access back on in Android settings. Telegram music remains available separately.",
                    action = "Open settings",
                    onAction = onOpenSettings,
                )

            state.isRefreshing && state.tracks.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.errorMessage != null && state.tracks.isEmpty() ->
                LibraryMessagePanelV2(
                    title = "Couldn't scan your music",
                    message = state.errorMessage,
                    action = "Try again",
                    onAction = onRefresh,
                )

            state.tracks.isEmpty() ->
                LibraryMessagePanelV2(
                    title = "No music yet",
                    message = "Add audio to your device or connect Telegram.",
                    action = "Scan again",
                    onAction = onRefresh,
                )

            searchedTracks.isEmpty() && selectedSection != LibrarySectionV2.PLAYLISTS ->
                LibraryMessagePanelV2(
                    title = "No matches",
                    message = "Nothing in your library matches “$query”.",
                    action = "Clear search",
                    onAction = { query = "" },
                )

            else -> when (selectedSection) {
                LibrarySectionV2.TRACKS -> TrackListV2(
                    tracks = searchedTracks,
                    queueTracks = state.tracks,
                    currentTrackId = state.playback.currentTrack?.id,
                    availability = state.availability,
                    downloads = state.downloads,
                    playlists = state.playlists,
                    onPlayTrack = onPlayTrack,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onPinOffline = onPinOffline,
                    onFavorite = onFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onGoToArtist = onGoToArtist,
                    onEnsureArtwork = onEnsureArtwork,
                )

                LibrarySectionV2.ARTISTS -> ArtistsSectionV2(
                    tracks = searchedTracks,
                    onOpenArtist = { selectedArtist = it },
                )

                LibrarySectionV2.ALBUMS -> AlbumsSectionV2(
                    tracks = searchedTracks,
                    onOpenAlbum = {
                        selectedAlbumName = it.name
                        selectedAlbumArtist = it.artist
                    },
                )

                LibrarySectionV2.PLAYLISTS -> PlaylistsSectionV2(
                    playlists = state.playlists,
                    playlistArtwork = state.playlistArtwork,
                    favoriteTracks = state.tracks.filter(Track::favorite),
                    onCreate = onCreatePlaylist,
                    onSaveQueue = onSaveQueue,
                    onOpenFavorites = { openedPlaylistKey = FAVORITES_KEY },
                    onOpenPlaylist = { id ->
                        onSelectPlaylist(id)
                        openedPlaylistKey = id.toString()
                    },
                    onEnsureArtwork = onEnsureArtwork,
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    title: String,
    tracks: List<Track>,
    playlistArtworkRef: String?,
    editable: Boolean,
    isFavorites: Boolean,
    currentTrackId: UUID?,
    availability: Map<UUID, LibraryTrackAvailability>,
    downloads: Map<UUID, OfflineDownload>,
    playlists: List<PlaylistSummary>,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onPlay: (List<Track>, PlaybackMode) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, UUID) -> Unit,
    onGoToArtist: (Track) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Track) -> Unit,
    onEnsureArtwork: (Track) -> Unit,
) {
    var sortModeIndex by rememberSaveable { mutableIntStateOf(0) }
    var editingTitle by rememberSaveable(title) { mutableStateOf(false) }
    var titleDraft by rememberSaveable(title) { mutableStateOf(title) }
    val sortMode = PlaylistSortMode.entries[sortModeIndex]
    val displayedTracks = remember(tracks, sortMode) {
        when (sortMode) {
            PlaylistSortMode.PLAYLIST_ORDER -> tracks
            PlaylistSortMode.ARTIST -> tracks.sortedWith(
                compareBy<Track>({ artistNameV2(it).lowercase() }, { it.title.lowercase() }),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to playlists")
            }

            if (playlistArtworkRef != null) {
                TrackArtwork(
                    artworkRef = playlistArtworkRef,
                    description = title,
                    size = 64.dp,
                )
            } else {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isFavorites) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isFavorites) Icons.Rounded.Favorite else Icons.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                if (editingTitle && editable) {
                    BasicTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    trackCountLabelV2(tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                )
                if (!editable && !isFavorites) {
                    Text(
                        "Telegram source playlist",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f),
                    )
                }
            }
            if (editable) {
                IconButton(
                    onClick = {
                        if (editingTitle) {
                            val normalized = titleDraft.trim()
                            if (normalized.isNotEmpty()) onRename(normalized)
                        } else {
                            titleDraft = title
                        }
                        editingTitle = !editingTitle
                    },
                ) {
                    Icon(
                        if (editingTitle) Icons.Rounded.Check else Icons.Rounded.Edit,
                        contentDescription = if (editingTitle) "Save playlist name" else "Edit playlist name",
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaylistSortMode.entries.forEachIndexed { index, mode ->
                Surface(
                    modifier = Modifier.clickable { sortModeIndex = index },
                    shape = RoundedCornerShape(14.dp),
                    color = if (sortModeIndex == index) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(mode.label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onPlay(displayedTracks, PlaybackMode.ORDERED) },
                enabled = displayedTracks.isNotEmpty(),
            ) { Text("Play") }
            Button(
                onClick = { onPlay(displayedTracks, PlaybackMode.PURE_SHUFFLE) },
                enabled = displayedTracks.isNotEmpty(),
            ) { Text("Pure shuffle") }
            Button(
                onClick = { onPlay(displayedTracks, PlaybackMode.SMART_SHUFFLE) },
                enabled = displayedTracks.isNotEmpty(),
            ) { Text("Smart shuffle") }
        }

        if (displayedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 140.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isFavorites) "Favorite a track and it will appear here." else "This playlist is empty.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 182.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    count = displayedTracks.size,
                    key = { index -> displayedTracks[index].id.toString() },
                ) { index ->
                    val track = displayedTracks[index]
                    if (
                        sortMode == PlaylistSortMode.ARTIST &&
                        (index == 0 || artistNameV2(displayedTracks[index - 1]) != artistNameV2(track))
                    ) {
                        Text(
                            artistNameV2(track),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    SwipeableTrackRowV2(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        onClick = { onPlayTrack(track, displayedTracks) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onPinOffline = { onPinOffline(track) },
                        availability = availability[track.id] ?: LibraryTrackAvailability.UNAVAILABLE,
                        download = downloads[track.id],
                        playlists = playlists,
                        onFavorite = { onFavorite(track) },
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(track, playlistId) },
                        onGoToArtist = { onGoToArtist(track) },
                        onEnsureArtwork = { onEnsureArtwork(track) },
                        onRemoveFromPlaylist = if (!isFavorites) ({ onRemove(track) }) else null,
                        onMoveUp = if (
                            !isFavorites &&
                            sortMode == PlaylistSortMode.PLAYLIST_ORDER &&
                            index > 0
                        ) ({ onMove(index, index - 1) }) else null,
                        onMoveDown = if (
                            !isFavorites &&
                            sortMode == PlaylistSortMode.PLAYLIST_ORDER &&
                            index < displayedTracks.lastIndex
                        ) ({ onMove(index, index + 1) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupDetailScreen(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    tracks: List<Track>,
    currentTrackId: UUID?,
    availability: Map<UUID, LibraryTrackAvailability>,
    downloads: Map<UUID, OfflineDownload>,
    playlists: List<PlaylistSummary>,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, UUID) -> Unit,
    onGoToArtist: (Track) -> Unit,
    onEnsureArtwork: (Track) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to library")
            }
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                    maxLines = 1,
                )
            }
        }
        TrackListV2(
            tracks = tracks,
            queueTracks = tracks,
            currentTrackId = currentTrackId,
            availability = availability,
            downloads = downloads,
            playlists = playlists,
            onPlayTrack = onPlayTrack,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onPinOffline = onPinOffline,
            onFavorite = onFavorite,
            onAddToPlaylist = onAddToPlaylist,
            onGoToArtist = onGoToArtist,
            onEnsureArtwork = onEnsureArtwork,
        )
    }
}

@Composable
private fun TrackListV2(
    tracks: List<Track>,
    queueTracks: List<Track>,
    currentTrackId: UUID?,
    availability: Map<UUID, LibraryTrackAvailability>,
    downloads: Map<UUID, OfflineDownload>,
    playlists: List<PlaylistSummary>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, UUID) -> Unit,
    onGoToArtist: (Track) -> Unit,
    onEnsureArtwork: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(tracks, key = { it.id.toString() }) { track ->
            SwipeableTrackRowV2(
                track = track,
                isCurrent = track.id == currentTrackId,
                onClick = { onPlayTrack(track, queueTracks) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onPinOffline = { onPinOffline(track) },
                availability = availability[track.id] ?: LibraryTrackAvailability.UNAVAILABLE,
                download = downloads[track.id],
                playlists = playlists,
                onFavorite = { onFavorite(track) },
                onAddToPlaylist = { playlistId -> onAddToPlaylist(track, playlistId) },
                onGoToArtist = { onGoToArtist(track) },
                onEnsureArtwork = { onEnsureArtwork(track) },
            )
        }
    }
}

@Composable
private fun SwipeableTrackRowV2(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    availability: LibraryTrackAvailability,
    download: OfflineDownload?,
    playlists: List<PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
    onGoToArtist: () -> Unit,
    onEnsureArtwork: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    LaunchedEffect(track.id, track.artworkRef) {
        onEnsureArtwork()
    }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.22f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPlayNext()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onAddToQueue()
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                val playNext = direction == SwipeToDismissBoxValue.StartToEnd
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (playNext) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = if (playNext) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (playNext) {
                            Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Play next", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Text("Add to queue", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.size(8.dp))
                            Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                        }
                    }
                }
            }
        },
    ) {
        TrackRowV2(
            track = track,
            isCurrent = isCurrent,
            onClick = onClick,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onPinOffline = onPinOffline,
            availability = availability,
            download = download,
            playlists = playlists,
            onFavorite = onFavorite,
            onAddToPlaylist = onAddToPlaylist,
            onGoToArtist = onGoToArtist,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRowV2(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    availability: LibraryTrackAvailability,
    download: OfflineDownload?,
    playlists: List<PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
    onGoToArtist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadableTrackArtwork(
                artworkRef = track.artworkRef,
                description = track.title,
                size = 52.dp,
                isOffline = availability == LibraryTrackAvailability.OFFLINE,
                download = download,
                onDownload = onPinOffline,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = "Currently playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.size(5.dp))
                    }
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${track.artist ?: "Unknown artist"} · ${availabilityLabelV2(availability)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (onMoveUp != null) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                }
            }
            if (onMoveDown != null) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
            }

            Text(
                formatDurationV2(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Go to artist") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onGoToArtist()
                        },
                    )
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to playlist · ${playlist.title}") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAddCircle, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist(playlist.id)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (track.favorite) "Remove favorite" else "Favorite") },
                        leadingIcon = {
                            Icon(
                                if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onFavorite()
                        },
                    )
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from playlist") },
                            leadingIcon = { Icon(Icons.Rounded.Clear, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRemoveFromPlaylist()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistsSectionV2(
    tracks: List<Track>,
    onOpenArtist: (String) -> Unit,
) {
    val artists = remember(tracks) {
        tracks.groupBy(::artistNameV2)
            .map { (name, artistTracks) -> name to artistTracks.size }
            .sortedBy { it.first.lowercase() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 182.dp),
    ) {
        items(artists, key = { it.first }) { (artist, count) ->
            LibraryGroupRowV2(
                title = artist,
                subtitle = trackCountLabelV2(count),
                icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                onClick = { onOpenArtist(artist) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
            )
        }
    }
}

@Composable
private fun AlbumsSectionV2(
    tracks: List<Track>,
    onOpenAlbum: (AlbumKeyV2) -> Unit,
) {
    val albums = remember(tracks) {
        tracks.groupBy { AlbumKeyV2(albumNameV2(it), artistNameV2(it)) }
            .map { (key, albumTracks) -> key to albumTracks.size }
            .sortedWith(compareBy({ it.first.name.lowercase() }, { it.first.artist.lowercase() }))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 182.dp),
    ) {
        items(albums, key = { "${it.first.artist}:${it.first.name}" }) { (album, count) ->
            LibraryGroupRowV2(
                title = album.name,
                subtitle = "${album.artist} · ${trackCountLabelV2(count)}",
                icon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                onClick = { onOpenAlbum(album) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
            )
        }
    }
}

@Composable
private fun PlaylistsSectionV2(
    playlists: List<PlaylistSummary>,
    playlistArtwork: Map<UUID, String?>,
    favoriteTracks: List<Track>,
    onCreate: () -> Unit,
    onSaveQueue: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (UUID) -> Unit,
    onEnsureArtwork: (Track) -> Unit,
) {
    favoriteTracks.firstOrNull()?.let { track ->
        LaunchedEffect(track.id, track.artworkRef) { onEnsureArtwork(track) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onCreate) { Text("New playlist") }
                Button(onClick = onSaveQueue) { Text("Save queue") }
            }
        }

        item(key = FAVORITES_KEY) {
            PlaylistCardV2(
                title = "Favorites",
                subtitle = trackCountLabelV2(favoriteTracks.size),
                artworkRef = favoriteTracks.firstOrNull()?.artworkRef,
                favorite = true,
                onClick = onOpenFavorites,
            )
        }

        items(playlists, key = { it.id.toString() }) { playlist ->
            PlaylistCardV2(
                title = playlist.title,
                subtitle = trackCountLabelV2(playlist.trackCount),
                artworkRef = playlistArtwork[playlist.id],
                favorite = false,
                onClick = { onOpenPlaylist(playlist.id) },
            )
        }
    }
}

@Composable
private fun PlaylistCardV2(
    title: String,
    subtitle: String,
    artworkRef: String?,
    favorite: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (artworkRef != null) {
                ChatAvatar(artworkRef, title, size = 52.dp)
            } else {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.PlaylistPlay,
                            contentDescription = null,
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                )
            }
        }
    }
}

@Composable
private fun LibraryGroupRowV2(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun LibraryHeaderV2(
    trackCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Library", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                trackCountLabelV2(trackCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        HeaderActionV2(onClick = onRefresh, enabled = !isRefreshing) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan local music")
        }
        Spacer(Modifier.size(6.dp))
        HeaderActionV2(onClick = onOpenTelegram) {
            Icon(Icons.Rounded.Cloud, contentDescription = "Open Telegram connection")
        }
    }
}

@Composable
private fun HeaderActionV2(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun FrostedSearchFieldV2(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Search songs, artists, albums",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionSwitcherV2(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LibrarySectionV2.entries.forEachIndexed { index, section ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .width(104.dp)
                        .height(38.dp)
                        .clickable { onSelected(index) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = when (section) {
                                LibrarySectionV2.TRACKS -> Icons.Rounded.MusicNote
                                LibrarySectionV2.ARTISTS -> Icons.Rounded.Person
                                LibrarySectionV2.ALBUMS -> Icons.Rounded.Album
                                LibrarySectionV2.PLAYLISTS -> Icons.Rounded.PlaylistAddCircle
                            },
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            section.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryMessagePanelV2(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, bottom = 150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null)
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
                )
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

private fun matchesAvailabilityV2(
    track: Track,
    availability: Map<UUID, LibraryTrackAvailability>,
    filter: AvailabilityFilterV2,
): Boolean = when (filter) {
    AvailabilityFilterV2.ALL -> true
    AvailabilityFilterV2.OFFLINE -> availability[track.id] == LibraryTrackAvailability.OFFLINE
    AvailabilityFilterV2.CLOUD -> availability[track.id] == LibraryTrackAvailability.CLOUD
}

private fun artistNameV2(track: Track): String =
    track.artist?.takeIf(String::isNotBlank) ?: "Unknown artist"

private fun albumNameV2(track: Track): String =
    track.album?.takeIf(String::isNotBlank) ?: "Unknown album"

private fun availabilityLabelV2(availability: LibraryTrackAvailability): String = when (availability) {
    LibraryTrackAvailability.OFFLINE -> "Offline"
    LibraryTrackAvailability.CLOUD -> "Cloud"
    LibraryTrackAvailability.UNAVAILABLE -> "Unavailable"
}

private fun trackCountLabelV2(count: Int): String =
    if (count == 1) "1 track" else "$count tracks"

private fun formatDurationV2(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
