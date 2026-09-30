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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.core.permissions.AudioPermission
import dev.behradhz.meowzix.core.permissions.AudioPermissionStatus
import dev.behradhz.meowzix.core.permissions.audioPermissionStatus
import dev.behradhz.meowzix.data.repository.AlbumSummary
import dev.behradhz.meowzix.data.repository.ArtistSummary
import dev.behradhz.meowzix.data.repository.LibraryAvailabilityFilter
import dev.behradhz.meowzix.domain.library.LibraryTrack
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.settings.LibraryDisplaySettings
import dev.behradhz.meowzix.domain.settings.LibraryGroupMode
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
import java.util.UUID

private enum class LibrarySectionV4(val label: String) {
    TRACKS("Tracks"), ARTISTS("Artists"), ALBUMS("Albums"), PLAYLISTS("Playlists")
}

@Composable
internal fun LibraryRouteV4(
    onOpenNowPlaying: () -> Unit,
    viewModel: LibraryViewModel,
    tracksViewModel: LibraryTracksViewModel = hiltViewModel(),
    displayViewModel: LibraryDisplayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val displaySettings by tracksViewModel.displaySettings.collectAsStateWithLifecycle()
    val trackCount by tracksViewModel.trackCount.collectAsStateWithLifecycle()
    val artists by tracksViewModel.artists.collectAsStateWithLifecycle()
    val albums by tracksViewModel.albums.collectAsStateWithLifecycle()
    val aggregateSelection by tracksViewModel.selectedAggregate.collectAsStateWithLifecycle()
    val aggregateTracks by tracksViewModel.aggregateTracks.collectAsStateWithLifecycle()
    val pagingItems = tracksViewModel.tracks.collectAsLazyPagingItems()

    val permission = AudioPermission.requiredPermission()
    fun hasPermission() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
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
                val granted = hasPermission()
                if (permissionGranted && !granted) permissionRequestAttempted = true
                permissionGranted = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.refresh()
    }

    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var availabilityIndex by rememberSaveable { mutableIntStateOf(0) }
    var showDisplaySheet by rememberSaveable { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }

    val availabilityFilter = LibraryAvailabilityFilter.entries[availabilityIndex]
    LaunchedEffect(availabilityFilter) { tracksViewModel.setAvailability(availabilityFilter) }

    BackHandler(enabled = aggregateSelection != null || selectedPlaylist != null || showDisplaySheet) {
        when {
            showDisplaySheet -> showDisplaySheet = false
            selectedPlaylist != null -> selectedPlaylist = null
            else -> tracksViewModel.closeAggregate()
        }
    }

    val permissionStatus = audioPermissionStatus(permissionGranted, permissionRequestAttempted)
    if (selectedPlaylist != null) {
        LibraryDetailV4(
            title = selectedPlaylist?.title ?: "Playlist",
            tracks = state.selectedPlaylistTracks,
            state = state,
            onBack = { selectedPlaylist = null },
            onPlay = { track ->
                viewModel.playTrack(track, state.selectedPlaylistTracks)
                onOpenNowPlaying()
            },
            viewModel = viewModel,
            onGoToArtist = { track ->
                selectedPlaylist = null
                artists.firstOrNull { it.normalizedName == track.normalizedArtist }
                    ?.let(tracksViewModel::openArtist)
            },
        )
        return
    }
    if (aggregateSelection != null) {
        val title = when (val selected = aggregateSelection) {
            is LibraryAggregateSelection.Artist -> selected.name
            is LibraryAggregateSelection.Album -> selected.name
            LibraryAggregateSelection.Favorites -> "Favorites"
            null -> "Library"
        }
        LibraryDetailV4(
            title = title,
            tracks = aggregateTracks,
            state = state,
            onBack = tracksViewModel::closeAggregate,
            onPlay = { track ->
                viewModel.playTrack(track, aggregateTracks)
                onOpenNowPlaying()
            },
            viewModel = viewModel,
            onGoToArtist = { track ->
                artists.firstOrNull { it.normalizedName == track.normalizedArtist }
                    ?.let(tracksViewModel::openArtist)
            },
        )
        return
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
            Column(Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "$trackCount tracks · ${sortLabelV4(displaySettings.sortMode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
            }
            IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh library")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibrarySectionV4.entries.forEachIndexed { index, section ->
                SelectChipV4(section.label, sectionIndex == index) { sectionIndex = index }
            }
        }

        if (LibrarySectionV4.entries[sectionIndex] == LibrarySectionV4.TRACKS) {
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
                    LibraryAvailabilityFilter.entries.forEachIndexed { index, filter ->
                        SelectChipV4(
                            label = filter.name.lowercase().replaceFirstChar(Char::uppercase),
                            selected = availabilityIndex == index,
                        ) { availabilityIndex = index }
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
                        Text(sortLabelV4(displaySettings.sortMode), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        when {
            permissionStatus == AudioPermissionStatus.REQUIRED && trackCount == 0 -> PermissionPanelV4(
                "Your music, in one place",
                "Allow audio access so Meowzix can build your local library.",
                "Allow music access",
            ) { permissionLauncher.launch(permission) }
            permissionStatus == AudioPermissionStatus.DENIED && trackCount == 0 -> PermissionPanelV4(
                "Music access is off",
                "Turn audio access back on in Android settings.",
                "Open settings",
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            }
            else -> when (LibrarySectionV4.entries[sectionIndex]) {
                LibrarySectionV4.TRACKS -> PagedTrackListV4(
                    items = pagingItems,
                    groupMode = displaySettings.groupMode,
                    state = state,
                    onPlay = { row ->
                        tracksViewModel.playTrack(row.track.id)
                        onOpenNowPlaying()
                    },
                    viewModel = viewModel,
                    onGoToArtist = { track ->
                        artists.firstOrNull { it.normalizedName == track.normalizedArtist }
                            ?.let(tracksViewModel::openArtist)
                    },
                )
                LibrarySectionV4.ARTISTS -> ArtistListV4(artists, tracksViewModel::openArtist)
                LibrarySectionV4.ALBUMS -> AlbumListV4(albums, tracksViewModel::openAlbum)
                LibrarySectionV4.PLAYLISTS -> PlaylistListV4(
                    playlists = state.playlists,
                    favoriteCount = state.tracks.count(Track::favorite),
                    onFavorites = tracksViewModel::openFavorites,
                    onPlaylist = { playlist ->
                        viewModel.selectPlaylist(playlist.id)
                        selectedPlaylist = playlist
                    },
                )
            }
        }
    }

    if (showDisplaySheet) {
        DisplaySheetV4(
            settings = displaySettings,
            onSort = displayViewModel::setSortMode,
            onGroup = displayViewModel::setGroupMode,
            onDismiss = { showDisplaySheet = false },
        )
    }
}

@Composable
private fun PagedTrackListV4(
    items: androidx.paging.compose.LazyPagingItems<LibraryTrack>,
    groupMode: LibraryGroupMode,
    state: LibraryUiState,
    onPlay: (LibraryTrack) -> Unit,
    viewModel: LibraryViewModel,
    onGoToArtist: (Track) -> Unit,
) {
    when (val refresh = items.loadState.refresh) {
        is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is LoadState.Error -> PermissionPanelV4(
            "Unable to load library",
            refresh.error.message ?: "The library query failed.",
            "Retry",
            items::retry,
        )
        is LoadState.NotLoading -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 188.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.track.id.toString() },
                contentType = items.itemContentType { "track" },
            ) { index ->
                val row = items[index] ?: return@items
                val group = groupLabelV4(row.track, groupMode)
                val previousGroup = if (index > 0) {
                    items.peek(index - 1)?.track?.let { groupLabelV4(it, groupMode) }
                } else null
                Column {
                    if (group != null && group != previousGroup) {
                        Text(
                            group,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    SwipeableLibraryTrackRow(
                        track = row.track,
                        isCurrent = state.playback.currentTrack?.id == row.track.id,
                        onClick = { onPlay(row) },
                        onPlayNext = { viewModel.playNext(row.track) },
                        onAddToQueue = { viewModel.addToQueue(row.track) },
                        onPinOffline = { viewModel.pinOffline(row.track) },
                        availability = row.availability,
                        download = state.downloads[row.track.id],
                        playlists = state.playlists,
                        onFavorite = { viewModel.setFavorite(row.track) },
                        onAddToPlaylist = { playlistId -> viewModel.addToPlaylist(row.track, playlistId) },
                        onGoToArtist = { onGoToArtist(row.track) },
                        onEnsureArtwork = { viewModel.ensureArtwork(row.track) },
                    )
                }
            }
            if (items.loadState.append is LoadState.Loading) {
                item(key = "page-loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistListV4(rows: List<ArtistSummary>, onOpen: (ArtistSummary) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 188.dp),
    ) {
        items(rows, key = { it.normalizedName }, contentType = { "artist" }) { row ->
            SummaryRowV4(row.name, "${row.trackCount} tracks", Icons.Rounded.Person) { onOpen(row) }
        }
    }
}

@Composable
private fun AlbumListV4(rows: List<AlbumSummary>, onOpen: (AlbumSummary) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 188.dp),
    ) {
        items(rows, key = { "${it.name}:${it.normalizedArtist}" }, contentType = { "album" }) { row ->
            SummaryRowV4(row.name, "${row.artist} · ${row.trackCount} tracks", Icons.Rounded.Album) { onOpen(row) }
        }
    }
}

@Composable
private fun PlaylistListV4(
    playlists: List<PlaylistSummary>,
    favoriteCount: Int,
    onFavorites: () -> Unit,
    onPlaylist: (PlaylistSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 188.dp),
    ) {
        item(key = "favorites", contentType = "playlist") {
            SummaryRowV4("Favorites", "$favoriteCount tracks", Icons.Rounded.Favorite, onFavorites)
        }
        items(playlists, key = { it.id.toString() }, contentType = { "playlist" }) { playlist ->
            SummaryRowV4(playlist.title, "${playlist.trackCount} tracks", Icons.Rounded.PlaylistPlay) {
                onPlaylist(playlist)
            }
        }
    }
}

@Composable
private fun LibraryDetailV4(
    title: String,
    tracks: List<Track>,
    state: LibraryUiState,
    onBack: () -> Unit,
    onPlay: (Track) -> Unit,
    viewModel: LibraryViewModel,
    onGoToArtist: (Track) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${tracks.size} tracks", style = MaterialTheme.typography.bodySmall)
            }
            if (tracks.isNotEmpty()) {
                Surface(
                    modifier = Modifier.clickable {
                        viewModel.playCollection(tracks, PlaybackMode.ORDERED)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Text("Play", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 188.dp),
        ) {
            items(tracks, key = { it.id.toString() }, contentType = { "track" }) { track ->
                SwipeableLibraryTrackRow(
                    track = track,
                    isCurrent = state.playback.currentTrack?.id == track.id,
                    onClick = { onPlay(track) },
                    onPlayNext = { viewModel.playNext(track) },
                    onAddToQueue = { viewModel.addToQueue(track) },
                    onPinOffline = { viewModel.pinOffline(track) },
                    availability = state.availability[track.id] ?: LibraryTrackAvailability.UNAVAILABLE,
                    download = state.downloads[track.id],
                    playlists = state.playlists,
                    onFavorite = { viewModel.setFavorite(track) },
                    onAddToPlaylist = { id -> viewModel.addToPlaylist(track, id) },
                    onGoToArtist = { onGoToArtist(track) },
                    onEnsureArtwork = { viewModel.ensureArtwork(track) },
                )
            }
        }
    }
}

@Composable
private fun SummaryRowV4(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.primary,
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) } }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
}

@Composable
private fun SelectChipV4(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PermissionPanelV4(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(body, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun DisplaySheetV4(
    settings: LibraryDisplaySettings,
    onSort: (LibrarySortMode) -> Unit,
    onGroup: (LibraryGroupMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Sort & group", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sort", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            LibrarySortMode.entries.forEach { mode ->
                OptionRowV4(sortLabelV4(mode), settings.sortMode == mode) { onSort(mode) }
            }
            Text("Group by", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            LibraryGroupMode.entries.forEach { mode ->
                OptionRowV4(groupLabelV4(mode), settings.groupMode == mode) { onGroup(mode) }
            }
        }
    }
}

@Composable
private fun OptionRowV4(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun groupLabelV4(track: Track, mode: LibraryGroupMode): String? = when (mode) {
    LibraryGroupMode.NONE -> null
    LibraryGroupMode.ARTIST -> track.artist?.takeIf(String::isNotBlank) ?: "Unknown artist"
    LibraryGroupMode.ALBUM -> track.album?.takeIf(String::isNotBlank) ?: "Unknown album"
    LibraryGroupMode.YEAR -> track.year?.toString() ?: "Unknown year"
}

private fun sortLabelV4(mode: LibrarySortMode): String = when (mode) {
    LibrarySortMode.RECENTLY_ADDED -> "Recently added"
    LibrarySortMode.OLDEST_ADDED -> "Oldest added"
    LibrarySortMode.TITLE_ASC -> "Title A–Z"
    LibrarySortMode.TITLE_DESC -> "Title Z–A"
    LibrarySortMode.ARTIST_ASC -> "Artist A–Z"
}

private fun groupLabelV4(mode: LibraryGroupMode): String = when (mode) {
    LibraryGroupMode.NONE -> "None"
    LibraryGroupMode.ARTIST -> "Artist"
    LibraryGroupMode.ALBUM -> "Album"
    LibraryGroupMode.YEAR -> "Year"
}
