package dev.behradhz.meowzix.feature.library

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
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
import dev.behradhz.meowzix.ui.components.TrackArtwork

private enum class LibrarySection(val label: String) {
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
}

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
    onOpenTelegram: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedSection = LibrarySection.entries[selectedSectionIndex]
    val filteredTracks = remember(state.tracks, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            state.tracks
        } else {
            state.tracks.filter { track ->
                track.title.contains(normalizedQuery, ignoreCase = true) ||
                    track.artist?.contains(normalizedQuery, ignoreCase = true) == true ||
                    track.album?.contains(normalizedQuery, ignoreCase = true) == true
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LibraryHeader(
                trackCount = state.tracks.size,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                onOpenTelegram = onOpenTelegram,
            )

            if (permissionStatus == AudioPermissionStatus.GRANTED) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    placeholder = { Text("Search tracks, artists, albums") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                )

                TabRow(
                    selectedTabIndex = selectedSectionIndex,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    LibrarySection.entries.forEachIndexed { index, section ->
                        Tab(
                            selected = index == selectedSectionIndex,
                            onClick = { selectedSectionIndex = index },
                            text = { Text(section.label) },
                            icon = {
                                Icon(
                                    imageVector = when (section) {
                                        LibrarySection.TRACKS -> Icons.Rounded.MusicNote
                                        LibrarySection.ARTISTS -> Icons.Rounded.Person
                                        LibrarySection.ALBUMS -> Icons.Rounded.Album
                                    },
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }

            if (state.isRefreshing && state.tracks.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                permissionStatus == AudioPermissionStatus.REQUIRED -> LibraryMessageCard(
                    title = "Your music, in one place",
                    message = "Allow audio access so Meowzix can build the local side of your library.",
                    action = "Allow music access",
                    onAction = onRequestPermission,
                )

                permissionStatus == AudioPermissionStatus.DENIED -> LibraryMessageCard(
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

                state.errorMessage != null && state.tracks.isEmpty() -> LibraryMessageCard(
                    title = "Couldn't scan your music",
                    message = state.errorMessage,
                    action = "Try again",
                    onAction = onRefresh,
                )

                state.tracks.isEmpty() -> LibraryMessageCard(
                    title = "No local music yet",
                    message = "Add audio to your device or connect Telegram. Meowzix keeps both sources in one library.",
                    action = "Scan again",
                    onAction = onRefresh,
                )

                filteredTracks.isEmpty() -> LibraryMessageCard(
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
                    )

                    LibrarySection.ARTISTS -> ArtistsSection(filteredTracks)
                    LibrarySection.ALBUMS -> AlbumsSection(filteredTracks)
                }
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Meowzix", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = if (trackCount == 1) "1 track" else "$trackCount tracks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan local music")
        }
        IconButton(onClick = onOpenTelegram) {
            Icon(Icons.Rounded.Cloud, contentDescription = "Open Telegram connection")
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = { it.id.toString() }) { track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                onClick = { onPlayTrack(track) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isCurrent) 3.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track.artworkRef, track.title, size = 58.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = "Currently playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp),
                        )
                    }
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    track.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(artists, key = { it.first }) { (artist, count) ->
            LibraryGroupCard(
                title = artist,
                subtitle = if (count == 1) "1 track" else "$count tracks",
                icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(albums, key = { it.first }) { (album, artist, count) ->
            LibraryGroupCard(
                title = album,
                subtitle = "$artist · ${if (count == 1) "1 track" else "$count tracks"}",
                icon = { Icon(Icons.Rounded.Album, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun LibraryGroupCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibraryMessageCard(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
