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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DownloadForOffline
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

private enum class LibrarySection(val label: String) {
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists"),
}

private enum class AvailabilityFilter(val label: String) {
    ALL("All"),
    OFFLINE("Offline"),
    CLOUD("Cloud"),
}

private data class AlbumKey(val name: String, val artist: String)

@Composable
fun LibraryRoute(
    onSwipePastEnd: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onOpenTelegram: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
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

    LibraryScreen(
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
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onPinOffline = viewModel::pinOffline,
        onFavorite = viewModel::setFavorite,
        onAddToPlaylist = viewModel::addToPlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        onSelectPlaylist = viewModel::selectPlaylist,
        onMovePlaylistTrack = viewModel::movePlaylistTrack,
        onRemovePlaylistTrack = viewModel::removePlaylistTrack,
        onPlayPlaylist = viewModel::playSelectedPlaylist,
        onSaveQueue = viewModel::saveQueueToPlaylist,
        onOpenTelegram = onOpenTelegram,
        onSwipePastEnd = onSwipePastEnd,
    )
}

@Composable
private fun LibraryScreen(
    state: LibraryUiState,
    permissionStatus: AudioPermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, UUID) -> Unit,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (UUID) -> Unit,
    onMovePlaylistTrack: (Int, Int) -> Unit,
    onRemovePlaylistTrack: (Track) -> Unit,
    onPlayPlaylist: (PlaybackMode) -> Unit,
    onSaveQueue: () -> Unit,
    onOpenTelegram: () -> Unit,
    onSwipePastEnd: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var availabilityFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumArtist by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedSection = LibrarySection.entries[selectedSectionIndex]
    val availabilityFilter = AvailabilityFilter.entries[availabilityFilterIndex]
    val searchedTracks = remember(state.tracks, query, availabilityFilter) {
        val normalizedQuery = query.trim()
        val searched = if (normalizedQuery.isEmpty()) {
            state.tracks
        } else {
            state.tracks.filter { track ->
                track.title.contains(normalizedQuery, ignoreCase = true) ||
                    track.artist?.contains(normalizedQuery, ignoreCase = true) == true ||
                    track.album?.contains(normalizedQuery, ignoreCase = true) == true
            }
        }
        searched.filter { track -> matchesAvailability(track, state.availability, availabilityFilter) }
    }

    val artistDetailTracks = selectedArtist?.let { artist ->
        state.tracks.filter {
            artistName(it) == artist && matchesAvailability(it, state.availability, availabilityFilter)
        }
    }.orEmpty()
    val albumDetailTracks = if (selectedAlbumName != null && selectedAlbumArtist != null) {
        state.tracks.filter {
            albumName(it) == selectedAlbumName &&
                artistName(it) == selectedAlbumArtist &&
                matchesAvailability(it, state.availability, availabilityFilter)
        }
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalSwipeNavigation(
                enabled = selectedArtist == null && selectedAlbumName == null,
                onSwipeLeft = {
                    if (selectedSectionIndex < LibrarySection.entries.lastIndex) selectedSectionIndex++
                    else onSwipePastEnd()
                },
                onSwipeRight = { if (selectedSectionIndex > 0) selectedSectionIndex-- },
            )
            .statusBarsPadding(),
    ) {
        if (selectedArtist != null) {
            GroupDetailHeader(
                title = selectedArtist.orEmpty(),
                subtitle = trackCountLabel(artistDetailTracks.size),
                icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                onBack = { selectedArtist = null },
            )
            TracksSection(
                tracks = artistDetailTracks,
                queueTracks = artistDetailTracks,
                currentTrackId = state.playback.currentTrack?.id,
                onPlayTrack = onPlayTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPinOffline = onPinOffline,
                availability = state.availability,
                downloads = state.downloads,
                playlists = state.playlists,
                onFavorite = onFavorite,
                onAddToPlaylist = onAddToPlaylist,
            )
            return@Column
        }

        if (selectedAlbumName != null && selectedAlbumArtist != null) {
            GroupDetailHeader(
                title = selectedAlbumName.orEmpty(),
                subtitle = "${selectedAlbumArtist.orEmpty()} · ${trackCountLabel(albumDetailTracks.size)}",
                icon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                onBack = {
                    selectedAlbumName = null
                    selectedAlbumArtist = null
                },
            )
            TracksSection(
                tracks = albumDetailTracks,
                queueTracks = albumDetailTracks,
                currentTrackId = state.playback.currentTrack?.id,
                onPlayTrack = onPlayTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPinOffline = onPinOffline,
                availability = state.availability,
                downloads = state.downloads,
                playlists = state.playlists,
                onFavorite = onFavorite,
                onAddToPlaylist = onAddToPlaylist,
            )
            return@Column
        }

        LibraryHeader(
            trackCount = state.tracks.size,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            onOpenTelegram = onOpenTelegram,
        )

        if (permissionStatus == AudioPermissionStatus.GRANTED || state.tracks.isNotEmpty()) {
            FrostedSearchField(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            LibrarySectionSwitcher(
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
                AvailabilityFilter.entries.forEachIndexed { index, filter ->
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
            permissionStatus == AudioPermissionStatus.REQUIRED && state.tracks.isEmpty() -> LibraryMessagePanel(
                title = "Your music, in one place",
                message = "Allow audio access so Meowzix can build the local side of your library.",
                action = "Allow music access",
                onAction = onRequestPermission,
            )

            permissionStatus == AudioPermissionStatus.DENIED && state.tracks.isEmpty() -> LibraryMessagePanel(
                title = "Music access is off",
                message = "Turn audio access back on in Android settings. Telegram music remains available separately.",
                action = "Open settings",
                onAction = onOpenSettings,
            )

            state.isRefreshing && state.tracks.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.errorMessage != null && state.tracks.isEmpty() -> LibraryMessagePanel(
                title = "Couldn't scan your music",
                message = state.errorMessage,
                action = "Try again",
                onAction = onRefresh,
            )

            state.tracks.isEmpty() -> LibraryMessagePanel(
                title = "No music yet",
                message = "Add audio to your device or connect Telegram.",
                action = "Scan again",
                onAction = onRefresh,
            )

            searchedTracks.isEmpty() && selectedSection != LibrarySection.PLAYLISTS -> LibraryMessagePanel(
                title = "No matches",
                message = "Nothing in your library matches “$query”.",
                action = "Clear search",
                onAction = { query = "" },
            )

            else -> when (selectedSection) {
                LibrarySection.TRACKS -> TracksSection(
                    tracks = searchedTracks,
                    // Requirement: tapping a normal track creates a queue from the whole library,
                    // not only the currently visible search/filter subset.
                    queueTracks = state.tracks,
                    currentTrackId = state.playback.currentTrack?.id,
                    onPlayTrack = onPlayTrack,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onPinOffline = onPinOffline,
                    availability = state.availability,
                    downloads = state.downloads,
                    playlists = state.playlists,
                    onFavorite = onFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                )

                LibrarySection.ARTISTS -> ArtistsSection(
                    tracks = searchedTracks,
                    onOpenArtist = { selectedArtist = it },
                )

                LibrarySection.ALBUMS -> AlbumsSection(
                    tracks = searchedTracks,
                    onOpenAlbum = { album ->
                        selectedAlbumName = album.name
                        selectedAlbumArtist = album.artist
                    },
                )

                LibrarySection.FAVORITES -> {
                    val favorites = searchedTracks.filter(Track::favorite)
                    TracksSection(
                        tracks = favorites,
                        queueTracks = state.tracks.filter(Track::favorite),
                        currentTrackId = state.playback.currentTrack?.id,
                        onPlayTrack = onPlayTrack,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onPinOffline = onPinOffline,
                        availability = state.availability,
                        playlists = state.playlists,
                        onFavorite = onFavorite,
                        onAddToPlaylist = onAddToPlaylist,
                    )
                }

                LibrarySection.PLAYLISTS -> PlaylistsSection(
                    playlists = state.playlists,
                    playlistArtwork = state.playlistArtwork,
                    selectedPlaylistId = state.selectedPlaylistId,
                    tracks = state.selectedPlaylistTracks,
                    onCreate = onCreatePlaylist,
                    onSelect = onSelectPlaylist,
                    onMove = onMovePlaylistTrack,
                    onRemove = onRemovePlaylistTrack,
                    onPlay = onPlayPlaylist,
                    onSaveQueue = onSaveQueue,
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(
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
                trackCountLabel(trackCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        HeaderAction(onClick = onRefresh, enabled = !isRefreshing) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan local music")
        }
        Spacer(Modifier.size(6.dp))
        HeaderAction(onClick = onOpenTelegram) {
            Icon(Icons.Rounded.Cloud, contentDescription = "Open Telegram connection")
        }
    }
}

@Composable
private fun GroupDetailHeader(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onBack: () -> Unit,
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
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeaderAction(
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
private fun FrostedSearchField(
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
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
private fun LibrarySectionSwitcher(
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
            LibrarySection.entries.forEachIndexed { index, section ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .width(104.dp)
                        .height(38.dp)
                        .clickable { onSelected(index) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.86f) else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = when (section) {
                                LibrarySection.TRACKS -> Icons.Rounded.MusicNote
                                LibrarySection.ARTISTS -> Icons.Rounded.Person
                                LibrarySection.ALBUMS -> Icons.Rounded.Album
                                LibrarySection.FAVORITES -> Icons.Rounded.Favorite
                                LibrarySection.PLAYLISTS -> Icons.Rounded.PlaylistAddCircle
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
private fun TracksSection(
    tracks: List<Track>,
    queueTracks: List<Track>,
    currentTrackId: UUID?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    availability: Map<UUID, LibraryTrackAvailability>,
    downloads: Map<UUID, OfflineDownload>,
    playlists: List<PlaylistSummary>,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, UUID) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(tracks, key = { it.id.toString() }) { track ->
            SwipeableTrackRow(
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
            )
        }
    }
}

@Composable
private fun SwipeableTrackRow(
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
) {
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
            val playNext = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = if (playNext) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
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
        },
    ) {
        TrackRow(
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
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
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
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
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
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${track.artist ?: "Unknown artist"} · ${availability.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                formatDuration(track.durationMs),
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
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = { menuExpanded = false; onAddToQueue() },
                    )
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                        onClick = { menuExpanded = false; onPlayNext() },
                    )
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to playlist · ${playlist.title}") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAddCircle, contentDescription = null) },
                            onClick = { menuExpanded = false; onAddToPlaylist(playlist.id) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (track.favorite) "Remove favorite" else "Favorite") },
                        leadingIcon = { Icon(if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null) },
                        onClick = { menuExpanded = false; onFavorite() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsSection(
    tracks: List<Track>,
    onOpenArtist: (String) -> Unit,
) {
    val artists = remember(tracks) {
        tracks.groupBy(::artistName)
            .map { (name, artistTracks) -> name to artistTracks.size }
            .sortedBy { it.first.lowercase() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 182.dp),
    ) {
        items(artists, key = { it.first }) { (artist, count) ->
            LibraryGroupRow(
                title = artist,
                subtitle = trackCountLabel(count),
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
private fun AlbumsSection(
    tracks: List<Track>,
    onOpenAlbum: (AlbumKey) -> Unit,
) {
    val albums = remember(tracks) {
        tracks.groupBy { AlbumKey(albumName(it), artistName(it)) }
            .map { (key, albumTracks) -> key to albumTracks.size }
            .sortedWith(compareBy({ it.first.name.lowercase() }, { it.first.artist.lowercase() }))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 182.dp),
    ) {
        items(albums, key = { "${it.first.artist}:${it.first.name}" }) { (album, count) ->
            LibraryGroupRow(
                title = album.name,
                subtitle = "${album.artist} · ${trackCountLabel(count)}",
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
private fun LibraryGroupRow(
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
private fun PlaylistsSection(
    playlists: List<PlaylistSummary>,
    playlistArtwork: Map<UUID, String?>,
    selectedPlaylistId: UUID?,
    tracks: List<Track>,
    onCreate: () -> Unit,
    onSelect: (UUID) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Track) -> Unit,
    onPlay: (PlaybackMode) -> Unit,
    onSaveQueue: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreate) { Text("New playlist") }
                Button(onClick = onSaveQueue) { Text("Save queue") }
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(playlist.id) },
                color = if (selectedPlaylistId == playlist.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChatAvatar(playlistArtwork[playlist.id], playlist.title, size = 46.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(playlist.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(trackCountLabel(playlist.trackCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
                    }
                }
            }
        }
        if (selectedPlaylistId != null && tracks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onPlay(PlaybackMode.ORDERED) }) { Text("Play ordered") }
                    Button(onClick = { onPlay(PlaybackMode.PURE_SHUFFLE) }) { Text("Pure shuffle") }
                    Button(onClick = { onPlay(PlaybackMode.SMART_SHUFFLE) }) { Text("Smart shuffle") }
                }
            }
            items(tracks.size, key = { tracks[it].id }) { index ->
                val track = tracks[index]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(track.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < tracks.lastIndex) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                    }
                    Button(onClick = { onRemove(track) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun LibraryMessagePanel(
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

private fun matchesAvailability(
    track: Track,
    availability: Map<UUID, LibraryTrackAvailability>,
    filter: AvailabilityFilter,
): Boolean = when (filter) {
    AvailabilityFilter.ALL -> true
    AvailabilityFilter.OFFLINE -> availability[track.id] == LibraryTrackAvailability.OFFLINE
    AvailabilityFilter.CLOUD -> availability[track.id] == LibraryTrackAvailability.CLOUD
}

private fun artistName(track: Track): String =
    track.artist?.takeIf(String::isNotBlank) ?: "Unknown artist"

private fun albumName(track: Track): String =
    track.album?.takeIf(String::isNotBlank) ?: "Unknown album"

private val LibraryTrackAvailability.label: String
    get() = when (this) {
        LibraryTrackAvailability.OFFLINE -> "Offline"
        LibraryTrackAvailability.CLOUD -> "Cloud"
        LibraryTrackAvailability.UNAVAILABLE -> "Unavailable"
    }

private fun trackCountLabel(count: Int): String = if (count == 1) "1 track" else "$count tracks"

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
