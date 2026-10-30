package dev.behradhz.meowzix.feature.queue

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import dev.behradhz.meowzix.feature.library.LocalTrackChatLinkActions
import dev.behradhz.meowzix.feature.library.TrackChatLinkActions
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.runtime.withFrameNanos
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
import dev.behradhz.meowzix.feature.library.TrackActionsMenu
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
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import dev.behradhz.meowzix.domain.playback.QueueGroup
import dev.behradhz.meowzix.domain.playback.QueueGroupKind
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.components.DownloadableTrackArtwork
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.MeowzixStickyHeader
import dev.behradhz.meowzix.ui.components.MeowzixHeaderAction
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@Composable
fun QueueRoute(
    searchQuery: String = "",
    onGoToArtist: (String) -> Unit = {},
    viewModel: QueueViewModel = hiltViewModel(),
    recommendationActions: RecommendationActionsViewModel = hiltViewModel(),
    libraryTools: LibraryToolsViewModel = hiltViewModel(),
    forwardViewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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
        LocalTrackChatLinkActions provides TrackChatLinkActions(
            lookup = libraryTools::telegramMessageLink,
            open = { link ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                runCatching {
                    context.startActivity(Intent(intent).setPackage("org.telegram.messenger"))
                }.recoverCatching { context.startActivity(intent) }
                    .onFailure {
                        Toast.makeText(context, "Unable to open Telegram message", Toast.LENGTH_LONG).show()
                    }
            },
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
            onMoveGroup = viewModel::moveGroup,
            onRemoveGroup = viewModel::removeGroup,
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
    onMoveGroup: (UUID, Int) -> Unit,
    onRemoveGroup: (UUID) -> Unit,
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
    val filteredItems = remember(state.items, state.currentIndex, searchTokens) {
        indexedQueueItems(state.items) { item ->
            if (searchTokens.isEmpty()) {
                true
            } else {
                val haystack = buildString {
                    append(TextNormalizer.normalize(item.title).orEmpty())
                    append(' ')
                    append(TextNormalizer.normalize(item.artist).orEmpty())
                }
                searchTokens.all(haystack::contains)
            }
        }.filter { it.index >= state.currentIndex.coerceAtLeast(0) }
    }
    val searchActive = searchTokens.isNotEmpty()

    var displayItems by remember { mutableStateOf(filteredItems) }
    var draggedQueueIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }
    var draggedGroupId by remember { mutableStateOf<UUID?>(null) }
    var draggedGroupDistance by remember { mutableFloatStateOf(0f) }
    var draggedGroupTarget by remember { mutableStateOf<Int?>(null) }
    var awaitingGroupCommit by remember { mutableStateOf(false) }
    var dragCenterY by remember { mutableFloatStateOf(Float.NaN) }
    val density = LocalDensity.current
    val groupCounts = remember(state.items, state.currentIndex) {
        state.items.drop((state.currentIndex + 1).coerceAtLeast(0))
            .mapNotNull { it.groupId }.groupingBy { it }.eachCount()
    }
    var hasFocusedCurrent by remember { mutableStateOf(false) }
    // Use the authoritative queue immediately outside a drag. A cached display list can
    // otherwise show removed items until a deferred LaunchedEffect catches up.
    val queueItems = if (draggedQueueIndex == null && draggedGroupId == null &&
        !awaitingGroupCommit) filteredItems else displayItems
    val firstGroupPositions = remember(state.items, state.currentIndex) {
        val first = mutableMapOf<UUID, Int>()
        state.items.forEachIndexed { index, item ->
            if (index > state.currentIndex) item.groupId?.let { first.putIfAbsent(it, index) }
        }
        first
    }
    val visibleItems = if (draggedGroupId == null) queueItems else queueItems.filter { entry ->
        entry.value.groupId != draggedGroupId || firstGroupPositions[draggedGroupId] == entry.index
    }

    LaunchedEffect(filteredItems) {
        if (draggedQueueIndex == null && draggedGroupId == null) {
            displayItems = filteredItems
            awaitingGroupCommit = false
        }
    }
    // A rejected/unchanged move must not leave a stale visual preview pinned forever.
    LaunchedEffect(awaitingGroupCommit) {
        if (awaitingGroupCommit) {
            kotlinx.coroutines.delay(1_000L)
            if (awaitingGroupCommit) {
                awaitingGroupCommit = false
                displayItems = filteredItems
            }
        }
    }
    LaunchedEffect(state.currentIndex, state.items.size, searchActive) {
        if (!searchActive && !hasFocusedCurrent && state.currentIndex in state.items.indices) {
            hasFocusedCurrent = true
            listState.scrollToItem(1)
        }
    }

    fun finishDrag(commit: Boolean) {
        val fromIndex = draggedQueueIndex
        if (!searchActive && commit && fromIndex != null) {
            val destinationSlot = displayItems.indexOfFirst { it.index == fromIndex }
            val toIndex = filteredItems.getOrNull(destinationSlot)?.index ?: -1
            if (toIndex >= 0 && fromIndex != toIndex) {
                onMove(fromIndex, toIndex)
            }
        }
        draggedQueueIndex = null
        draggedDistance = 0f
        dragCenterY = Float.NaN
    }

    fun reorderAt(center: Float) {
        val draggedIndex = draggedQueueIndex ?: return
        val from = displayItems.indexOfFirst { it.index == draggedIndex }
        if (from < 0) return
        val positions = listState.layoutInfo.visibleItemsInfo
        val source = positions.firstOrNull { it.index == from + 1 } ?: return
        val target = positions.asSequence()
            .filter { it.index > 0 && it.index != from + 1 }
            .minByOrNull { abs((it.offset + it.size / 2f) - center) } ?: return
        val targetPosition = target.index - 1
        if (targetPosition !in displayItems.indices || targetPosition == from) return
        val targetCenter = target.offset + target.size / 2f
        if ((targetPosition > from && center >= targetCenter) ||
            (targetPosition < from && center <= targetCenter)) {
            val ordered = displayItems.toMutableList()
            val moved = ordered.removeAt(from)
            ordered.add(targetPosition, moved)
            displayItems = ordered
            draggedDistance += source.offset - target.offset
        }
    }

    // Reorder only the visual snapshot. QueueRepository is called once when the handle is released.
    fun reorderGroupAt(center: Float) {
        val id = draggedGroupId ?: return
        val from = visibleItems.indexOfFirst { it.value.groupId == id }
        if (from < 0) return
        val infos = listState.layoutInfo.visibleItemsInfo
        val source = infos.firstOrNull { it.index == from + 1 } ?: return
        val target = infos.asSequence()
            .filter { it.index > 0 && it.index != from + 1 }
            .minByOrNull { abs(it.offset + it.size / 2f - center) } ?: return
        val targetEntry = visibleItems.getOrNull(target.index - 1) ?: return
        if (targetEntry.index <= state.currentIndex || targetEntry.value.groupId == id) return
        val targetCenter = target.offset + target.size / 2f
        val movingDown = target.index > from + 1
        if ((movingDown && center < targetCenter) || (!movingDown && center > targetCenter)) return
        val movingBlock = displayItems.filter { it.value.groupId == id }
        val remaining = displayItems.filterNot { it.value.groupId == id }.toMutableList()
        val targetGroup = targetEntry.value.groupId
        val targetPositions = remaining.indices.filter { index ->
            if (targetGroup != null) remaining[index].value.groupId == targetGroup
            else remaining[index].value.occurrenceId == targetEntry.value.occurrenceId
        }
        if (targetPositions.isEmpty()) return
        val destination = if (movingDown) targetPositions.last() + 1 else targetPositions.first()
        val currentStart = displayItems.indexOfFirst { it.value.groupId == id }
        if (destination == currentStart) return
        remaining.addAll(destination.coerceIn(0, remaining.size), movingBlock)
        displayItems = remaining
        draggedGroupDistance += source.offset - target.offset
    }

    fun finishGroupDrag(id: UUID, commit: Boolean) {
        val lastGroup = displayItems.indexOfLast { it.value.groupId == id }
        val nextItem = displayItems.getOrNull(lastGroup + 1)
        val target = nextItem?.index ?: state.items.size
        val changed = displayItems.indexOfFirst { it.value.groupId == id } !=
            filteredItems.indexOfFirst { it.value.groupId == id }
        draggedGroupId = null
        draggedGroupDistance = 0f
        dragCenterY = Float.NaN
        draggedGroupTarget = null
        if (commit && changed) {
            awaitingGroupCommit = true
            onMoveGroup(id, target)
        } else displayItems = filteredItems
    }

    // One frame-clock loop for both group and track drags: no coroutine per pointer event.
    LaunchedEffect(draggedQueueIndex, draggedGroupId) {
        var previousFrame = 0L
        while (isActive && (draggedQueueIndex != null || draggedGroupId != null)) {
            val frame = withFrameNanos { it }
            val dt = if (previousFrame == 0L) 0f else
                ((frame - previousFrame).coerceAtMost(50_000_000L) / 1_000_000_000f)
            previousFrame = frame
            val y = dragCenterY
            if (!y.isFinite() || dt == 0f) continue
            val layout = listState.layoutInfo
            val top = layout.viewportStartOffset + layout.beforeContentPadding
            val bottom = layout.viewportEndOffset - layout.afterContentPadding
            val slowZone = with(density) { 140.dp.toPx() }
            val fastZone = with(density) { 65.dp.toPx() }
            val slowSpeed = with(density) { 360.dp.toPx() }
            val fastSpeed = with(density) { 1150.dp.toPx() }
            fun speed(distance: Float): Float {
                if (distance >= slowZone) return 0f
                if (distance <= fastZone) {
                    return slowSpeed + (fastSpeed - slowSpeed) *
                        ((fastZone - distance).coerceAtLeast(0f) / fastZone).coerceIn(0f, 1f)
                }
                return 140f * density.density +
                    (slowSpeed - 140f * density.density) *
                    ((slowZone - distance) / (slowZone - fastZone))
            }
            val velocity = when {
                y < top + slowZone -> -speed(y - top)
                y > bottom - slowZone -> speed(bottom - y)
                else -> 0f
            }
            if (velocity != 0f) {
                val moved = listState.scrollBy(velocity * dt)
                if (draggedQueueIndex != null) {
                    draggedDistance += moved
                    reorderAt(y)
                } else {
                    draggedGroupDistance += moved
                    reorderGroupAt(y)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().hazeSource(feedbackHazeState),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 100.dp, bottom = 182.dp),
            // Adjacent grouped rows share one continuous glass container.
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Preserve item index 0 for drag/reorder computations.
            item(key = "queue-header-spacer") { Spacer(Modifier.size(0.dp)) }

            if (visibleItems.isEmpty()) {
                item {
                    if (searchActive) {
                        EmptyQueueSearch(searchQuery)
                    } else {
                        EmptyQueue()
                    }
                }
            } else {
                itemsIndexed(visibleItems, key = { _, entry -> entry.value.occurrenceId.toString() }) { _, entry ->
                    val item = entry.value
                    val queueIndex = entry.index
                    val isDragging = draggedQueueIndex == queueIndex
                    val groupId = item.groupId
                    val group = groupId?.let(state.groups::get)
                    val isGroupHeader = !searchActive && group != null &&
                        firstGroupPositions[groupId] == queueIndex
                    val isGroupDragging = groupId != null && groupId == draggedGroupId
                    val lastGroupMember = groupId != null &&
                        (isGroupDragging || state.items.getOrNull(queueIndex + 1)?.groupId != groupId)
                    val previous = state.items.getOrNull(queueIndex - 1)
                    val nowPlayingSection = !searchActive && queueIndex == state.currentIndex
                    val newManualSection = !searchActive && queueIndex > state.currentIndex &&
                        item.origin == QueueItemOrigin.MANUAL &&
                        (previous?.origin != QueueItemOrigin.MANUAL || queueIndex == state.currentIndex + 1)
                    val newGeneratedSection = !searchActive && queueIndex > state.currentIndex &&
                        item.origin == QueueItemOrigin.GENERATED &&
                        (previous?.origin != QueueItemOrigin.GENERATED || queueIndex == state.currentIndex + 1)
                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging || isGroupDragging) 20f else 0f)
                            .animateItem(
                                fadeInSpec = tween(durationMillis = 180),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                fadeOutSpec = tween(durationMillis = 230),
                            )
                            .graphicsLayer {
                                translationY = when {
                                    isDragging -> draggedDistance
                                    isGroupDragging -> draggedGroupDistance
                                    else -> 0f
                                }
                            },
                    ) {
                        Column(Modifier.animateContentSize()) {
                        if (nowPlayingSection) {
                            Text("Now Playing", modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (newManualSection) {
                            Text("Queue", modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 10.dp),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (newGeneratedSection) {
                            Text("Next in the queue", modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 10.dp),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .padding(bottom = if (groupId != null && !lastGroupMember) 0.dp else 6.dp)
                                .then(
                                    if (groupId != null) {
                                        Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                                                RoundedCornerShape(
                                                    topStart = if (isGroupHeader) 20.dp else 0.dp,
                                                    topEnd = if (isGroupHeader) 20.dp else 0.dp,
                                                    bottomStart = if (lastGroupMember) 20.dp else 0.dp,
                                                    bottomEnd = if (lastGroupMember) 20.dp else 0.dp,
                                                ),
                                            )
                                            .padding(start = 12.dp, end = 7.dp, top = 3.dp, bottom = 3.dp)
                                    } else Modifier
                                ),
                        ) {
                        Column {
                        if (isGroupHeader && group != null) {
                            GroupQueueHeader(
                                group = group,
                                count = groupCounts[group.id] ?: 0,
                                compact = isGroupDragging,
                                offsetY = 0f,
                                reorderEnabled = !searchActive && draggedQueueIndex == null,
                                onStart = {
                                    displayItems = filteredItems
                                    draggedGroupId = group.id
                                    draggedGroupDistance = 0f
                                    draggedGroupTarget = queueIndex
                                    dragCenterY = Float.NaN
                                },
                                onDrag = { delta ->
                                    draggedGroupDistance += delta
                                    val info = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == item.occurrenceId.toString() }
                                    if (info != null) {
                                        val center = info.offset + info.size / 2f + draggedGroupDistance
                                        dragCenterY = center
                                        reorderGroupAt(center)
                                    }
                                },
                                onStop = { commit -> finishGroupDrag(group.id, commit) },
                                onRemove = { onRemoveGroup(group.id) },
                                onMoveToFront = { onMoveGroup(group.id, state.currentIndex + 1) },
                                onMoveToEnd = {
                                    val firstGenerated = state.items.indices.firstOrNull { index ->
                                        index > state.currentIndex && state.items[index].origin == QueueItemOrigin.GENERATED
                                    } ?: state.items.size
                                    onMoveGroup(group.id, firstGenerated)
                                },
                            )
                        }
                        if (!isGroupDragging) SwipeableQueueItem(
                            item = item,
                            isCurrent = queueIndex == state.currentIndex,
                            isDragging = isDragging,
                            dragOffsetY = 0f,
                            reorderEnabled = !searchActive && draggedGroupId == null &&
                                queueIndex != state.currentIndex,
                            onPlay = { if (state.items.getOrNull(queueIndex) == item) onPlay(queueIndex) },
                            onDragStart = {
                                if (!searchActive && draggedGroupId == null) {
                                    displayItems = filteredItems
                                    draggedQueueIndex = queueIndex
                                    draggedDistance = 0f
                                }
                            },
                            onDrag = { deltaY ->
                                if (searchActive) return@SwipeableQueueItem
                                val dragged = draggedQueueIndex ?: return@SwipeableQueueItem
                                val from = displayItems.indexOfFirst { it.index == dragged }
                                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from + 1 }
                                    ?: return@SwipeableQueueItem
                                draggedDistance += deltaY
                                dragCenterY = info.offset + info.size / 2f + draggedDistance
                                reorderAt(dragCenterY)
                            },
                            onDragEnd = { finishDrag(commit = true) },
                            onDragCancel = {
                                displayItems = filteredItems
                                finishDrag(commit = false)
                            },
                            onRemove = {
                                if (state.items.getOrNull(queueIndex) == item) {
                                    onRemove(queueIndex)
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
                }
            }
        }

        MeowzixStickyHeader(
            title = "Queue",
            subtitle = when {
                state.items.isEmpty() -> "Nothing queued"
                searchActive -> "${filteredItems.size} of ${state.items.size} tracks"
                else -> "${state.items.size} tracks"
            },
            hazeState = feedbackHazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (state.items.size > 1) {
                MeowzixHeaderAction(
                    if (state.playbackMode == PlaybackMode.PURE_SHUFFLE) "Disable shuffle" else "Shuffle queue",
                    Icons.Rounded.Shuffle, onToggleShuffle,
                )
            }
            if (state.items.isNotEmpty()) {
                MeowzixHeaderAction("Clear queue", Icons.Rounded.DeleteSweep, onClear)
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

@Composable
private fun GroupQueueHeader(
    group: QueueGroup,
    count: Int,
    compact: Boolean,
    offsetY: Float,
    reorderEnabled: Boolean,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onStop: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onMoveToFront: () -> Unit,
    onMoveToEnd: () -> Unit,
) {
    var menuExpanded by remember(group.id) { mutableStateOf(false) }
    var swipeX by remember(group.id) { mutableFloatStateOf(0f) }
    var horizontalDrag by remember(group.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val actionThreshold = with(density) { 75.dp.toPx() }
    val removeThreshold = with(density) { 150.dp.toPx() }
    val haptics = rememberMeowzixHaptics()
    val animatedSwipeX by animateFloatAsState(
        targetValue = swipeX,
        animationSpec = if (horizontalDrag) snap() else tween(190),
        label = "group-swipe",
    )
    val swipingRight = animatedSwipeX > 0f
    val deleting = abs(animatedSwipeX) >= removeThreshold
    Box(Modifier.fillMaxWidth().graphicsLayer { translationY = offsetY }) {
        if (abs(animatedSwipeX) > 0.5f) {
            Surface(
                modifier = Modifier.matchParentSize().drawWithContent {
                    val edge = animatedSwipeX.coerceIn(-size.width, size.width)
                    if (edge > 0f) clipRect(left = 0f, right = edge) {
                        this@drawWithContent.drawContent()
                    } else if (edge < 0f) clipRect(left = size.width + edge, right = size.width) {
                        this@drawWithContent.drawContent()
                    }
                },
                shape = RoundedCornerShape(18.dp),
                color = if (deleting) MaterialTheme.colorScheme.errorContainer
                    else if (swipingRight) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = if (swipingRight) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (deleting) Icons.Rounded.Delete else if (swipingRight)
                            Icons.Rounded.PlaylistPlay else Icons.Rounded.PlaylistAdd,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (deleting) "Remove group" else if (swipingRight) "Play next" else "Add to queue",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { translationX = animatedSwipeX }
                .pointerInput(group.id, actionThreshold, removeThreshold) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = true },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            val old = abs(swipeX)
                            swipeX = (swipeX + delta).coerceIn(-removeThreshold * 2, removeThreshold * 2)
                            if ((old < actionThreshold && abs(swipeX) >= actionThreshold) ||
                                (old < removeThreshold && abs(swipeX) >= removeThreshold)) {
                                haptics.perform(MeowzixHapticCue.Threshold)
                            }
                        },
                        onDragEnd = {
                            val released = swipeX
                            horizontalDrag = false
                            if (abs(released) < removeThreshold) swipeX = 0f
                            when {
                                abs(released) >= removeThreshold -> onRemove()
                                released >= actionThreshold -> onMoveToFront()
                                released <= -actionThreshold -> onMoveToEnd()
                            }
                        },
                        onDragCancel = { horizontalDrag = false; swipeX = 0f },
                    )
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.17f),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 12.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (group.kind == QueueGroupKind.ALBUM) Icons.Rounded.Album else Icons.Rounded.Person,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${group.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · $count tracks",
                        style = MaterialTheme.typography.bodySmall)
                }
                // Match single-track cards: drag handle before the overflow button.
                Box(
                    modifier = Modifier.size(42.dp).pointerInput(group.id, reorderEnabled) {
                        if (reorderEnabled) detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.perform(MeowzixHapticCue.Threshold)
                                onStart()
                            },
                            onDrag = { change, delta -> change.consume(); onDrag(delta.y) },
                            onDragEnd = { onStop(true) },
                            onDragCancel = { onStop(false) },
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = "Drag entire group",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Group actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.width(280.dp),
                        offset = androidx.compose.ui.unit.DpOffset((-8).dp, 0.dp),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.995f),
                                        ),
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(group.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count tracks · ${group.kind.name.lowercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.size(6.dp))
                            DropdownMenuItem(
                                text = { Text("Play next") },
                                leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                                onClick = { menuExpanded = false; onMoveToFront() },
                            )
                            DropdownMenuItem(
                                text = { Text("Add to queue") },
                                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                                onClick = { menuExpanded = false; onMoveToEnd() },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove group") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = { menuExpanded = false; onRemove() },
                            )
                        }
                    }
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
    val chatActions = LocalTrackChatLinkActions.current
    var menuExpanded by remember { mutableStateOf(false) }
    var chatLink by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(menuExpanded, item.id) {
        if (menuExpanded) {
            chatLink = try {
                chatActions?.lookup?.invoke(item.id)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
        }
    }
    val density = LocalDensity.current
    val haptics = rememberMeowzixHaptics()
    val actionThreshold = with(density) { 75.dp.toPx() }
    val removeThreshold = with(density) { 150.dp.toPx() }
    val maximumSwipe = with(density) { 300.dp.toPx() }
    var swipeOffsetX by remember(item.occurrenceId) { mutableFloatStateOf(0f) }
    var swipeStage by remember(item.occurrenceId) { mutableIntStateOf(0) }
    var horizontalDragActive by remember(item.occurrenceId) { mutableStateOf(false) }
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
                modifier = Modifier.matchParentSize()
                    .drawWithContent {
                        val edge = visualOffsetX.coerceIn(-size.width, size.width)
                        if (edge > 0f) clipRect(left = 0f, right = edge) {
                            this@drawWithContent.drawContent()
                        } else if (edge < 0f) clipRect(left = size.width + edge, right = size.width) {
                            this@drawWithContent.drawContent()
                        }
                    },
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
                    }
                    .drawWithContent {
                        val edge = visualOffsetX.coerceIn(-size.width, size.width)
                        if (edge > 0f) clipRect(left = 0f, right = edge) {
                            this@drawWithContent.drawContent()
                        } else if (edge < 0f) clipRect(left = size.width + edge, right = size.width) {
                            this@drawWithContent.drawContent()
                        }
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
                .pointerInput(item.occurrenceId, isDragging, actionThreshold, removeThreshold) {
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
                                // Preserve the swipe translation while the row animates out.
                                if (abs(releasedOffset) < removeThreshold) swipeOffsetX = 0f
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
            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)
                    else Color(0x4D252329),
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                        .pointerInput(item.occurrenceId, reorderEnabled) {
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
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                    }
                    if (menuExpanded) {
                        TrackActionsMenu(
                            expanded = menuExpanded,
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
                            onViewInChat = chatLink?.let { link -> { chatActions?.open?.invoke(link) } },
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
