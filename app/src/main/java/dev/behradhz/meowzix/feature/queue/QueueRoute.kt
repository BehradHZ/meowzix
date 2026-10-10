package dev.behradhz.meowzix.feature.queue

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.feature.library.LibraryRecommendationActions
import dev.behradhz.meowzix.feature.library.LibraryTrackToolsActions
import dev.behradhz.meowzix.feature.library.LibraryToolsDialogs
import dev.behradhz.meowzix.feature.library.LibraryToolsViewModel
import dev.behradhz.meowzix.feature.library.LocalLibraryRecommendationActions
import dev.behradhz.meowzix.feature.library.LocalLibraryTrackToolsActions
import dev.behradhz.meowzix.feature.library.LocalTrackForwardAction
import dev.behradhz.meowzix.feature.library.TrackActionsSheet
import dev.behradhz.meowzix.feature.library.TrackForwardOverlay
import dev.behradhz.meowzix.feature.library.TrackForwardSelection
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.feature.recommendation.RecommendationActionDialogs
import dev.behradhz.meowzix.feature.recommendation.RecommendationActionsViewModel
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.components.DownloadableTrackArtwork
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun QueueRoute(
    searchQuery: String = "",
    onGoToArtist: (String) -> Unit = {},
    viewModel: QueueViewModel = hiltViewModel(),
    recommendationActions: RecommendationActionsViewModel = hiltViewModel(),
    libraryTools: LibraryToolsViewModel = hiltViewModel(),
    forwardViewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aux by viewModel.aux.collectAsStateWithLifecycle()
    var forwardSelection by remember { mutableStateOf<TrackForwardSelection?>(null) }

    CompositionLocalProvider(
        LocalLibraryRecommendationActions provides LibraryRecommendationActions(
            continueVibe = recommendationActions::continueVibe,
            why = recommendationActions::why,
        ),
        LocalLibraryTrackToolsActions provides LibraryTrackToolsActions(
            editMetadata = libraryTools::openMetadata,
            manageDuplicates = libraryTools::openDuplicates,
        ),
        LocalTrackForwardAction provides { selection ->
            forwardSelection = selection
            forwardViewModel.openForwardPickerForTrack(selection.id)
        },
    ) {
        QueueScreen(
            state = state,
            aux = aux,
            searchQuery = searchQuery,
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
            onGoToArtist = onGoToArtist,
        )
        RecommendationActionDialogs(recommendationActions)
        LibraryToolsDialogs(libraryTools)
        TrackForwardOverlay(
            selection = forwardSelection,
            viewModel = forwardViewModel,
            onDismiss = { forwardSelection = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueScreen(
    state: QueueState,
    aux: QueueAuxState,
    searchQuery: String,
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
    onGoToArtist: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackHazeState = rememberHazeState()
    val searchTokens = remember(searchQuery) {
        TextNormalizer.normalize(searchQuery)
            ?.split(' ')
            ?.filter(String::isNotBlank)
            .orEmpty()
    }
    val filteredItems = remember(state.items, searchTokens) {
        if (searchTokens.isEmpty()) {
            state.items
        } else {
            state.items.filter { item ->
                val haystack = buildString {
                    append(TextNormalizer.normalize(item.title).orEmpty())
                    append(' ')
                    append(TextNormalizer.normalize(item.artist).orEmpty())
                }
                searchTokens.all(haystack::contains)
            }
        }
    }
    val searchActive = searchTokens.isNotEmpty()

    var displayItems by remember { mutableStateOf(filteredItems) }
    var draggedItemId by remember { mutableStateOf<UUID?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }
    var hasFocusedCurrent by remember { mutableStateOf(false) }

    LaunchedEffect(filteredItems, draggedItemId) {
        if (draggedItemId == null) displayItems = filteredItems
    }
    LaunchedEffect(state.currentIndex, state.items.size, searchActive) {
        if (!searchActive && !hasFocusedCurrent && state.currentIndex in state.items.indices) {
            hasFocusedCurrent = true
            listState.scrollToItem(state.currentIndex + 1)
        }
    }

    fun finishDrag(commit: Boolean) {
        val id = draggedItemId
        if (!searchActive && commit && id != null) {
            val fromIndex = state.items.indexOfFirst { it.id == id }
            val toIndex = displayItems.indexOfFirst { it.id == id }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                onMove(fromIndex, toIndex)
            }
        }
        draggedItemId = null
        draggedDistance = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(feedbackHazeState),
    ) {
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
                                when {
                                    state.items.isEmpty() -> "Nothing queued"
                                    searchActive -> "${filteredItems.size} of ${state.items.size} tracks"
                                    else -> "${state.items.size} tracks"
                                },
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
                item {
                    if (searchActive) {
                        EmptyQueueSearch(searchQuery)
                    } else {
                        EmptyQueue()
                    }
                }
            } else {
                itemsIndexed(displayItems, key = { index, item -> "${item.id}-$index" }) { _, item ->
                    val isDragging = draggedItemId == item.id
                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 180),
                            placementSpec = tween(durationMillis = 220),
                            fadeOutSpec = tween(durationMillis = 180),
                        ),
                    ) {
                        SwipeableQueueItem(
                            item = item,
                            isCurrent = item.id == state.items.getOrNull(state.currentIndex)?.id,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) draggedDistance else 0f,
                            reorderEnabled = !searchActive,
                            onPlay = {
                                val actualIndex = state.items.indexOfFirst { it.id == item.id }
                                if (actualIndex >= 0) onPlay(actualIndex)
                            },
                            onDragStart = {
                                if (!searchActive) {
                                    draggedItemId = item.id
                                    draggedDistance = 0f
                                }
                            },
                            onDrag = { deltaY ->
                                if (searchActive) return@SwipeableQueueItem
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
                                displayItems = filteredItems
                                finishDrag(commit = false)
                            },
                            onRemove = {
                                val actualIndex = state.items.indexOfFirst { it.id == item.id }
                                if (actualIndex >= 0) {
                                    onRemove(actualIndex)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar("Removed from queue · ${item.title}")
                                    }
                                }
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
                            onGoToArtist = { artist -> onGoToArtist(artist) },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp, bottom = 166.dp),
        ) { data ->
            GlassSurface(
                hazeState = feedbackHazeState,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = data.visuals.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
    reorderEnabled: Boolean,
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
    onGoToArtist: (String) -> Unit,
) {
    val recommendationActions = LocalLibraryRecommendationActions.current
    val trackToolsActions = LocalLibraryTrackToolsActions.current
    val forwardAction = LocalTrackForwardAction.current
    var menuExpanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = rememberMeowzixHaptics()
    val actionThreshold = with(density) { 75.dp.toPx() }
    val removeThreshold = with(density) { 150.dp.toPx() }
    val maximumSwipe = with(density) { 300.dp.toPx() }
    var swipeOffsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var swipeStage by remember(item.id) { mutableIntStateOf(0) }
    var horizontalDragActive by remember(item.id) { mutableStateOf(false) }
    val visualOffsetX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = if (horizontalDragActive) snap() else tween(190),
        label = "queue-two-stage-swipe",
    )
    val swipeMagnitude = abs(visualOffsetX)
    val swipingRight = visualOffsetX >= 0f
    val removeStage = swipeMagnitude >= removeThreshold
    val actionExitProgress by animateFloatAsState(
        targetValue = if (removeStage) 1f else 0f,
        animationSpec = tween(durationMillis = 190),
        label = "queue-action-exit",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY },
    ) {
        if (swipeMagnitude > 0.5f) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    horizontalArrangement = if (swipingRight) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (swipingRight) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = actionExitProgress * visualOffsetX
                    },
                shape = RoundedCornerShape(18.dp),
                color = if (swipingRight) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    horizontalArrangement = if (swipingRight) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (swipingRight) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Play next", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Add to queue", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = visualOffsetX }
                .pointerInput(item.id, isDragging, actionThreshold, removeThreshold) {
                    if (!isDragging) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                horizontalDragActive = true
                                swipeStage = 0
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                val nextOffset = (swipeOffsetX + amount).coerceIn(-maximumSwipe, maximumSwipe)
                                val nextMagnitude = abs(nextOffset)
                                val nextStage = when {
                                    nextMagnitude >= removeThreshold -> 2
                                    nextMagnitude >= actionThreshold -> 1
                                    else -> 0
                                }
                                if (nextStage > swipeStage) {
                                    haptics.perform(MeowzixHapticCue.Threshold)
                                }
                                swipeStage = nextStage
                                swipeOffsetX = nextOffset
                            },
                            onDragCancel = {
                                horizontalDragActive = false
                                swipeStage = 0
                                swipeOffsetX = 0f
                            },
                            onDragEnd = {
                                val releasedOffset = swipeOffsetX
                                horizontalDragActive = false
                                swipeStage = 0
                                swipeOffsetX = 0f
                                when {
                                    abs(releasedOffset) >= removeThreshold -> onRemove()
                                    abs(releasedOffset) >= actionThreshold && releasedOffset > 0f -> onPlayNext()
                                    abs(releasedOffset) >= actionThreshold -> onAddToQueue()
                                }
                            },
                        )
                    }
                }
                .clickable(onClick = onPlay),
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
                        .pointerInput(item.id, reorderEnabled) {
                            if (reorderEnabled) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart() },
                                    onDragCancel = onDragCancel,
                                    onDragEnd = onDragEnd,
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.y)
                                    },
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = if (reorderEnabled) "Drag to reorder" else "Reordering disabled while filtering",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (reorderEnabled) 1f else 0.28f),
                    )
                }
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                }
            }
        }
        if (menuExpanded) {
            TrackActionsSheet(
                title = item.title,
                artist = item.artist,
                favorite = favorite,
                isQueue = true,
                playlists = playlists,
                onDismiss = { menuExpanded = false },
                onPrimary = onRemove,
                onAddToQueue = onAddToQueue,
                onFavorite = onFavorite,
                onForward = { forwardAction(TrackForwardSelection(item.id, item.title, item.artist)) },
                onGoToArtist = { item.artist?.let(onGoToArtist) },
                onAddToPlaylist = onAddToPlaylist,
                onContinueVibe = { recommendationActions?.continueVibe?.invoke(item.id) },
                onEditMetadata = { trackToolsActions?.editMetadata?.invoke(item.id) },
                onManageDuplicates = { trackToolsActions?.manageDuplicates?.invoke(item.id) },
                onWhy = { recommendationActions?.why?.invoke(item.id) },
            )
        }
    }
}

@Composable
private fun EmptyQueueSearch(query: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No matches", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "No queued tracks match “${query.trim()}”",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            )
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
            Text("Add tracks from your library. Swipe a little for Play next / Add to queue, or keep pulling to remove.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
        }
    }
}
