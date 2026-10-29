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
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.behradhz.meowzix.ui.components.MeowzixStickyHeader
import dev.behradhz.meowzix.ui.components.MeowzixHeaderAction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.behradhz.meowzix.core.common.TextNormalizer
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
import kotlinx.coroutines.delay

private enum class LibrarySectionV4(val label: String) {
    TRACKS("Tracks"), ARTISTS("Artists"), ALBUMS("Albums")
}

@Composable
internal fun LibraryRouteV4(
    onOpenNowPlaying: () -> Unit,
    viewModel: LibraryViewModel,
    requestedArtist: String? = null,
    onArtistRequestConsumed: () -> Unit = {},
    requestedAlbum: AlbumSummary? = null,
    onAlbumRequestConsumed: () -> Unit = {},
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
    LaunchedEffect(requestedArtist, artists) {
        val normalized = TextNormalizer.normalize(requestedArtist) ?: return@LaunchedEffect
        val matched = artists.firstOrNull { it.normalizedName == normalized } ?: return@LaunchedEffect
        tracksViewModel.openArtist(matched)
        onArtistRequestConsumed()
    }

    LaunchedEffect(requestedAlbum, albums) {
        val requested = requestedAlbum ?: return@LaunchedEffect
        val album = albums.firstOrNull {
            it.name == requested.name && it.normalizedArtist == requested.normalizedArtist
        } ?: return@LaunchedEffect
        tracksViewModel.openAlbum(album)
        onAlbumRequestConsumed()
    }

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
    // Only check periodically while the Library is visible; tab navigation never forces a scan.
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            viewModel.refreshIfDue()
            while (true) {
                delay(LIBRARY_AUTO_REFRESH_INTERVAL_MS)
                viewModel.refreshIfDue()
            }
        }
    }

    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(sectionIndex) {
        if (sectionIndex !in LibrarySectionV4.entries.indices) sectionIndex = 0
    }
    var availabilityIndex by rememberSaveable { mutableIntStateOf(0) }
    var showDisplaySheet by rememberSaveable { mutableStateOf(false) }
    val selectedSection = LibrarySectionV4.entries.getOrElse(sectionIndex) { LibrarySectionV4.TRACKS }

    val availabilityFilter = LibraryAvailabilityFilter.entries[availabilityIndex]
    LaunchedEffect(availabilityFilter) { tracksViewModel.setAvailability(availabilityFilter) }

    BackHandler(enabled = aggregateSelection != null || showDisplaySheet) {
        when {
            showDisplaySheet -> showDisplaySheet = false
            else -> tracksViewModel.closeAggregate()
        }
    }

    val permissionStatus = audioPermissionStatus(permissionGranted, permissionRequestAttempted)
    if (aggregateSelection == LibraryAggregateSelection.Favorites) {
        key("favorites") {
            PlaylistDetailScreenV3(
                playlist = null,
                title = "Favorites",
                tracks = aggregateTracks,
                artworkRef = aggregateTracks.firstOrNull()?.artworkRef,
                editable = false,
                isFavorites = true,
                currentTrackId = state.playback.currentTrack?.id,
                availability = state.availability,
                downloads = state.downloads,
                playlists = state.playlists,
                onBack = tracksViewModel::closeAggregate,
                onSaveMetadata = { _, _, _ -> },
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
                    tracksViewModel.closeAggregate()
                    artists.firstOrNull { it.normalizedName == track.normalizedArtist }
                        ?.let(tracksViewModel::openArtist)
                },
                onMove = viewModel::movePlaylistTrack,
                onRemove = viewModel::removePlaylistTrack,
                onEnsureArtwork = viewModel::ensureArtwork,
                onDelete = {},
                deleting = false,
                errorMessage = state.errorMessage,
            )
        }
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

    val headerHazeState = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 96.dp),
        ) {
    Column(
        modifier = Modifier.fillMaxSize().hazeSource(headerHazeState),
    ) {
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

        if (selectedSection == LibrarySectionV4.TRACKS) {
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
            else -> when (selectedSection) {
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
            }
        }
    }
        }
        MeowzixStickyHeader(
            title = "Library",
            subtitle = "$trackCount tracks · ${sortLabelV4(displaySettings.sortMode)}",
            hazeState = headerHazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
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
    val listState = rememberLazyListState()
    // Compose can prefetch off-screen rows: bitmap decode/repair waits for actual viewport entry.
    val visibleTrackKeys by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet() }
    }
    val refresh = items.loadState.refresh
    val hasPresentedItems = items.itemCount > 0

    when {
        refresh is LoadState.Error && !hasPresentedItems -> PermissionPanelV4(
            "Unable to load library",
            refresh.error.message ?: "The library query failed.",
            "Retry",
            items::retry,
        )
        refresh is LoadState.Loading && !hasPresentedItems -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 188.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        artworkVisible = row.track.id.toString() in visibleTrackKeys,
                        containerColor = Color(0x4D252329),
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(rows, key = { "${it.name}:${it.normalizedArtist}" }, contentType = { "album" }) { row ->
            SummaryRowV4(row.name, "${row.artist} · ${row.trackCount} tracks", Icons.Rounded.Album) { onOpen(row) }
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
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val visibleTrackKeys by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet() }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding().hazeSource(hazeState),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 100.dp, bottom = 188.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    artworkVisible = track.id.toString() in visibleTrackKeys,
                    containerColor = Color(0x4D252329),
                )
            }
        }
        MeowzixStickyHeader(
            title = title,
            subtitle = "${tracks.size} tracks",
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            MeowzixHeaderAction("Back to library", Icons.Rounded.ArrowBack, onBack)
            if (tracks.isNotEmpty()) {
                MeowzixHeaderAction("Play", Icons.Rounded.PlayArrow, {
                    viewModel.playCollection(tracks, PlaybackMode.ORDERED)
                })
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0x4D252329),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.70f))
        }
        }
    }
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
