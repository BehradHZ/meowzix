package dev.behradhz.meowzix.feature.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.chrisbanes.haze.HazeState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlaylistTrackSort(val label: String) {
    CUSTOM("Custom"),
    NAME("Name"),
    MODIFIED("Modified"),
}

private enum class PlaylistGroupBy(val label: String) {
    NONE("None"),
    ARTIST("Artist"),
    ALBUM("Album"),
    YEAR("Year"),
}

@Composable
internal fun PlaylistsSectionV3(
    playlists: List<PlaylistSummary>,
    playlistArtwork: Map<UUID, String?>,
    favoriteTracks: List<Track>,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (UUID) -> Unit,
    onEnsureArtwork: (Track) -> Unit,
    onCreate: (() -> Unit)? = null,
    onSaveQueue: (() -> Unit)? = null,
) {
    favoriteTracks.firstOrNull()?.let { track ->
        LaunchedEffect(track.id, track.artworkRef) { onEnsureArtwork(track) }
    }

    var confirmSaveQueue by rememberSaveable { mutableStateOf(false) }
    if (confirmSaveQueue && onSaveQueue != null) {
        AlertDialog(
            onDismissRequest = { confirmSaveQueue = false },
            title = { Text("Save current queue?") },
            text = { Text("Create a new playlist from the tracks currently in your queue? Your queue and music files will stay unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSaveQueue = false
                        onSaveQueue()
                    },
                ) { Text("Save as playlist") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSaveQueue = false }) { Text("Cancel") }
            },
        )
    }

    PlaylistGlassBackdrop { hazeState ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.fillMaxSize().testTag("playlist-grid"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 188.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "playlist-heading", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Your collections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "A place for every mood.",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onCreate != null || onSaveQueue != null) {
                        Row(
                            modifier = Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            onCreate?.let { PlaylistGlassAction(hazeState, "New playlist", Icons.Rounded.Add, it, accented = true) }
                            onSaveQueue?.let { PlaylistGlassAction(hazeState, "Save queue", Icons.Rounded.QueueMusic, { confirmSaveQueue = true }) }
                        }
                    }
                }
            }
            item(key = "favorites-v3", span = { GridItemSpan(maxLineSpan) }) {
                PlaylistMorphCardV3(
                    hazeState = hazeState,
                    title = "Favorites",
                    artworkRef = favoriteTracks.firstOrNull()?.artworkRef,
                    favorite = true,
                    trackCount = favoriteTracks.size,
                    onClick = onOpenFavorites,
                )
            }
            item(key = "playlist-count", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Playlists", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${playlists.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(playlists, key = { "playlist-v3:${it.id}" }, contentType = { "playlist" }) { playlist ->
                PlaylistMorphCardV3(
                    hazeState = hazeState,
                    title = playlist.title,
                    artworkRef = playlist.artworkRef ?: playlistArtwork[playlist.id],
                    favorite = false,
                    trackCount = playlist.trackCount,
                    telegram = playlistArtwork.containsKey(playlist.id),
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
            if (playlists.isEmpty()) {
                item(key = "no-playlists", span = { GridItemSpan(maxLineSpan) }) {
                    PlaylistGlassPanel(hazeState, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp)) {
                            Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Text("Make it yours", modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Create a playlist or save your current queue to start a collection.",
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistMorphCardV3(
    hazeState: HazeState,
    title: String,
    artworkRef: String?,
    favorite: Boolean,
    trackCount: Int,
    telegram: Boolean = false,
    onClick: () -> Unit,
) {
    var opening by remember { mutableStateOf(false) }
    val artworkSize by animateDpAsState(if (opening) 164.dp else 92.dp, label = "playlist-card-artwork-morph")
    LaunchedEffect(opening) {
        if (opening) {
            delay(260)
            onClick()
            opening = false
        }
    }
    PlaylistGlassPanel(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(enabled = !opening, role = Role.Button) { opening = true },
        accented = favorite,
    ) {
        if (favorite && !opening) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaylistArtworkV3(artworkRef, title, true, artworkSize)
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    Text("THE ONES YOU LOVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(title, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(playlistTrackCount(trackCount), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val coverSize = minOf(maxWidth, if (opening) 180.dp else 148.dp)
                    PlaylistArtworkV3(artworkRef, title, favorite, coverSize)
                }
                Text(title, modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (telegram) {
                        Icon(Icons.Rounded.Send, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(5.dp))
                    }
                    Text(
                        if (telegram) "Telegram · ${playlistTrackCount(trackCount)}" else playlistTrackCount(trackCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

private fun playlistTrackCount(count: Int): String = if (count == 1) "1 track" else "$count tracks"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistDetailScreenV3(
    playlist: PlaylistSummary?,
    title: String,
    tracks: List<Track>,
    artworkRef: String?,
    editable: Boolean,
    isFavorites: Boolean,
    currentTrackId: UUID?,
    availability: Map<UUID, LibraryTrackAvailability>,
    downloads: Map<UUID, OfflineDownload>,
    playlists: List<PlaylistSummary>,
    onBack: () -> Unit,
    onSaveMetadata: (String, String?, String?) -> Unit,
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
    onDelete: () -> Unit,
    deleting: Boolean = false,
    errorMessage: String? = null,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }
    if (editing && editable) {
        PlaylistEditScreenV3(
            initialTitle = title,
            initialDescription = playlist?.description,
            initialArtworkRef = playlist?.artworkRef,
            onBack = { editing = false },
            onSave = { newTitle, description, selectedArtwork ->
                onSaveMetadata(newTitle, description, selectedArtwork)
                editing = false
            },
        )
        return
    }

    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    if (showDeleteConfirmation && editable) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirmation = false },
            title = { Text("Delete playlist?") },
            text = { Text("Delete \"$title\"? This removes the playlist, not its songs or downloads.") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) { Text("Delete playlist", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { showDeleteConfirmation = false },
                ) { Text("Cancel") }
            },
        )
    }

    var sort by rememberSaveable { mutableStateOf(PlaylistTrackSort.CUSTOM) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var groupBy by rememberSaveable { mutableStateOf(PlaylistGroupBy.NONE) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var customTracks by remember { mutableStateOf(tracks) }
    var draggedTrackId by remember { mutableStateOf<UUID?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }

    LaunchedEffect(tracks, draggedTrackId) {
        if (draggedTrackId == null) customTracks = tracks
    }

    val sortedTracks = remember(tracks, sort, ascending, customTracks) {
        when (sort) {
            PlaylistTrackSort.CUSTOM -> customTracks
            PlaylistTrackSort.NAME -> tracks.sortedBy { it.title.lowercase() }.let { if (ascending) it else it.reversed() }
            PlaylistTrackSort.MODIFIED -> tracks.sortedBy(Track::updatedAt).let { if (ascending) it else it.reversed() }
        }
    }
    val grouped = remember(sortedTracks, groupBy) {
        when (groupBy) {
            PlaylistGroupBy.NONE -> listOf<String?>(null).map { it to sortedTracks }
            PlaylistGroupBy.ARTIST -> sortedTracks.groupBy { it.artist?.takeIf(String::isNotBlank) ?: "Unknown artist" }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER).toList()
            PlaylistGroupBy.ALBUM -> sortedTracks.groupBy { it.album?.takeIf(String::isNotBlank) ?: "Unknown album" }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER).toList()
            PlaylistGroupBy.YEAR -> sortedTracks.groupBy { it.year?.toString() ?: "Unknown year" }
                .toSortedMap(compareByDescending<String> { it.toIntOrNull() ?: Int.MIN_VALUE }).toList()
        }
    }

    fun finishDrag(commit: Boolean) {
        val id = draggedTrackId
        if (commit && id != null) {
            val from = tracks.indexOfFirst { it.id == id }
            val to = customTracks.indexOfFirst { it.id == id }
            if (from >= 0 && to >= 0 && from != to) onMove(from, to)
        } else if (!commit) {
            customTracks = tracks
        }
        draggedTrackId = null
        draggedDistance = 0f
    }

    PlaylistGlassBackdrop { hazeState ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("playlist-detail-list"),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 188.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "playlist-navigation") {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlaylistGlassPanel(hazeState, radius = 18.dp) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to playlists")
                        }
                    }
                    Text(
                        if (isFavorites) "Favorites" else if (editable) "Your playlist" else "Telegram playlist",
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (editable) {
                        PlaylistGlassPanel(hazeState, radius = 18.dp) {
                            IconButton(enabled = !deleting, onClick = { showDeleteConfirmation = true }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete playlist")
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        PlaylistGlassPanel(hazeState, radius = 18.dp) {
                            IconButton(enabled = !deleting, onClick = { editing = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit playlist")
                            }
                        }
                    }
                }
            }
            item(key = "playlist-hero") {
                PlaylistGlassPanel(hazeState, Modifier.fillMaxWidth(), radius = 32.dp, accented = true) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlaylistArtworkV3(artworkRef, title, isFavorites, 174.dp)
                        Text(
                            title,
                            modifier = Modifier.padding(top = 22.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        playlist?.description?.takeIf(String::isNotBlank)?.let { description ->
                            Text(
                                description,
                                modifier = Modifier.padding(top = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        val durationMinutes = tracks.sumOf { it.durationMs.coerceAtLeast(0L) } / 60_000L
                        Text(
                            "${playlistTrackCount(tracks.size)} · $durationMinutes min",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        playlist?.updatedAt?.takeUnless { it == java.time.Instant.EPOCH }?.let { updated ->
                            Text(
                                "Updated ${DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault()).format(updated)}",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { onPlay(sortedTracks, PlaybackMode.ORDERED) },
                            enabled = sortedTracks.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().padding(top = 22.dp).heightIn(min = 52.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Play", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PlaylistGlassAction(
                                hazeState, "Shuffle", Icons.Rounded.Shuffle,
                                { onPlay(sortedTracks, PlaybackMode.PURE_SHUFFLE) },
                                modifier = Modifier.weight(1f), enabled = sortedTracks.isNotEmpty(),
                            )
                            PlaylistGlassAction(
                                hazeState, "Smart", Icons.Rounded.AutoAwesome,
                                { onPlay(sortedTracks, PlaybackMode.SMART_SHUFFLE) },
                                modifier = Modifier.weight(1f), enabled = sortedTracks.isNotEmpty(),
                            )
                        }
                    }
                }
            }

            item(key = "playlist-controls") {
                PlaylistOrganizationControlsV3(
                    hazeState = hazeState,
                    sort = sort,
                    ascending = ascending,
                    groupBy = groupBy,
                    onSort = { selected ->
                        sort = selected
                        if (selected == PlaylistTrackSort.CUSTOM) groupBy = PlaylistGroupBy.NONE
                    },
                    onToggleDirection = { ascending = !ascending },
                    onGroupBy = { selected ->
                        groupBy = selected
                        if (selected != PlaylistGroupBy.NONE && sort == PlaylistTrackSort.CUSTOM) {
                            sort = PlaylistTrackSort.NAME
                        }
                    },
                )
            }

            errorMessage?.let { message ->
                item(key = "playlist-error") {
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (editable && sort == PlaylistTrackSort.CUSTOM && tracks.isNotEmpty()) {
                item(key = "reorder-hint") {
                    Text("Hold the handle to reorder", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            if (sortedTracks.isEmpty()) {
                item(key = "playlist-empty") {
                    PlaylistGlassPanel(hazeState, Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (isFavorites) Icons.Rounded.Favorite else Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Text(if (isFavorites) "Your favorites start here" else "Ready for your first track", modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Text(
                                if (isFavorites) "Favorite a track and it will appear here." else "Use a track's menu to add music to this playlist.",
                                modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else if (sort == PlaylistTrackSort.CUSTOM && groupBy == PlaylistGroupBy.NONE) {
                itemsIndexed(customTracks, key = { _, track -> "track:${track.id}" }) { index, track ->
                    val isDragging = draggedTrackId == track.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) draggedDistance else 0f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistGlassPanel(hazeState, Modifier.weight(1f).testTag("playlist-track:${track.id}"), radius = 20.dp) {
                            SwipeableLibraryTrackRow(
                                track = track,
                                isCurrent = track.id == currentTrackId,
                                onClick = { onPlayTrack(track, customTracks) },
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
                                onRemoveFromPlaylist = if (editable) ({ onRemove(track) }) else null,
                                onMoveUp = if (editable && index > 0) ({ onMove(index, index - 1) }) else null,
                                onMoveDown = if (editable && index < customTracks.lastIndex) ({ onMove(index, index + 1) }) else null,
                                containerColor = Color.Transparent,
                            )
                        }
                        if (editable) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("playlist-drag:${track.id}")
                                    .pointerInput(track.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedTrackId = track.id
                                                draggedDistance = 0f
                                            },
                                            onDragCancel = { finishDrag(false) },
                                            onDragEnd = { finishDrag(true) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val draggedId = draggedTrackId ?: return@detectDragGesturesAfterLongPress
                                                val currentIndex = customTracks.indexOfFirst { it.id == draggedId }
                                                if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                                draggedDistance += dragAmount.y
                                                val currentInfo = listState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == "track:$draggedId" }
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val draggedCenter = currentInfo.offset + currentInfo.size / 2f + draggedDistance
                                                val targetInfo = listState.layoutInfo.visibleItemsInfo
                                                    .asSequence()
                                                    .filter { it.key is String && (it.key as String).startsWith("track:") && it.key != "track:$draggedId" }
                                                    .minByOrNull { info -> abs((info.offset + info.size / 2f) - draggedCenter) }
                                                val targetId = (targetInfo?.key as? String)?.removePrefix("track:")
                                                val targetIndex = targetId?.let { raw ->
                                                    runCatching { UUID.fromString(raw) }.getOrNull()
                                                }?.let { id -> customTracks.indexOfFirst { it.id == id } } ?: -1
                                                if (targetInfo != null && targetIndex in customTracks.indices && targetIndex != currentIndex) {
                                                    val targetCenter = targetInfo.offset + targetInfo.size / 2f
                                                    val crossed = if (targetIndex > currentIndex) draggedCenter >= targetCenter else draggedCenter <= targetCenter
                                                    if (crossed) {
                                                        val oldOffset = currentInfo.offset
                                                        val reordered = customTracks.toMutableList()
                                                        val moved = reordered.removeAt(currentIndex)
                                                        reordered.add(targetIndex, moved)
                                                        customTracks = reordered
                                                        draggedDistance += oldOffset - targetInfo.offset
                                                    }
                                                }
                                                val layout = listState.layoutInfo
                                                val top = currentInfo.offset + draggedDistance
                                                val bottom = top + currentInfo.size
                                                val overscroll = when {
                                                    dragAmount.y > 0f && bottom > layout.viewportEndOffset ->
                                                        (bottom - layout.viewportEndOffset).coerceAtMost(currentInfo.size.toFloat())
                                                    dragAmount.y < 0f && top < layout.viewportStartOffset ->
                                                        (top - layout.viewportStartOffset).coerceAtLeast(-currentInfo.size.toFloat())
                                                    else -> 0f
                                                }
                                                if (overscroll != 0f) {
                                                    scope.launch {
                                                        val consumed = listState.scrollBy(overscroll)
                                                        draggedDistance += consumed
                                                    }
                                                }
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.DragHandle, contentDescription = "Drag to reorder")
                            }
                        }
                    }
                }
            } else {
                grouped.forEach { (groupTitle, groupTracks) ->
                    if (groupTitle != null) {
                        item(key = "group:$groupTitle") {
                            Text(
                                groupTitle,
                                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    items(groupTracks, key = { "sorted:${it.id}" }) { track ->
                        PlaylistGlassPanel(hazeState, Modifier.fillMaxWidth().testTag("playlist-track:${track.id}"), radius = 20.dp) {
                            SwipeableLibraryTrackRow(
                                track = track,
                                isCurrent = track.id == currentTrackId,
                                onClick = { onPlayTrack(track, sortedTracks) },
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
                                onRemoveFromPlaylist = if (editable) ({ onRemove(track) }) else null,
                                containerColor = Color.Transparent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistOrganizationControlsV3(
    hazeState: HazeState,
    sort: PlaylistTrackSort,
    ascending: Boolean,
    groupBy: PlaylistGroupBy,
    onSort: (PlaylistTrackSort) -> Unit,
    onToggleDirection: () -> Unit,
    onGroupBy: (PlaylistGroupBy) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            PlaylistGlassAction(hazeState, "Sort · ${sort.label}", Icons.Rounded.Tune, { sortMenu = true })
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                PlaylistTrackSort.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { sortMenu = false; onSort(option) })
                }
            }
        }
        if (sort != PlaylistTrackSort.CUSTOM) {
            PlaylistGlassPanel(hazeState, radius = 18.dp) {
                IconButton(onClick = onToggleDirection) {
                    Icon(
                        if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = if (ascending) "Ascending order" else "Descending order",
                    )
                }
            }
        }
        Box {
            PlaylistGlassAction(hazeState, "Group by · ${groupBy.label}", Icons.Rounded.KeyboardArrowDown, { groupMenu = true })
            DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                PlaylistGroupBy.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = {
                            when (option) {
                                PlaylistGroupBy.ARTIST -> Icon(Icons.Rounded.Person, null)
                                PlaylistGroupBy.ALBUM -> Icon(Icons.Rounded.Album, null)
                                else -> Unit
                            }
                        },
                        onClick = { groupMenu = false; onGroupBy(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistEditScreenV3(
    initialTitle: String,
    initialDescription: String?,
    initialArtworkRef: String?,
    onBack: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var description by rememberSaveable(initialDescription) { mutableStateOf(initialDescription.orEmpty()) }
    var artworkRef by rememberSaveable(initialArtworkRef) { mutableStateOf(initialArtworkRef) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            artworkRef = uri.toString()
        }
    }

    PlaylistGlassBackdrop { hazeState ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding().testTag("playlist-edit-list"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 188.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "edit-navigation") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PlaylistGlassPanel(hazeState, radius = 18.dp) {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back to playlist") }
                    }
                    Text("Edit playlist", modifier = Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(
                        enabled = title.isNotBlank(),
                        onClick = { onSave(title.trim(), description.trim().takeIf(String::isNotEmpty), artworkRef) },
                    ) { Icon(Icons.Rounded.Save, "Save playlist", tint = MaterialTheme.colorScheme.primary) }
                }
            }
            item(key = "edit-artwork") {
                PlaylistGlassPanel(hazeState, Modifier.fillMaxWidth(), radius = 32.dp, accented = true) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlaylistArtworkV3(artworkRef, title.ifBlank { "Playlist" }, false, 184.dp)
                        Row(
                            modifier = Modifier.padding(top = 22.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PlaylistGlassAction(hazeState, "Choose image", Icons.Rounded.Image, { imagePicker.launch(arrayOf("image/*")) })
                            if (artworkRef != null) {
                                PlaylistGlassAction(hazeState, "Remove", Icons.Rounded.DeleteOutline, { artworkRef = null })
                            }
                        }
                    }
                }
            }
            item(key = "edit-metadata") {
                PlaylistGlassPanel(hazeState, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("Playlist details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            label = { Text("Playlist name") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            shape = RoundedCornerShape(18.dp),
                            label = { Text("Description") },
                            minLines = 4,
                        )
                    }
                }
            }
            item(key = "edit-save") {
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp),
                    enabled = title.isNotBlank(),
                    onClick = { onSave(title.trim(), description.trim().takeIf(String::isNotEmpty), artworkRef) },
                ) {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Save changes")
                }
            }
        }
    }
}

@Composable
private fun PlaylistArtworkV3(
    artworkRef: String?,
    title: String,
    favorite: Boolean,
    size: Dp,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier.size(size)
            .shadow(14.dp, shape, ambientColor = colors.primary.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkRef != null) {
            TrackArtwork(artworkRef = artworkRef, description = title, size = size)
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = if (favorite) 0.28f else 0.16f), Color.Transparent),
                    ),
                ),
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface.copy(alpha = 0.32f),
                modifier = Modifier.size(size * 0.52f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.28f),
                        tint = colors.primary,
                    )
                }
            }
        }
    }
}
