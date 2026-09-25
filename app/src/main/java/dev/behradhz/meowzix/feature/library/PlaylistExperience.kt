package dev.behradhz.meowzix.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.ui.components.TrackArtwork
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
) {
    favoriteTracks.firstOrNull()?.let { track ->
        LaunchedEffect(track.id, track.artworkRef) { onEnsureArtwork(track) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "favorites-v3") {
            PlaylistMorphCardV3(
                title = "Favorites",
                artworkRef = favoriteTracks.firstOrNull()?.artworkRef,
                favorite = true,
                onClick = onOpenFavorites,
            )
        }
        items(playlists, key = { "playlist-v3:${it.id}" }) { playlist ->
            PlaylistMorphCardV3(
                title = playlist.title,
                artworkRef = playlist.artworkRef ?: playlistArtwork[playlist.id],
                favorite = false,
                onClick = { onOpenPlaylist(playlist.id) },
            )
        }
    }
}

@Composable
private fun PlaylistMorphCardV3(
    title: String,
    artworkRef: String?,
    favorite: Boolean,
    onClick: () -> Unit,
) {
    var opening by remember { mutableStateOf(false) }
    val artworkSize by animateDpAsState(if (opening) 86.dp else 72.dp, label = "playlist-card-morph")

    LaunchedEffect(opening) {
        if (opening) {
            delay(120)
            onClick()
            opening = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !opening) { opening = true },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(if (opening) 26.dp else 20.dp),
        tonalElevation = if (opening) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaylistArtworkV3(
                artworkRef = artworkRef,
                title = title,
                favorite = favorite,
                size = artworkSize,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
) {
    var editing by rememberSaveable { mutableStateOf(false) }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "playlist-hero") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to playlists")
                    }
                    Spacer(Modifier.weight(1f))
                    if (editable) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit playlist")
                        }
                    }
                }
                PlaylistArtworkV3(
                    artworkRef = artworkRef,
                    title = title,
                    favorite = isFavorites,
                    size = 220.dp,
                )
                Text(
                    title,
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                playlist?.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        description,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                    )
                }
                val updatedText = playlist?.updatedAt?.let {
                    DateTimeFormatter.ofPattern("MMM d, yyyy")
                        .withZone(ZoneId.systemDefault())
                        .format(it)
                }
                Text(
                    buildString {
                        append(if (tracks.size == 1) "1 track" else "${tracks.size} tracks")
                        if (updatedText != null) append(" · Updated $updatedText")
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onPlay(sortedTracks, PlaybackMode.ORDERED) }, enabled = sortedTracks.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(onClick = { onPlay(sortedTracks, PlaybackMode.PURE_SHUFFLE) }, enabled = sortedTracks.isNotEmpty()) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Shuffle")
                    }
                    OutlinedButton(onClick = { onPlay(sortedTracks, PlaybackMode.SMART_SHUFFLE) }, enabled = sortedTracks.isNotEmpty()) {
                        Icon(Icons.Rounded.SmartToy, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Smart")
                    }
                }
            }
        }

        item(key = "playlist-controls") {
            PlaylistOrganizationControlsV3(
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

        if (sortedTracks.isEmpty()) {
            item(key = "playlist-empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isFavorites) "Favorite a track and it will appear here." else "This playlist is empty.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f),
                    )
                }
            }
        } else if (sort == PlaylistTrackSort.CUSTOM && groupBy == PlaylistGroupBy.NONE) {
            itemsIndexed(customTracks, key = { _, track -> "track:${track.id}" }) { _, track ->
                val isDragging = draggedTrackId == track.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) draggedDistance else 0f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        SwipeableTrackRowV2(
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
                        )
                    }
                    if (editable) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
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
                            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(groupTracks, key = { "sorted:${it.id}" }) { track ->
                    Box(Modifier.padding(horizontal = 10.dp)) {
                        SwipeableTrackRowV2(
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistOrganizationControlsV3(
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
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Surface(
                modifier = Modifier.clickable { sortMenu = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text("Sort · ${sort.label}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                PlaylistTrackSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { sortMenu = false; onSort(option) },
                    )
                }
            }
        }
        if (sort != PlaylistTrackSort.CUSTOM) {
            IconButton(onClick = onToggleDirection) {
                Icon(
                    if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = if (ascending) "Ascending order" else "Descending order",
                )
            }
        }
        Box {
            Surface(
                modifier = Modifier.clickable { groupMenu = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text("Group by · ${groupBy.label}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
            }
            DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                PlaylistGroupBy.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = {
                            when (option) {
                                PlaylistGroupBy.ARTIST -> Icon(Icons.Rounded.Person, contentDescription = null)
                                PlaylistGroupBy.ALBUM -> Icon(Icons.Rounded.Album, contentDescription = null)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to playlist")
                }
                Text(
                    "Edit playlist",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    enabled = title.isNotBlank(),
                    onClick = { onSave(title.trim(), description.trim().takeIf(String::isNotEmpty), artworkRef) },
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = "Save playlist")
                }
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PlaylistArtworkV3(artworkRef = artworkRef, title = title.ifBlank { "Playlist" }, favorite = false, size = 210.dp)
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Choose image")
                    }
                    if (artworkRef != null) {
                        OutlinedButton(onClick = { artworkRef = null }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Remove")
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Playlist name") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text("Description") },
                minLines = 4,
            )
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), description.trim().takeIf(String::isNotEmpty), artworkRef) },
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Save changes")
            }
        }
    }
}

@Composable
private fun PlaylistArtworkV3(
    artworkRef: String?,
    title: String,
    favorite: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    if (artworkRef != null) {
        TrackArtwork(artworkRef = artworkRef, description = title, size = size)
    } else {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(28.dp),
            color = if (favorite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.34f),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
