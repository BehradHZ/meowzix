package dev.behradhz.meowzix.feature.queue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistAddCircle
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.components.DownloadableTrackArtwork
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun QueueRoute(
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aux by viewModel.aux.collectAsStateWithLifecycle()
    QueueScreen(
        state = state,
        aux = aux,
        onPlay = viewModel::play,
        onMove = viewModel::move,
        onRemove = viewModel::remove,
        onClear = viewModel::clear,
        onToggleShuffle = viewModel::toggleShuffle,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onPinOffline = viewModel::pinOffline,
        onFavorite = viewModel::toggleFavorite,
        onAddToPlaylist = viewModel::addToPlaylist,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueScreen(
    state: QueueState,
    aux: QueueAuxState,
    onPlay: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlayNext: (UUID) -> Unit,
    onAddToQueue: (UUID) -> Unit,
    onPinOffline: (UUID) -> Unit,
    onFavorite: (UUID) -> Unit,
    onAddToPlaylist: (UUID, UUID) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var displayItems by remember { mutableStateOf(state.items) }
    var draggedItemId by remember { mutableStateOf<UUID?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }
    var hasFocusedCurrent by remember { mutableStateOf(false) }

    LaunchedEffect(state.items, draggedItemId) {
        if (draggedItemId == null) displayItems = state.items
    }
    LaunchedEffect(state.currentIndex, state.items.size) {
        if (!hasFocusedCurrent && state.currentIndex in state.items.indices) {
            hasFocusedCurrent = true
            listState.scrollToItem(state.currentIndex + 1)
        }
    }

    fun finishDrag(commit: Boolean) {
        val id = draggedItemId
        if (commit && id != null) {
            val fromIndex = state.items.indexOfFirst { it.id == id }
            val toIndex = displayItems.indexOfFirst { it.id == id }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                onMove(fromIndex, toIndex)
            }
        }
        draggedItemId = null
        draggedDistance = 0f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        stickyHeader {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Queue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.items.isEmpty()) "Nothing queued" else "${state.items.size} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                        )
                    }
                    if (state.items.size > 1) {
                        val shuffleEnabled = state.playbackMode == PlaybackMode.PURE_SHUFFLE
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = if (shuffleEnabled) "Disable shuffle" else "Shuffle queue",
                                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.items.isNotEmpty()) {
                        Button(onClick = onClear, shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        }

        if (displayItems.isEmpty()) {
            item { EmptyQueue() }
        } else {
            itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                val isDragging = draggedItemId == item.id
                SwipeableQueueItem(
                    item = item,
                    isCurrent = item.id == state.items.getOrNull(state.currentIndex)?.id,
                    isDragging = isDragging,
                    dragOffsetY = if (isDragging) draggedDistance else 0f,
                    onPlay = {
                        val actualIndex = state.items.indexOfFirst { it.id == item.id }
                        if (actualIndex >= 0) onPlay(actualIndex)
                    },
                    onDragStart = {
                        draggedItemId = item.id
                        draggedDistance = 0f
                    },
                    onDrag = { deltaY ->
                        val draggedId = draggedItemId ?: return@SwipeableQueueItem
                        val currentIndex = displayItems.indexOfFirst { it.id == draggedId }
                        if (currentIndex < 0) return@SwipeableQueueItem
                        draggedDistance += deltaY

                        val currentInfo = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == currentIndex + 1 }
                            ?: return@SwipeableQueueItem
                        val draggedCenter = currentInfo.offset + currentInfo.size / 2f + draggedDistance
                        val targetInfo = listState.layoutInfo.visibleItemsInfo
                            .asSequence()
                            .filter { it.index > 0 && it.index != currentIndex + 1 }
                            .minByOrNull { info -> abs((info.offset + info.size / 2f) - draggedCenter) }
                        val targetIndex = targetInfo?.index?.minus(1)

                        if (targetInfo != null && targetIndex != null && targetIndex in displayItems.indices && targetIndex != currentIndex) {
                            val targetCenter = targetInfo.offset + targetInfo.size / 2f
                            val crossedTarget = if (targetIndex > currentIndex) {
                                draggedCenter >= targetCenter
                            } else {
                                draggedCenter <= targetCenter
                            }
                            if (crossedTarget) {
                                val oldOffset = currentInfo.offset
                                val reordered = displayItems.toMutableList()
                                val moved = reordered.removeAt(currentIndex)
                                reordered.add(targetIndex, moved)
                                displayItems = reordered
                                draggedDistance += oldOffset - targetInfo.offset
                            }
                        }

                        val layout = listState.layoutInfo
                        val draggedTop = currentInfo.offset + draggedDistance
                        val draggedBottom = draggedTop + currentInfo.size
                        val overscroll = when {
                            deltaY > 0f && draggedBottom > layout.viewportEndOffset ->
                                (draggedBottom - layout.viewportEndOffset).coerceAtMost(currentInfo.size.toFloat())
                            deltaY < 0f && draggedTop < layout.viewportStartOffset ->
                                (draggedTop - layout.viewportStartOffset).coerceAtLeast(-currentInfo.size.toFloat())
                            else -> 0f
                        }
                        if (overscroll != 0f) {
                            scope.launch {
                                val consumed = listState.scrollBy(overscroll)
                                draggedDistance += consumed
                            }
                        }
                    },
                    onDragEnd = { finishDrag(commit = true) },
                    onDragCancel = {
                        displayItems = state.items
                        finishDrag(commit = false)
                    },
                    onRemove = {
                        val actualIndex = state.items.indexOfFirst { it.id == item.id }
                        if (actualIndex >= 0) onRemove(actualIndex)
                    },
                    onPlayNext = { onPlayNext(item.id) },
                    onAddToQueue = { onAddToQueue(item.id) },
                    onPinOffline = { onPinOffline(item.id) },
                    availability = aux.availability[item.id] ?: LibraryTrackAvailability.UNAVAILABLE,
                    download = aux.downloads[item.id],
                    favorite = aux.tracks[item.id]?.favorite == true,
                    playlists = aux.playlists,
                    onFavorite = { onFavorite(item.id) },
                    onAddToPlaylist = { playlistId -> onAddToPlaylist(item.id, playlistId) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableQueueItem(
    item: QueueItem,
    isCurrent: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onPlay: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    availability: LibraryTrackAvailability,
    download: OfflineDownload?,
    favorite: Boolean,
    playlists: List<PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.22f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPlayNext()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onRemove()
                    true
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    SwipeToDismissBox(
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY },
        state = dismissState,
        enableDismissFromStartToEnd = !isDragging,
        enableDismissFromEndToStart = !isDragging,
        backgroundContent = {
            val playNext = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                color = if (playNext) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                else MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    horizontalArrangement = if (playNext) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playNext) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Play next", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Delete from queue", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
            shape = RoundedCornerShape(18.dp),
            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shadowElevation = if (isDragging) 6.dp else 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadableTrackArtwork(
                    artworkRef = item.artworkRef,
                    description = item.title,
                    size = 52.dp,
                    isOffline = availability == LibraryTrackAvailability.OFFLINE,
                    download = download,
                    onDownload = onPinOffline,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (isCurrent) "Playing now · ${item.artist ?: "Unknown artist"}" else item.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragCancel = onDragCancel,
                                onDragEnd = onDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = "Drag to reorder")
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
                            text = { Text(if (favorite) "Remove favorite" else "Favorite") },
                            leadingIcon = { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null) },
                            onClick = { menuExpanded = false; onFavorite() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete from queue") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onRemove() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyQueue() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Queue is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Add tracks from your library. Swipe a queue card right for Play next or left to remove it.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
        }
    }
}
