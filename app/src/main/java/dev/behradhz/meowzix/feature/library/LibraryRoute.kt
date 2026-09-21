package dev.behradhz.meowzix.feature.library

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistAddCircle
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
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
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.ui.components.TrackArtwork

private enum class LibrarySection(val label: String) {
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists"),
}

private enum class AvailabilityFilter(val label: String) { ALL("All"), OFFLINE("Offline"), CLOUD("Cloud") }

@Composable
fun LibraryRoute(
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
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequestAttempted = true
        permissionGranted = granted
    }

    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentlyGranted = hasPermission()
                if (permissionGranted && !currentlyGranted) {
                    permissionRequestAttempted = true
                }
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
        onPlayTrack = { track ->
            viewModel.playTrack(track)
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
    )
}

@Composable
private fun LibraryScreen(
    state: LibraryUiState,
    permissionStatus: AudioPermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, java.util.UUID) -> Unit,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (java.util.UUID) -> Unit,
    onMovePlaylistTrack: (Int, Int) -> Unit,
    onRemovePlaylistTrack: (Track) -> Unit,
    onPlayPlaylist: (PlaybackMode) -> Unit,
    onSaveQueue: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var availabilityFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedSection = LibrarySection.entries[selectedSectionIndex]
    val availabilityFilter = AvailabilityFilter.entries[availabilityFilterIndex]
    val filteredTracks = remember(state.tracks, state.availability, query, availabilityFilter) {
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
        searched.filter { track ->
            when (availabilityFilter) {
                AvailabilityFilter.ALL -> true
                AvailabilityFilter.OFFLINE -> state.availability[track.id] == LibraryTrackAvailability.OFFLINE
                AvailabilityFilter.CLOUD -> state.availability[track.id] == LibraryTrackAvailability.CLOUD
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
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
                        color = if (availabilityFilterIndex == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
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
                message = "Turn audio access back on in Android settings. Telegram and app-owned music can still remain separate sources later.",
                action = "Open settings",
                onAction = onOpenSettings,
            )

            state.isRefreshing && state.tracks.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.errorMessage != null && state.tracks.isEmpty() -> LibraryMessagePanel(
                title = "Couldn't scan your music",
                message = state.errorMessage,
                action = "Try again",
                onAction = onRefresh,
            )

            state.tracks.isEmpty() -> LibraryMessagePanel(
                title = "No local music yet",
                message = "Add audio to your device or connect Telegram. Meowzix keeps both sources in one library.",
                action = "Scan again",
                onAction = onRefresh,
            )

            filteredTracks.isEmpty() -> LibraryMessagePanel(
                title = "No matches",
                message = "Nothing in your local library matches “$query”.",
                action = "Clear search",
                onAction = { query = "" },
            )

            else -> when (selectedSection) {
                LibrarySection.TRACKS -> TracksSection(
                    tracks = filteredTracks,
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

                LibrarySection.ARTISTS -> ArtistsSection(filteredTracks)
                LibrarySection.ALBUMS -> AlbumsSection(filteredTracks)
                LibrarySection.FAVORITES -> TracksSection(
                    tracks = filteredTracks.filter(Track::favorite),
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
                LibrarySection.PLAYLISTS -> PlaylistsSection(
                    playlists = state.playlists,
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
            Text(
                text = "Library",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (trackCount == 1) "1 track" else "$trackCount tracks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        HeaderAction(
            onClick = onRefresh,
            enabled = !isRefreshing,
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan local music")
        }
        Spacer(Modifier.size(6.dp))
        HeaderAction(onClick = onOpenTelegram) {
            Icon(Icons.Rounded.Cloud, contentDescription = "Open Telegram connection")
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
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
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
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
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
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search songs, artists, albums",
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
                    Icon(
                        Icons.Rounded.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    )
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
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
        ),
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
                    color = if (selected) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
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
                            text = section.label,
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
    currentTrackId: java.util.UUID?,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    availability: Map<java.util.UUID, LibraryTrackAvailability>,
    playlists: List<dev.behradhz.meowzix.domain.library.PlaylistSummary>,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, java.util.UUID) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 10.dp,
            end = 10.dp,
            top = 2.dp,
            bottom = 182.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(tracks, key = { it.id.toString() }) { track ->
            SwipeableTrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                onClick = { onPlayTrack(track) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onPinOffline = { onPinOffline(track) },
                availability = availability[track.id] ?: LibraryTrackAvailability.UNAVAILABLE,
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
    playlists: List<dev.behradhz.meowzix.domain.library.PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (java.util.UUID) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
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
            playlists = playlists,
            onFavorite = onFavorite,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    availability: LibraryTrackAvailability,
    playlists: List<dev.behradhz.meowzix.domain.library.PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (java.util.UUID) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track.artworkRef, track.title, size = 52.dp)
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
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${track.artist ?: "Unknown artist"} · ${availability.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Track actions",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    )
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
                        text = { Text(if (track.favorite) "Remove favorite" else "Favorite") },
                        leadingIcon = { Icon(if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onFavorite()
                        },
                    )
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to ${playlist.title}") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAddCircle, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist(playlist.id)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Pin offline") },
                        leadingIcon = { Icon(Icons.Rounded.DownloadForOffline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPinOffline()
                        },
                    )
                }
            }
        }
    }
}

private val LibraryTrackAvailability.label: String
    get() = when (this) {
        LibraryTrackAvailability.OFFLINE -> "Offline"
        LibraryTrackAvailability.CLOUD -> "Cloud"
        LibraryTrackAvailability.UNAVAILABLE -> "Unavailable"
    }

@Composable
private fun PlaylistsSection(
    playlists: List<dev.behradhz.meowzix.domain.library.PlaylistSummary>,
    selectedPlaylistId: java.util.UUID?,
    tracks: List<Track>,
    onCreate: () -> Unit,
    onSelect: (java.util.UUID) -> Unit,
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
                modifier = Modifier.fillMaxWidth().clickable { onSelect(playlist.id) },
                color = if (selectedPlaylistId == playlist.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("${playlist.title} · ${playlist.trackCount} tracks", modifier = Modifier.padding(12.dp))
            }
        }
        if (selectedPlaylistId != null && tracks.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(PlaybackMode.ORDERED) }) { Text("Play ordered") }
                    Button(onClick = { onPlay(PlaybackMode.PURE_SHUFFLE) }) { Text("Pure shuffle") }
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
private fun ArtistsSection(tracks: List<Track>) {
    val artists = remember(tracks) {
        tracks
            .groupBy { it.artist?.takeIf(String::isNotBlank) ?: "Unknown artist" }
            .map { (name, artistTracks) -> name to artistTracks.size }
            .sortedBy { it.first.lowercase() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 2.dp,
            bottom = 182.dp,
        ),
    ) {
        items(artists, key = { it.first }) { (artist, count) ->
            LibraryGroupRow(
                title = artist,
                subtitle = if (count == 1) "1 track" else "$count tracks",
                icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
            )
        }
    }
}

@Composable
private fun AlbumsSection(tracks: List<Track>) {
    val albums = remember(tracks) {
        tracks
            .groupBy { it.album?.takeIf(String::isNotBlank) ?: "Unknown album" }
            .map { (name, albumTracks) ->
                Triple(name, albumTracks.firstOrNull()?.artist ?: "Unknown artist", albumTracks.size)
            }
            .sortedBy { it.first.lowercase() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 2.dp,
            bottom = 182.dp,
        ),
    ) {
        items(albums, key = { it.first }) { (album, artist, count) ->
            LibraryGroupRow(
                title = album,
                subtitle = "$artist · ${if (count == 1) "1 track" else "$count tracks"}",
                icon = { Icon(Icons.Rounded.Album, contentDescription = null) },
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
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
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start,
            ) {
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
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
                )
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
