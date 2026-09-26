package dev.behradhz.meowzix.feature.library

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import dev.behradhz.meowzix.domain.settings.LibraryDisplaySettings
import dev.behradhz.meowzix.domain.settings.LibraryGroupMode
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
import java.util.UUID

private enum class LibrarySectionV3(val label: String) {
    TRACKS("Tracks"), ARTISTS("Artists"), ALBUMS("Albums"), PLAYLISTS("Playlists")
}

private enum class AvailabilityV3(val label: String) {
    ALL("All"), OFFLINE("Offline"), CLOUD("Cloud")
}

private sealed interface LibraryDetailV3 {
    data class Artist(val name: String) : LibraryDetailV3
    data class Album(val name: String, val artist: String) : LibraryDetailV3
    data object Favorites : LibraryDetailV3
    data class Playlist(val id: UUID, val title: String) : LibraryDetailV3
}

private sealed interface TrackListItemV3 {
    data class Header(val label: String) : TrackListItemV3
    data class Song(val track: Track) : TrackListItemV3
}

@Composable
internal fun LibraryRouteV3(
    onOpenNowPlaying: () -> Unit,
    viewModel: LibraryViewModel,
    displayViewModel: LibraryDisplayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val displaySettings by displayViewModel.settings.collectAsStateWithLifecycle()
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
                val nowGranted = hasPermission()
                if (permissionGranted && !nowGranted) permissionRequestAttempted = true
                permissionGranted = nowGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.refresh()
    }

    LibraryScreenV3(
        state = state,
        displaySettings = displaySettings,
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
        onSortMode = displayViewModel::setSortMode,
        onGroupMode = displayViewModel::setGroupMode,
        onPlayTrack = { track, queue ->
            viewModel.playTrack(track, queue)
            onOpenNowPlaying()
        },
        onPlayCollection = { tracks ->
            viewModel.playCollection(tracks, PlaybackMode.ORDERED)
            if (tracks.isNotEmpty()) onOpenNowPlaying()
        },
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onPinOffline = viewModel::pinOffline,
        onFavorite = viewModel::setFavorite,
        onAddToPlaylist = viewModel::addToPlaylist,
        onSelectPlaylist = viewModel::selectPlaylist,
        onEnsureArtwork = viewModel::ensureArtwork,
    )
}

@Composable
private fun LibraryScreenV3(
    state: LibraryUiState,
    displaySettings: LibraryDisplaySettings,
    permissionStatus: AudioPermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSortMode: (LibrarySortMode) -> Unit,
    onGroupMode: (LibraryGroupMode) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayCollection: (List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, UUID) -> Unit,
    onSelectPlaylist: (UUID) -> Unit,
    onEnsureArtwork: (Track) -> Unit,
) {
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var availabilityIndex by rememberSaveable { mutableIntStateOf(0) }
    var showDisplaySheet by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<LibraryDetailV3?>(null) }

    val availability = AvailabilityV3.entries[availabilityIndex]
    val availableTracks = remember(state.tracks, state.availability, availability, displaySettings) {
        val filtered = state.tracks.filter { track ->
            when (availability) {
                AvailabilityV3.ALL -> true
                AvailabilityV3.OFFLINE -> state.availability[track.id] == LibraryTrackAvailability.OFFLINE
                AvailabilityV3.CLOUD -> state.availability[track.id] == LibraryTrackAvailability.CLOUD
            }
        }
        orderTracksV3(filtered, displaySettings)
    }

    val detailTracks = remember(detail, availableTracks, state.selectedPlaylistTracks) {
        when (val selected = detail) {
            is LibraryDetailV3.Artist -> availableTracks.filter { artistV3(it) == selected.name }
            is LibraryDetailV3.Album -> availableTracks.filter {
                albumV3(it) == selected.name && artistV3(it) == selected.artist
            }
            LibraryDetailV3.Favorites -> availableTracks.filter(Track::favorite)
            is LibraryDetailV3.Playlist -> orderTracksV3(state.selectedPlaylistTracks, displaySettings)
            null -> emptyList()
        }
    }

    BackHandler(enabled = detail != null || showDisplaySheet) {
        if (showDisplaySheet) showDisplaySheet = false else detail = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (detail != null) {
                IconButton(onClick = { detail = null }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to library")
                }
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                    }
                }
                Spacer(Modifier.size(12.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = detailTitleV3(detail) ?: "Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (detail == null) {
                        "${state.tracks.size} tracks · ${sortLabelV3(displaySettings.sortMode)}"
                    } else {
                        "${detailTracks.size} tracks"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
            }

            if (detailTracks.isNotEmpty()) {
                Surface(
                    modifier = Modifier.clickable { onPlayCollection(detailTracks) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Play", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else if (detail == null) {
                IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh library")
                    }
                }
            }
        }

        if (detail == null && (permissionStatus == AudioPermissionStatus.GRANTED || state.tracks.isNotEmpty())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibrarySectionV3.entries.forEachIndexed { index, section ->
                    SelectChipV3(
                        label = section.label,
                        selected = sectionIndex == index,
                        onClick = { sectionIndex = index },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AvailabilityV3.entries.forEachIndexed { index, item ->
                        SelectChipV3(
                            label = item.label,
                            selected = availabilityIndex == index,
                            onClick = { availabilityIndex = index },
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Surface(
                    modifier = Modifier.clickable { showDisplaySheet = true },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Sort, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (displaySettings.groupMode == LibraryGroupMode.NONE) {
                                sortLabelV3(displaySettings.sortMode)
                            } else {
                                "${groupLabelV3(displaySettings.groupMode)} · ${sortLabelV3(displaySettings.sortMode)}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        when {
            permissionStatus == AudioPermissionStatus.REQUIRED && state.tracks.isEmpty() -> PermissionPanelV3(
                title = "Your music, in one place",
                body = "Allow audio access so Meowzix can build your local library.",
                action = "Allow music access",
                onAction = onRequestPermission,
            )
            permissionStatus == AudioPermissionStatus.DENIED && state.tracks.isEmpty() -> PermissionPanelV3(
                title = "Music access is off",
                body = "Turn audio access back on in Android settings.",
                action = "Open settings",
                onAction = onOpenSettings,
            )
            state.isRefreshing && state.tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            detail != null -> TrackListV3(
                tracks = detailTracks,
                queueTracks = detailTracks,
                groupMode = LibraryGroupMode.NONE,
                state = state,
                onPlayTrack = onPlayTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPinOffline = onPinOffline,
                onFavorite = onFavorite,
                onAddToPlaylist = onAddToPlaylist,
                onEnsureArtwork = onEnsureArtwork,
                onGoToArtist = { track -> detail = LibraryDetailV3.Artist(artistV3(track)) },
            )
            state.tracks.isEmpty() -> PermissionPanelV3(
                title = "No music yet",
                body = "Add audio to your device or connect Telegram from Profile.",
                action = "Scan again",
                onAction = onRefresh,
            )
            else -> when (LibrarySectionV3.entries[sectionIndex]) {
                LibrarySectionV3.TRACKS -> TrackListV3(
                    tracks = availableTracks,
                    queueTracks = availableTracks,
                    groupMode = displaySettings.groupMode,
                    state = state,
                    onPlayTrack = onPlayTrack,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onPinOffline = onPinOffline,
                    onFavorite = onFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onEnsureArtwork = onEnsureArtwork,
                    onGoToArtist = { track -> detail = LibraryDetailV3.Artist(artistV3(track)) },
                )
                LibrarySectionV3.ARTISTS -> GroupListV3(
                    rows = availableTracks.groupBy(::artistV3)
                        .map { (name, tracks) -> Triple(name, "${tracks.size} tracks", LibraryDetailV3.Artist(name)) }
                        .sortedBy { it.first.lowercase() },
                    icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    onOpen = { detail = it },
                )
                LibrarySectionV3.ALBUMS -> GroupListV3(
                    rows = availableTracks.groupBy { albumV3(it) to artistV3(it) }
                        .map { (key, tracks) -> Triple(key.first, "${key.second} · ${tracks.size} tracks", LibraryDetailV3.Album(key.first, key.second)) }
                        .sortedBy { it.first.lowercase() },
                    icon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                    onOpen = { detail = it },
                )
                LibrarySectionV3.PLAYLISTS -> PlaylistListV3(
                    state = state,
                    onFavorites = { detail = LibraryDetailV3.Favorites },
                    onPlaylist = { playlist ->
                        onSelectPlaylist(playlist.id)
                        detail = LibraryDetailV3.Playlist(playlist.id, playlist.title)
                    },
                )
            }
        }
    }

    if (showDisplaySheet) {
        DisplaySheetV3(
            settings = displaySettings,
            onSortMode = onSortMode,
            onGroupMode = onGroupMode,
            onDismiss = { showDisplaySheet = false },
        )
    }
}

@Composable
private fun TrackListV3(
    tracks: List<Track>,
    queueTracks: List<Track>,
    groupMode: LibraryGroupMode,
    state: LibraryUiState,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPinOffline: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track, UUID) -> Unit,
    onEnsureArtwork: (Track) -> Unit,
    onGoToArtist: (Track) -> Unit,
) {
    val listItems = remember(tracks, groupMode) { flattenGroupedTracksV3(tracks, groupMode) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 188.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            items = listItems,
            key = { item ->
                when (item) {
                    is TrackListItemV3.Header -> "header:${item.label}"
                    is TrackListItemV3.Song -> item.track.id.toString()
                }
            },
        ) { item ->
            when (item) {
                is TrackListItemV3.Header -> Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                )
                is TrackListItemV3.Song -> {
                    val track = item.track
                    SwipeableTrackRowV2(
                        track = track,
                        isCurrent = state.playback.currentTrack?.id == track.id,
                        onClick = { onPlayTrack(track, queueTracks) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onPinOffline = { onPinOffline(track) },
                        availability = state.availability[track.id] ?: LibraryTrackAvailability.UNAVAILABLE,
                        download = state.downloads[track.id],
                        playlists = state.playlists,
                        onFavorite = { onFavorite(track) },
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(track, playlistId) },
                        onGoToArtist = { onGoToArtist(track) },
                        onEnsureArtwork = { onEnsureArtwork(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupListV3(
    rows: List<Triple<String, String, LibraryDetailV3>>,
    icon: @Composable () -> Unit,
    onOpen: (LibraryDetailV3) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 188.dp),
    ) {
        items(rows, key = { "${it.first}:${it.second}" }) { (title, subtitle, destination) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(destination) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) { Box(contentAlignment = Alignment.Center) { icon() } }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
        }
    }
}

@Composable
private fun PlaylistListV3(
    state: LibraryUiState,
    onFavorites: () -> Unit,
    onPlaylist: (dev.behradhz.meowzix.domain.library.PlaylistSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 188.dp),
    ) {
        item("favorites") {
            PlaylistRowV3("Favorites", "${state.tracks.count(Track::favorite)} tracks", Icons.Rounded.Favorite, onFavorites)
        }
        items(state.playlists, key = { it.id.toString() }) { playlist ->
            PlaylistRowV3(playlist.title, "${playlist.trackCount} tracks", Icons.Rounded.PlaylistPlay) { onPlaylist(playlist) }
        }
    }
}

@Composable
private fun PlaylistRowV3(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) } }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun DisplaySheetV3(
    settings: LibraryDisplaySettings,
    onSortMode: (LibrarySortMode) -> Unit,
    onGroupMode: (LibraryGroupMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Sort & group", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sort", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            LibrarySortMode.entries.forEach { mode ->
                OptionRowV3(sortLabelV3(mode), settings.sortMode == mode) { onSortMode(mode) }
            }
            Text("Group by", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            LibraryGroupMode.entries.forEach { mode ->
                OptionRowV3(groupLabelV3(mode), settings.groupMode == mode) { onGroupMode(mode) }
            }
        }
    }
}

@Composable
private fun OptionRowV3(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SelectChipV3(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PermissionPanelV3(title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

private fun orderTracksV3(tracks: List<Track>, settings: LibraryDisplaySettings): List<Track> {
    val sortComparator = when (settings.sortMode) {
        LibrarySortMode.RECENTLY_ADDED -> compareByDescending<Track> { it.createdAt }
        LibrarySortMode.OLDEST_ADDED -> compareBy<Track> { it.createdAt }
        LibrarySortMode.TITLE_ASC -> compareBy<Track> { it.title.lowercase() }
        LibrarySortMode.TITLE_DESC -> compareByDescending<Track> { it.title.lowercase() }
        LibrarySortMode.ARTIST_ASC -> compareBy<Track>({ artistV3(it).lowercase() }, { it.title.lowercase() })
    }
    if (settings.groupMode == LibraryGroupMode.NONE) return tracks.sortedWith(sortComparator)
    val groupComparator = compareBy<Track> { groupKeyV3(it, settings.groupMode).lowercase() }
    return tracks.sortedWith(groupComparator.then(sortComparator))
}

private fun flattenGroupedTracksV3(tracks: List<Track>, groupMode: LibraryGroupMode): List<TrackListItemV3> {
    if (groupMode == LibraryGroupMode.NONE) return tracks.map(TrackListItemV3::Song)
    return buildList {
        tracks.groupBy { groupKeyV3(it, groupMode) }.forEach { (group, groupedTracks) ->
            add(TrackListItemV3.Header(group))
            groupedTracks.forEach { add(TrackListItemV3.Song(it)) }
        }
    }
}

private fun groupKeyV3(track: Track, mode: LibraryGroupMode): String = when (mode) {
    LibraryGroupMode.NONE -> ""
    LibraryGroupMode.ARTIST -> artistV3(track)
    LibraryGroupMode.ALBUM -> albumV3(track)
    LibraryGroupMode.YEAR -> track.year?.toString() ?: "Unknown year"
}

private fun artistV3(track: Track): String = track.artist?.takeIf(String::isNotBlank) ?: "Unknown artist"
private fun albumV3(track: Track): String = track.album?.takeIf(String::isNotBlank) ?: "Unknown album"

private fun detailTitleV3(detail: LibraryDetailV3?): String? = when (detail) {
    is LibraryDetailV3.Artist -> detail.name
    is LibraryDetailV3.Album -> detail.name
    LibraryDetailV3.Favorites -> "Favorites"
    is LibraryDetailV3.Playlist -> detail.title
    null -> null
}

private fun sortLabelV3(mode: LibrarySortMode): String = when (mode) {
    LibrarySortMode.RECENTLY_ADDED -> "Recently added"
    LibrarySortMode.OLDEST_ADDED -> "Oldest added"
    LibrarySortMode.TITLE_ASC -> "Title A–Z"
    LibrarySortMode.TITLE_DESC -> "Title Z–A"
    LibrarySortMode.ARTIST_ASC -> "Artist A–Z"
}

private fun groupLabelV3(mode: LibraryGroupMode): String = when (mode) {
    LibraryGroupMode.NONE -> "None"
    LibraryGroupMode.ARTIST -> "Artist"
    LibraryGroupMode.ALBUM -> "Album"
    LibraryGroupMode.YEAR -> "Year"
}
