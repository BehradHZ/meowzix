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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import dev.behradhz.meowzix.navigation.dockAwareBottomPadding
import dev.behradhz.meowzix.navigation.LocalFloatingControlsTouchBounds
import dev.behradhz.meowzix.navigation.LocalQueueReorderGestureState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import dev.behradhz.meowzix.ui.components.GlassLazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SelectAll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.feature.library.LibraryRecommendationActions
import dev.behradhz.meowzix.feature.library.LibraryTrackToolsActions
import dev.behradhz.meowzix.feature.library.LibraryToolsDialogs
import dev.behradhz.meowzix.feature.library.LibraryToolsViewModel
import dev.behradhz.meowzix.feature.library.LibrarySelectionMoreMenu
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
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.navigation.ContextualSelectionController
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import dev.behradhz.meowzix.domain.playback.QueueGroup
import dev.behradhz.meowzix.domain.playback.QueueGroupKind
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.components.DownloadableTrackArtwork
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.behradhz.meowzix.ui.components.StagedSwipeReveal
import dev.behradhz.meowzix.ui.components.TRACK_CARD_ALPHA
import dev.behradhz.meowzix.ui.components.TRACK_ROW_HORIZONTAL_PADDING
import dev.behradhz.meowzix.ui.components.TRACK_ROW_VERTICAL_PADDING
import dev.behradhz.meowzix.ui.components.TRACK_ROW_SPACING
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.MeowzixStickyHeader
import dev.behradhz.meowzix.ui.components.MeowzixHeaderAction
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
fun QueueRoute(
    isActive: Boolean = true,
    selectionController: ContextualSelectionController? = null,
    selectionMoreActions: List<LibrarySelectionAction> = emptyList(),
    scrollToTopRequest: Int = 0,
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
    LaunchedEffect(viewModel) {
        viewModel.removalErrors.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

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
            originLookup = libraryTools::telegramChatOrigin,
            refreshOrigin = libraryTools::refreshTelegramChatOrigin,
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
            isActive = isActive,
            selectionController = selectionController,
            selectionMoreActions = selectionMoreActions,
            onBulkAction = viewModel::performSelectionAction,
            onBulkAddToPlaylist = viewModel::addSelectedToPlaylist,
            onBulkForward = { items ->
                viewModel.resolveSelectionTracks(items.map(QueueItem::id)) { tracks ->
                    val first = tracks.firstOrNull() ?: return@resolveSelectionTracks
                    forwardSelection = TrackForwardSelection(
                        first.id,
                        if (tracks.size == 1) first.title else "${tracks.size} selected tracks",
                        if (tracks.size == 1) first.artist else null,
                    )
                    forwardViewModel.openForwardPickerForTracks(tracks)
                }
            },
            scrollToTopRequest = scrollToTopRequest,
            state = state,
            aux = aux,
            searchQuery = searchQuery,
            onPlay = viewModel::play,
            onMove = viewModel::move,
            onRemove = viewModel::removeOccurrence,
            onMoveGroup = viewModel::moveGroup,
            onRemoveGroup = viewModel::removeGroup,
            onClear = viewModel::clear,
            onToggleShuffle = viewModel::toggleShuffle,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onPinOffline = viewModel::pinOffline,
            onRemoveTrack = viewModel::removeTrack,
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

/** Shared Queue gestures and layout within the compact main/lock-screen player. */
@Composable
fun PlayerInlineQueue(
    modifier: Modifier = Modifier,
    onGoToArtist: (String) -> Unit = {},
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aux by viewModel.aux.collectAsStateWithLifecycle()
    QueueScreen(
        state = state,
        aux = aux,
        searchQuery = "",
        onPlay = viewModel::play,
        onMove = viewModel::move,
        onRemove = viewModel::removeOccurrence,
        onMoveGroup = viewModel::moveGroup,
        onRemoveGroup = viewModel::removeGroup,
        onClear = viewModel::clear,
        onToggleShuffle = viewModel::toggleShuffle,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onPinOffline = viewModel::pinOffline,
        onRemoveTrack = viewModel::removeTrack,
        onFavorite = viewModel::toggleFavorite,
        onAddToPlaylist = viewModel::addToPlaylist,
        onGoToArtist = onGoToArtist,
        modifier = modifier,
        inlineMode = true,
    )
}

/** Block a whole Queue row if even its bottom edge enters the protected region. */
internal fun queueRowBlockedByFloatingControls(
    rowBottomInWindow: Float,
    controlsTopInWindow: Float,
    guardPx: Float,
): Boolean = !rowBottomInWindow.isFinite() ||
    rowBottomInWindow > controlsTopInWindow - guardPx

/** A partially hidden row can still be dragged when its handle is fully exposed. */
internal fun queueDragHandleEnabled(
    allowed: Boolean,
    isDragging: Boolean,
    handleBottomInWindow: Float,
    controlsTopInWindow: Float,
    guardPx: Float,
): Boolean = allowed && (isDragging || !queueRowBlockedByFloatingControls(
    handleBottomInWindow, controlsTopInWindow, guardPx,
))

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QueueScreen(
    scrollToTopRequest: Int = 0,
    state: QueueState,
    aux: QueueAuxState,
    searchQuery: String,
    onPlay: (Int) -> Unit,
    onMove: (Int, Int, QueueItemOrigin) -> Unit,
    onRemove: (UUID) -> Unit,
    onMoveGroup: (UUID, Int) -> Unit,
    onRemoveGroup: (UUID) -> Unit,
    onClear: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlayNext: (UUID) -> Unit,
    onAddToQueue: (UUID) -> Unit,
    onPinOffline: (UUID) -> Unit,
    onRemoveTrack: (UUID, Boolean, Boolean) -> Unit,
    onFavorite: (UUID) -> Unit,
    onAddToPlaylist: (UUID, UUID) -> Unit,
    onGoToArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    inlineMode: Boolean = false,
    isActive: Boolean = true,
    selectionController: ContextualSelectionController? = null,
    selectionMoreActions: List<LibrarySelectionAction> = emptyList(),
    onBulkAction: (LibrarySelectionAction, List<QueueItem>) -> Unit = { _, _ -> },
    onBulkAddToPlaylist: (List<QueueItem>, UUID) -> Unit = { _, _ -> },
    onBulkForward: (List<QueueItem>) -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedOccurrences by remember { mutableStateOf(emptySet<UUID>()) }
    var showSelectionPlaylists by remember { mutableStateOf(false) }
    val selectedItems = state.items.filter { it.occurrenceId in selectedOccurrences }
    fun cancelSelection() {
        selectionMode = false
        selectedOccurrences = emptySet()
        showSelectionPlaylists = false
    }
    fun toggleSelection(occurrence: UUID) {
        selectedOccurrences = if (occurrence in selectedOccurrences) selectedOccurrences - occurrence
            else selectedOccurrences + occurrence
        if (selectedOccurrences.isEmpty()) selectionMode = false
    }
    fun handleSelectionAction(action: LibrarySelectionAction) {
        if (selectedItems.isEmpty()) return
        when (action) {
            LibrarySelectionAction.ADD_TO_PLAYLIST -> showSelectionPlaylists = true
            LibrarySelectionAction.SHARE -> {
                onBulkForward(selectedItems)
                cancelSelection()
            }
            else -> {
                onBulkAction(action, selectedItems)
                cancelSelection()
            }
        }
    }
    SideEffect {
        selectionController?.active = isActive && selectionMode
        selectionController?.onAction = ::handleSelectionAction
    }
    DisposableEffect(selectionController) {
        onDispose { selectionController?.active = false }
    }
    androidx.activity.compose.BackHandler(enabled = isActive && selectionMode) { cancelSelection() }
    LaunchedEffect(state.items) {
        val available = state.items.map(QueueItem::occurrenceId).toSet()
        selectedOccurrences = selectedOccurrences.intersect(available)
        if (selectionMode && selectedOccurrences.isEmpty()) selectionMode = false
    }

    var displayItems by remember { mutableStateOf(filteredItems) }
    var draggedQueueIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOccurrenceId by remember { mutableStateOf<UUID?>(null) }
    var draggedGroupId by remember { mutableStateOf<UUID?>(null) }
    var draggedGroupTarget by remember { mutableStateOf<Int?>(null) }
    var awaitingGroupCommit by remember { mutableStateOf(false) }
    var awaitingTrackCommit by remember { mutableStateOf(false) }
    var dragCenterY by remember { mutableFloatStateOf(Float.NaN) }
    // Only the floating preview follows the actual finger. Drop hit-testing
    // intentionally continues to use dragCenterY in LazyColumn coordinates.
    var dragFingerYInRoot by remember { mutableFloatStateOf(Float.NaN) }
    var dragPreviewHeightPx by remember { mutableIntStateOf(0) }
    var dragHoveredOrigin by remember { mutableStateOf<QueueItemOrigin?>(null) }
    var dragCapturedByRoot by remember { mutableStateOf(false) }
    var manualScrollActive by remember { mutableStateOf(false) }
    var rootTopInWindow by remember { mutableFloatStateOf(0f) }
    var listTopInWindow by remember { mutableFloatStateOf(0f) }
    val reorderGestureState = LocalQueueReorderGestureState.current
    DisposableEffect(reorderGestureState, inlineMode) {
        onDispose { if (!inlineMode) reorderGestureState?.active = false }
    }
    val dragScope = rememberCoroutineScope()
    val secondFingerFling = ScrollableDefaults.flingBehavior()
    val density = LocalDensity.current
    val reorderHaptics = rememberMeowzixHaptics()
    val groupCounts = remember(state.items, state.currentIndex) {
        state.items.drop((state.currentIndex + 1).coerceAtLeast(0))
            .mapNotNull { it.groupId }.groupingBy { it }.eachCount()
    }
    var hasFocusedCurrent by remember { mutableStateOf(false) }
    // Use the authoritative queue immediately outside a drag. A cached display list can
    // otherwise show removed items until a deferred LaunchedEffect catches up.
    val queueItems = if (draggedQueueIndex == null && draggedGroupId == null &&
        !awaitingGroupCommit && !awaitingTrackCommit) filteredItems else displayItems
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
            awaitingTrackCommit = false
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
    LaunchedEffect(awaitingTrackCommit) {
        if (awaitingTrackCommit) {
            kotlinx.coroutines.delay(1_000L)
            if (awaitingTrackCommit) {
                awaitingTrackCommit = false
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

    /** The same viewport-space boundary drives hover feedback and final commit. */
    fun queueDropTargetOrigin(): QueueItemOrigin? {
        val occurrence = draggedOccurrenceId ?: return null
        val source = filteredItems.firstOrNull { it.value.occurrenceId == occurrence } ?: return null
        val visualSlot = displayItems.indexOfFirst { it.value.occurrenceId == occurrence }
        val slotOrigin = filteredItems.getOrNull(visualSlot)?.value?.origin ?: return null
        val previewSlots = queueSectionStartSlots(displayItems, state.currentIndex) { _, row ->
            queuePreviewOrigin(row.value, draggedOccurrenceId, dragHoveredOrigin)
        }
        val layout = listState.layoutInfo
        val nextSectionTop = previewSlots.generated?.let { slot ->
            layout.visibleItemsInfo.firstOrNull {
                it.key == "section-${slot}-${QueueItemOrigin.GENERATED}"
            }?.offset?.toFloat()
        }
        val cardHeight = dragPreviewHeightPx.takeIf { it > 0 }?.toFloat()
            ?: layout.visibleItemsInfo.firstOrNull {
                it.key == occurrence.toString()
            }?.size?.toFloat()
            ?: with(density) { 76.dp.toPx() }
        // The floating card follows the finger, which can grab the handle
        // off-center. Hit-test the VISIBLE card, not its hidden list row.
        val centerInList = if (!inlineMode && dragFingerYInRoot.isFinite()) {
            dragFingerYInRoot + rootTopInWindow - listTopInWindow
        } else dragCenterY
        // A section heading may leave LazyColumn's viewport during independent
        // scrolling. The visible non-dragged rows still reveal which lane the
        // finger is hovering over; do not lock the song to its previous lane.
        if (nextSectionTop == null) {
            val byKey = displayItems.asSequence()
                .filter { it.index > state.currentIndex && it.value.occurrenceId != occurrence }
                .associateBy { it.value.occurrenceId.toString() }
            val visibleRows = layout.visibleItemsInfo.mapNotNull { info ->
                byKey[info.key]?.let {
                    QueueVisibleLaneRow(info.offset.toFloat(), it.value.origin)
                }
            }
            return queueDropOriginWithoutHeading(
                sourceOrigin = source.value.origin,
                previousOrigin = dragHoveredOrigin,
                dragCenterY = centerInList,
                draggedCardHeightPx = cardHeight,
                visibleRows = visibleRows,
            )
        }
        return queueDropOrigin(
            slotOrigin, centerInList, nextSectionTop, cardHeight, source.value.origin,
        )
    }

    fun finishDrag(commit: Boolean) {
        val occurrence = draggedOccurrenceId
        // Use the last hit-tested lane: the heading may move during preview recomposition.
        val destinationOrigin = dragHoveredOrigin ?: queueDropTargetOrigin()
        val firstNextSlot = filteredItems.indexOfFirst {
            it.index > state.currentIndex && it.value.origin == QueueItemOrigin.GENERATED
        }
        val move = occurrence?.let {
            resolveQueueBoundaryMove(
                filteredItems, displayItems, it, destinationOrigin, firstNextSlot,
            )
        }
        if (!searchActive && commit && move != null &&
            move.fromIndex > state.currentIndex && move.toIndex > state.currentIndex) {
            awaitingTrackCommit = true
            onMove(move.fromIndex, move.toIndex, move.origin)
        } else {
            displayItems = filteredItems
        }
        draggedQueueIndex = null
        draggedOccurrenceId = null
        dragHoveredOrigin = null
        dragCenterY = Float.NaN
        dragFingerYInRoot = Float.NaN
        dragPreviewHeightPx = 0
        if (!inlineMode) reorderGestureState?.active = false
    }

    fun reorderAt(center: Float) {
        val draggedOccurrence = draggedOccurrenceId ?: return
        val from = displayItems.indexOfFirst { it.value.occurrenceId == draggedOccurrence }
        if (from < 0 || !center.isFinite()) return

        // Hit-test before the visual reorder can move the section heading.
        val lane = queueDropTargetOrigin()
        // Reordering still uses row midpoints; only the Queue/Next lane switch
        // uses the dragged card's top edge and the heading.
        val targets = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            val slot = displayItems.indexOfFirst { it.value.occurrenceId.toString() == info.key }
            val entry = displayItems.getOrNull(slot) ?: return@mapNotNull null
            if (entry.index <= state.currentIndex) return@mapNotNull null
            QueueDragTarget(entry.value.occurrenceId, slot, info.offset + info.size / 2f)
        }
        val targetId = crossedReorderTarget(targets, from, center,
            firstMovableSlot = if (filteredItems.firstOrNull()?.index == state.currentIndex) 1 else 0)
        val reordered = if (targetId != null) {
            reorderHaptics.perform(MeowzixHapticCue.Threshold)
            reorderPreviewItems(displayItems, draggedOccurrence, targetId)
        } else displayItems
        if (lane != null && lane != dragHoveredOrigin) {
            val sourceOrigin = filteredItems.firstOrNull {
                it.value.occurrenceId == draggedOccurrence
            }?.value?.origin
            if (sourceOrigin == QueueItemOrigin.GENERATED && lane == QueueItemOrigin.MANUAL) {
                reorderHaptics.perform(MeowzixHapticCue.Threshold)
            }
            dragHoveredOrigin = lane
        }
        displayItems = keepQueuePreviewSections(
            reordered, state.currentIndex, draggedOccurrence, dragHoveredOrigin,
        )
    }

    // Reorder only the visual snapshot. QueueRepository is called once when the handle is released.
    fun reorderGroupAt(center: Float) {
        val id = draggedGroupId ?: return
        val from = visibleItems.indexOfFirst { it.value.groupId == id }
        if (from < 0) return
        val infos = listState.layoutInfo.visibleItemsInfo
        val sourceKey = visibleItems[from].value.occurrenceId.toString()
        // The source may have left the LazyColumn viewport during independent scrolling.
        val target = infos.asSequence()
            .filter { it.key != sourceKey && visibleItems.any { row ->
                row.value.occurrenceId.toString() == it.key
            } }
            .minByOrNull { abs(it.offset + it.size / 2f - center) } ?: return
        val targetSlot = visibleItems.indexOfFirst {
            it.value.occurrenceId.toString() == target.key
        }
        val targetEntry = visibleItems.getOrNull(targetSlot) ?: return
        if (targetEntry.index <= state.currentIndex || targetEntry.value.groupId == id) return
        val targetCenter = target.offset + target.size / 2f
        val movingDown = targetSlot > from
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
    }

    fun finishGroupDrag(id: UUID, commit: Boolean) {
        val lastGroup = displayItems.indexOfLast { it.value.groupId == id }
        val nextItem = displayItems.getOrNull(lastGroup + 1)
        val target = nextItem?.index ?: state.items.size
        val changed = displayItems.indexOfFirst { it.value.groupId == id } !=
            filteredItems.indexOfFirst { it.value.groupId == id }
        draggedGroupId = null
        dragCenterY = Float.NaN
        dragFingerYInRoot = Float.NaN
        dragPreviewHeightPx = 0
        draggedGroupTarget = null
        if (!inlineMode) reorderGestureState?.active = false
        if (commit && changed) {
            awaitingGroupCommit = true
            onMoveGroup(id, target)
        } else displayItems = filteredItems
    }

    // Pointer ownership lives on the stable screen root, not a virtualized list item.
    // A grabbed row can leave the viewport while a second finger scrolls the Queue.
    val currentRootDrag by rememberUpdatedState(newValue = { centerY: Float ->
        dragCenterY = centerY
        if (draggedQueueIndex != null) reorderAt(centerY) else reorderGroupAt(centerY)
    })
    val currentRootStop by rememberUpdatedState(newValue = { commit: Boolean ->
        if (draggedQueueIndex != null) finishDrag(commit)
        else draggedGroupId?.let { finishGroupDrag(it, commit) }
    })

    // One frame-clock loop for auto-scroll and externally scrolled hit-testing.
    LaunchedEffect(draggedQueueIndex, draggedGroupId) {
        var previousFrame = 0L
        var lastScrollPosition: Pair<Int, Int>? = null
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
            var autoScrolled = false
            if (velocity != 0f && !manualScrollActive) {
                try {
                    autoScrolled = listState.scrollBy(velocity * dt) != 0f
                } catch (cancel: CancellationException) {
                    // A second finger uses a higher-priority scroll mutator.
                    // Do not terminate the reorder session when it takes priority.
                    if (!currentCoroutineContext().isActive) throw cancel
                }
            }
            val scrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            if (autoScrolled || (lastScrollPosition != null && lastScrollPosition != scrollPosition)) {
                if (draggedQueueIndex != null) reorderAt(y) else reorderGroupAt(y)
            }
            lastScrollPosition = scrollPosition
        }
    }

    Box(
        modifier.fillMaxSize()
            .onGloballyPositioned { rootTopInWindow = it.boundsInWindow().top }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var captured = false
                    var finished = false
                    var centerOffsetY = 0f
                    var secondFingerId: PointerId? = null
                    var secondFingerTargets: Channel<Float>? = null
                    var secondFingerTracker: VelocityTracker? = null
                    var manualScrollJob: Job? = null
                    var manualScrollGeneration = 0
                    var secondFingerReleaseVelocity = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!captured && change.pressed &&
                                (draggedQueueIndex != null || draggedGroupId != null) &&
                                dragCenterY.isFinite()) {
                                captured = true
                                dragCapturedByRoot = true
                                centerOffsetY = dragCenterY -
                                    (change.position.y + rootTopInWindow - listTopInWindow)
                            }
                            if (captured) {
                                if (change.pressed) {
                                    // Root-local pointer Y is independent of list padding,
                                    // section headers, and auto/second-finger scrolling.
                                    dragFingerYInRoot = change.position.y
                                    currentRootDrag(
                                        change.position.y + rootTopInWindow -
                                            listTopInWindow + centerOffsetY
                                    )
                                    // Only the grabbed pointer belongs to reorder.
                                    change.consume()
                                } else {
                                    finished = true
                                    // Finalize placement against the last settled scroll position.
                                    manualScrollJob?.cancel()
                                    manualScrollGeneration++
                                    manualScrollActive = false
                                    currentRootStop(true)
                                }

                                // Explicit two-finger scrolling works even if LazyColumn's
                                // own recognizer was canceled by the first (drag) pointer.
                                if (secondFingerId == null && change.pressed) {
                                    val second = event.changes.firstOrNull {
                                        it.id != down.id && it.pressed
                                    }
                                    if (second != null) {
                                        manualScrollJob?.cancel()
                                        secondFingerId = second.id
                                        manualScrollActive = true
                                        secondFingerReleaseVelocity = 0f
                                        val tracker = VelocityTracker().apply {
                                            addPosition(second.uptimeMillis, second.position)
                                        }
                                        secondFingerTracker = tracker
                                        val targets = Channel<Float>(Channel.CONFLATED)
                                        secondFingerTargets = targets
                                        val initialY = second.position.y
                                        val generation = ++manualScrollGeneration
                                        manualScrollJob = dragScope.launch {
                                            try {
                                                // Prevent the LazyColumn recognizer from canceling
                                                // the independent finger's scrolling mid-gesture.
                                                listState.scroll(MutatePriority.PreventUserInput) {
                                                    var previousY = initialY
                                                    for (y in targets) {
                                                        scrollBy(previousY - y)
                                                        previousY = y
                                                    }
                                                    val fling = secondFingerReleaseVelocity
                                                    if (abs(fling) > 350f) {
                                                        val scrollScope = this
                                                        with(secondFingerFling) {
                                                            scrollScope.performFling(fling)
                                                        }
                                                    }
                                                }
                                            } catch (_: CancellationException) {
                                                // A new gesture or released handle owns the list.
                                            } finally {
                                                if (generation == manualScrollGeneration) {
                                                    manualScrollActive = false
                                                }
                                            }
                                        }
                                    }
                                }
                                val secondChange = event.changes.firstOrNull {
                                    it.id == secondFingerId
                                }
                                if (secondChange != null) {
                                    secondFingerTracker?.addPosition(
                                        secondChange.uptimeMillis, secondChange.position,
                                    )
                                    if (secondChange.pressed) {
                                        secondFingerTargets?.trySend(secondChange.position.y)
                                        secondChange.consume()
                                    } else {
                                        secondFingerReleaseVelocity =
                                            -(secondFingerTracker?.calculateVelocity()?.y ?: 0f)
                                                .coerceIn(-12_000f, 12_000f)
                                        secondFingerTargets?.trySend(secondChange.position.y)
                                        secondFingerTargets?.close()
                                        secondFingerTargets = null
                                        secondFingerTracker = null
                                        secondFingerId = null
                                        secondChange.consume()
                                        // Keep manualScrollActive until fling settles.
                                    }
                                }
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        secondFingerTargets?.close()
                        manualScrollJob?.cancel()
                        manualScrollGeneration++
                        manualScrollActive = false
                        if (captured && !finished) currentRootStop(false)
                        dragCapturedByRoot = false
                    }
                }
            },
    ) {
        GlassLazyColumn(
            modifier = Modifier.fillMaxSize()
                .then(if (inlineMode) Modifier else Modifier.statusBarsPadding())
                .onGloballyPositioned { listTopInWindow = it.boundsInWindow().top }
                .hazeSource(feedbackHazeState),
            state = listState,
            contentPadding = if (inlineMode) PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                             else PaddingValues(start = 12.dp, end = 12.dp, top = 100.dp, bottom = dockAwareBottomPadding(206.dp)),
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
                val previewLanes = draggedQueueIndex != null && draggedGroupId == null
                val sectionStarts = queueSectionStartSlots(visibleItems, state.currentIndex) { _, row ->
                    if (previewLanes) queuePreviewOrigin(
                        row.value, draggedOccurrenceId, dragHoveredOrigin,
                    ) else row.value.origin
                }
                visibleItems.forEachIndexed { displayPosition, entry ->
                    // Preserve each existing manual song's section during a track drag.
                    // The Next heading moves down as a generated song becomes manual.
                    val origin = if (previewLanes) queuePreviewOrigin(
                        entry.value, draggedOccurrenceId, dragHoveredOrigin,
                    ) else entry.value.origin
                    val currentHeader = !searchActive && entry.index == state.currentIndex
                    val manualHeader = !searchActive && displayPosition == sectionStarts.manual
                    val generatedHeader = !searchActive && displayPosition == sectionStarts.generated
                    if (currentHeader || manualHeader || generatedHeader) {
                        item(key = "section-${displayPosition}-${origin}") {
                            Text(
                                text = when {
                                    currentHeader -> "Now Playing"
                                    manualHeader -> "Queue"
                                    else -> "Next in the queue"
                                },
                                modifier = Modifier.fillMaxWidth()
                                    .padding(start = 10.dp, top = if (inlineMode) 10.dp else 18.dp,
                                             bottom = if (inlineMode) 6.dp else 10.dp),
                                style = if (inlineMode) MaterialTheme.typography.labelLarge
                                        else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    item(key = entry.value.occurrenceId.toString()) {
                    val item = entry.value
                    val queueIndex = entry.index
                    val floatingControls = LocalFloatingControlsTouchBounds.current
                    var rowBottomInWindow by remember(item.occurrenceId) { mutableFloatStateOf(Float.NaN) }
                    val touchBlocked = !inlineMode && floatingControls != null &&
                        queueRowBlockedByFloatingControls(
                            rowBottomInWindow,
                            minOf(floatingControls.dockTopY, floatingControls.miniPlayerTopY),
                            with(density) { 16.dp.toPx() },
                        )
                    val isDragging = draggedOccurrenceId == item.occurrenceId
                    val groupId = item.groupId
                    val group = groupId?.let(state.groups::get)
                    val isGroupHeader = !searchActive && group != null &&
                        firstGroupPositions[groupId] == queueIndex
                    val isGroupDragging = groupId != null && groupId == draggedGroupId
                    val lastGroupMember = groupId != null &&
                        (isGroupDragging || state.items.getOrNull(queueIndex + 1)?.groupId != groupId)
                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging || isGroupDragging) 20f else 0f)
                            .then(
                                // Never animate the grabbed row back to its previous layout slot.
                                if (isDragging || isGroupDragging) Modifier
                                else Modifier.animateItem(
                                    fadeInSpec = tween(durationMillis = 180),
                                    placementSpec = tween(durationMillis = 170),
                                    fadeOutSpec = tween(durationMillis = 180),
                                )
                            )
                            .graphicsLayer {
                                // Keep the layout slot stable; the visible lifted item
                                // is rendered by the non-virtualized Queue root.
                                alpha = if (isDragging || isGroupDragging) 0f else 1f
                            }
                            .onGloballyPositioned { coordinates ->
                                rowBottomInWindow = coordinates.boundsInWindow().bottom
                            },
                    ) {
                        Column(Modifier.animateContentSize()) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .padding(bottom = if (groupId != null && !lastGroupMember) 0.dp else TRACK_ROW_SPACING)
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
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
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
                                reorderEnabled = !selectionMode && (!touchBlocked || isGroupDragging) &&
                                    !searchActive && draggedQueueIndex == null,
                                interactionEnabled = !touchBlocked || isGroupDragging,
                                onStart = {
                                    dragCapturedByRoot = false
                                    if (!inlineMode) reorderGestureState?.active = true
                                    displayItems = filteredItems
                                    awaitingGroupCommit = false
                                    draggedGroupId = group.id
                                    draggedGroupTarget = queueIndex
                                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        it.key == item.occurrenceId.toString()
                                    }
                                    dragCenterY = info?.let { it.offset + it.size / 2f } ?: Float.NaN
                                },
                                onDrag = { delta ->
                                    if (dragCapturedByRoot) return@GroupQueueHeader
                                    if (!dragCenterY.isFinite()) {
                                        val info = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == item.occurrenceId.toString() }
                                        dragCenterY = info?.let { it.offset + it.size / 2f } ?: Float.NaN
                                    }
                                    if (dragCenterY.isFinite()) {
                                        dragCenterY += delta
                                        reorderGroupAt(dragCenterY)
                                    }
                                },
                                onStop = { commit ->
                                    if (!dragCapturedByRoot && draggedGroupId == group.id)
                                        finishGroupDrag(group.id, commit)
                                },
                                onRemove = { onRemoveGroup(group.id) },
                                onMoveToFront = { onMoveGroup(group.id, state.currentIndex + 1) },
                                showOverflowActions = !inlineMode && !selectionMode,
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
                            reorderEnabled = !selectionMode && !searchActive && draggedGroupId == null &&
                                queueIndex != state.currentIndex,
                            protectDragHandleFromDock = !inlineMode,
                            interactionEnabled = !touchBlocked || isDragging,
                            onPlay = { if (!touchBlocked && state.items.getOrNull(queueIndex) == item) onPlay(queueIndex) },
                            selectionMode = selectionMode,
                            selected = item.occurrenceId in selectedOccurrences,
                            onSelect = { toggleSelection(item.occurrenceId) },
                            onLongSelect = {
                                if (!touchBlocked && !inlineMode) {
                                    selectionMode = true
                                    selectedOccurrences = selectedOccurrences + item.occurrenceId
                                }
                            },
                            onDragStart = { fingerYInWindow ->
                                if (!searchActive && draggedGroupId == null) {
                                    dragCapturedByRoot = false
                                    dragPreviewHeightPx = 0
                                    dragHoveredOrigin = null
                                    // Use the exact handle touch position for the
                                    // first preview frame; root pointer events update it.
                                    if (!inlineMode) {
                                        dragFingerYInRoot = if (fingerYInWindow.isFinite())
                                            fingerYInWindow - rootTopInWindow else Float.NaN
                                    }
                                    if (!inlineMode) reorderGestureState?.active = true
                                    displayItems = filteredItems
                                    awaitingTrackCommit = false
                                    draggedQueueIndex = queueIndex
                                    draggedOccurrenceId = item.occurrenceId
                                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        it.key == item.occurrenceId.toString()
                                    }
                                    dragCenterY = info?.let { it.offset + it.size / 2f } ?: Float.NaN
                                }
                            },
                            onDrag = { deltaY ->
                                if (dragCapturedByRoot || searchActive) return@SwipeableQueueItem
                                if (!inlineMode && dragFingerYInRoot.isFinite()) dragFingerYInRoot += deltaY
                                val draggedKey = draggedOccurrenceId?.toString()
                                    ?: return@SwipeableQueueItem
                                if (!dragCenterY.isFinite()) {
                                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        it.key == draggedKey
                                    } ?: return@SwipeableQueueItem
                                    dragCenterY = info.offset + info.size / 2f
                                }
                                dragCenterY += deltaY
                                reorderAt(dragCenterY)
                            },
                            onDragEnd = {
                                if (!dragCapturedByRoot && draggedQueueIndex != null)
                                    finishDrag(commit = true)
                            },
                            onDragCancel = {
                                if (!dragCapturedByRoot && draggedQueueIndex != null)
                                    finishDrag(commit = false)
                            },
                            // A gesture can finish after playback advanced or a queue
                            // mutation shifted this row's old index. Its occurrence
                            // identity is stable, even for duplicate copies of a song.
                            onRemove = { onRemove(item.occurrenceId) },
                            onPlayNext = { onPlayNext(item.id) },
                            onAddToQueue = { onAddToQueue(item.id) },
                            onPinOffline = { onPinOffline(item.id) },
                            onRemoveTrack = { offline, library -> onRemoveTrack(item.id, offline, library) },
                            availability = aux.statuses[item.id]?.availability ?: LibraryTrackAvailability.UNAVAILABLE,
                            download = aux.downloads[item.id],
                            favorite = aux.statuses[item.id]?.favorite == true,
                            playlists = aux.playlists,
                            onFavorite = { onFavorite(item.id) },
                            onAddToPlaylist = { playlistId -> onAddToPlaylist(item.id, playlistId) },
                            onGoToArtist = { artist -> onGoToArtist(artist) },
                            showOverflowActions = !inlineMode && !selectionMode,
                        )
                        }
                        }
                        }
                    }
                    }
                }
            }
        }

        // A stable overlay survives list virtualization and independent scrolling.
        val draggedTrack = draggedOccurrenceId?.let { occurrence ->
            displayItems.firstOrNull { it.value.occurrenceId == occurrence }?.value
        }
        val draggedGroup = draggedGroupId?.let(state.groups::get)
        val promoteToQueue = draggedTrack?.origin == QueueItemOrigin.GENERATED &&
            (dragHoveredOrigin ?: queueDropTargetOrigin()) == QueueItemOrigin.MANUAL
        if (dragCenterY.isFinite() && (draggedTrack != null || draggedGroup != null)) {
            val useFingerPosition = !inlineMode && draggedTrack != null &&
                dragFingerYInRoot.isFinite()
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = if (inlineMode) 2.dp else 12.dp)
                    .offset {
                        val centerY = if (useFingerPosition) dragFingerYInRoot else
                            listTopInWindow - rootTopInWindow + dragCenterY
                        val halfHeight = if (useFingerPosition && dragPreviewHeightPx > 0)
                            dragPreviewHeightPx / 2f else with(density) { 38.dp.toPx() }
                        IntOffset(0, (centerY - halfHeight).roundToInt())
                    }
                    .onSizeChanged { dragPreviewHeightPx = it.height }
                    .zIndex(30f)
                    .testTag("queue-drag-preview"),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    shadowElevation = 10.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    ),
                ) {
                    Row(
                        Modifier.padding(
                            horizontal = TRACK_ROW_HORIZONTAL_PADDING,
                            vertical = TRACK_ROW_VERTICAL_PADDING,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (draggedGroup != null) {
                            Icon(
                                if (draggedGroup.kind == QueueGroupKind.ALBUM)
                                    Icons.Rounded.Album else Icons.Rounded.Person,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                draggedGroup.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (draggedTrack != null) {
                            TrackArtwork(
                                artworkRef = draggedTrack.artworkRef,
                                description = draggedTrack.title,
                                size = 52.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    draggedTrack.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (promoteToQueue) "Move to Queue"
                                    else draggedTrack.artist ?: "Unknown artist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (promoteToQueue) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(
                            if (promoteToQueue) Icons.Rounded.QueueMusic else Icons.Rounded.DragHandle,
                            contentDescription = if (promoteToQueue) "Release to move to Queue" else null,
                        )
                    }
                }
            }
        }

        if (!inlineMode) Crossfade(
            targetState = selectionMode,
            animationSpec = tween(240),
            modifier = Modifier.align(Alignment.TopCenter),
            label = "queue-selection-header",
        ) { selecting ->
            MeowzixStickyHeader(
                title = if (selecting) "Selected ${selectedOccurrences.size}" else "Queue",
                subtitle = if (selecting) "" else when {
                    state.items.isEmpty() -> "Nothing queued"
                    searchActive -> "${filteredItems.size} of ${state.items.size} tracks"
                    else -> "${state.items.size} tracks"
                },
                hazeState = feedbackHazeState,
                leadingActions = if (selecting) {
                    { MeowzixHeaderAction("Cancel selection", Icons.Rounded.Close, ::cancelSelection) }
                } else null,
            ) {
                if (selecting) {
                    MeowzixHeaderAction("Select all", Icons.Rounded.SelectAll, {
                        val ids = filteredItems.map { it.value.occurrenceId }.toSet()
                        selectedOccurrences = if (ids.isNotEmpty() && ids.all(selectedOccurrences::contains))
                            selectedOccurrences - ids else selectedOccurrences + ids
                        if (selectedOccurrences.isEmpty()) selectionMode = false
                    })
                    LibrarySelectionMoreMenu(selectionMoreActions, ::handleSelectionAction)
                } else {
                    if (filteredItems.isNotEmpty()) {
                        MeowzixHeaderAction("Select tracks", Icons.Rounded.Checklist, { selectionMode = true })
                    }
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
            }
        }
        if (showSelectionPlaylists) AlertDialog(
            onDismissRequest = { showSelectionPlaylists = false },
            title = { Text("Add ${selectedItems.size} queued tracks to playlist") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val destinations = aux.playlists.filter { !it.telegramManaged && !it.localManaged }
                    if (destinations.isEmpty()) Text("Create a playlist first.")
                    destinations.forEach { playlist ->
                        Text(playlist.title, modifier = Modifier.fillMaxWidth()
                            .clickable {
                                onBulkAddToPlaylist(selectedItems, playlist.id)
                                cancelSelection()
                            }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectionPlaylists = false }) { Text("Cancel") }
            },
        )


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
    interactionEnabled: Boolean = true,
    showOverflowActions: Boolean = true,
) {
    var menuExpanded by remember(group.id) { mutableStateOf(false) }
    LaunchedEffect(interactionEnabled) { if (!interactionEnabled) menuExpanded = false }
    var swipeX by remember(group.id) { mutableFloatStateOf(0f) }
    var horizontalDrag by remember(group.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val actionThreshold = with(density) { 75.dp.toPx() }
    val removeThreshold = with(density) { 150.dp.toPx() }
    val haptics = rememberMeowzixHaptics()
    val maximumSwipe = with(density) { 300.dp.toPx() }
    val animatedSwipeX by animateFloatAsState(
        targetValue = swipeX,
        animationSpec = if (horizontalDrag) snap() else tween(190),
        label = "group-swipe",
    )
    val swipingRight = animatedSwipeX > 0f
    Box(Modifier.fillMaxWidth().graphicsLayer { translationY = offsetY }) {
        StagedSwipeReveal(
            offsetPx = animatedSwipeX,
            primaryLabel = if (swipingRight) "Play next" else "Add to queue",
            primaryIcon = if (swipingRight) Icons.Rounded.PlaylistPlay else Icons.Rounded.PlaylistAdd,
            secondaryLabel = "Remove group",
            secondaryThresholdPx = removeThreshold,
        )

        Surface(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedSwipeX
                    shape = RoundedCornerShape(18.dp)
                    clip = true
                }
                .pointerInput(group.id, actionThreshold, removeThreshold, interactionEnabled) {
                    if (interactionEnabled) detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = true },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            val old = abs(swipeX)
                            swipeX = (swipeX + delta).coerceIn(-maximumSwipe, maximumSwipe)
                            if ((old < actionThreshold && abs(swipeX) >= actionThreshold) ||
                                (old < removeThreshold && abs(swipeX) >= removeThreshold)) {
                                haptics.perform(MeowzixHapticCue.Threshold)
                            }
                        },
                        onDragEnd = {
                            val released = swipeX
                            horizontalDrag = false
                            swipeX = 0f // settle with the original 190ms tween
                            when (queueSwipeActionForOffset(released, actionThreshold, removeThreshold)) {
                                QueueSwipeAction.REMOVE -> onRemove()
                                QueueSwipeAction.PLAY_NEXT -> onMoveToFront()
                                QueueSwipeAction.ADD_TO_QUEUE -> onMoveToEnd()
                                QueueSwipeAction.NONE -> Unit
                            }
                        },
                        onDragCancel = { horizontalDrag = false; swipeX = 0f },
                    )
                },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TRACK_CARD_ALPHA),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = TRACK_ROW_HORIZONTAL_PADDING, vertical = if (compact) 8.dp else 10.dp)
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
                    modifier = Modifier.size(48.dp).pointerInput(group.id, reorderEnabled) {
                        if (reorderEnabled) detectDragGestures(
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
                if (showOverflowActions) Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = interactionEnabled, modifier = Modifier.size(38.dp)) {
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
    protectDragHandleFromDock: Boolean,
    onPlay: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    onLongSelect: () -> Unit = {},
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    onRemoveTrack: (Boolean, Boolean) -> Unit,
    availability: LibraryTrackAvailability,
    download: OfflineDownload?,
    favorite: Boolean,
    playlists: List<PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
    onGoToArtist: (String) -> Unit,
    interactionEnabled: Boolean = true,
    showOverflowActions: Boolean = true,
) {
    val recommendationActions = LocalLibraryRecommendationActions.current
    val trackToolsActions = LocalLibraryTrackToolsActions.current
    val forwardAction = LocalTrackForwardAction.current
    val chatActions = LocalTrackChatLinkActions.current
    var menuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(interactionEnabled) { if (!interactionEnabled) menuExpanded = false }
    var chatLink by remember(item.id) { mutableStateOf<String?>(null) }
    var chatOrigin by remember(item.id) {
        mutableStateOf<dev.behradhz.meowzix.domain.telegram.TelegramTrackOrigin?>(null)
    }
    var chatOriginLoaded by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(menuExpanded, item.id) {
        chatLink = null
        chatOrigin = null
        chatOriginLoaded = false
        if (menuExpanded) {
            chatOrigin = try {
                chatActions?.originLookup?.invoke(item.id)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
            chatOriginLoaded = true
            if (chatOrigin != null) {
                chatLink = try {
                    chatActions?.lookup?.invoke(item.id)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    null
                }
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
    LaunchedEffect(selectionMode) {
        if (selectionMode) { swipeOffsetX = 0f; swipeStage = 0; horizontalDragActive = false }
    }
    var handleTopInWindow by remember(item.occurrenceId) { mutableFloatStateOf(Float.NaN) }
    var handleBottomInWindow by remember(item.occurrenceId) { mutableFloatStateOf(Float.NaN) }
    val floatingTouchBounds = LocalFloatingControlsTouchBounds.current
    val effectiveReorderEnabled = if (!protectDragHandleFromDock || floatingTouchBounds == null)
        reorderEnabled
    else queueDragHandleEnabled(
        allowed = reorderEnabled,
        isDragging = isDragging,
        handleBottomInWindow = handleBottomInWindow,
        controlsTopInWindow = minOf(
            floatingTouchBounds.dockTopY, floatingTouchBounds.miniPlayerTopY,
        ),
        guardPx = with(LocalDensity.current) { 16.dp.toPx() },
    )
    val draggingNow by rememberUpdatedState(isDragging)
    val visualOffsetX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = if (horizontalDragActive) snap() else tween(190),
        label = "queue-two-stage-swipe",
    )
    val swipingRight = visualOffsetX >= 0f


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY },
    ) {
        StagedSwipeReveal(
            offsetPx = visualOffsetX,
            primaryLabel = if (swipingRight) "Play next" else "Add to queue",
            primaryIcon = if (swipingRight) Icons.Rounded.PlaylistPlay else Icons.Rounded.PlaylistAdd,
            secondaryLabel = "Remove",
            secondaryThresholdPx = removeThreshold,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("queue-item-${item.occurrenceId}")
                .graphicsLayer {
                    translationX = visualOffsetX
                    shape = RoundedCornerShape(18.dp)
                    clip = true
                }
                .pointerInput(item.occurrenceId, actionThreshold, removeThreshold, interactionEnabled, selectionMode) {
                    if (!selectionMode && !draggingNow && interactionEnabled) {
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
                                swipeOffsetX = 0f // always settle, including actions that keep the row
                                when (queueSwipeActionForOffset(
                                    releasedOffset, actionThreshold, removeThreshold,
                                )) {
                                    QueueSwipeAction.REMOVE -> onRemove()
                                    QueueSwipeAction.PLAY_NEXT -> onPlayNext()
                                    QueueSwipeAction.ADD_TO_QUEUE -> onAddToQueue()
                                    QueueSwipeAction.NONE -> Unit
                                }
                            },
                        )
                    }
                }
                .combinedClickable(
                    enabled = interactionEnabled,
                    onClick = { if (selectionMode) onSelect() else onPlay() },
                    onLongClick = if (showOverflowActions && !selectionMode) onLongSelect else null,
                ),
            shape = RoundedCornerShape(18.dp),
            color = (if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = TRACK_CARD_ALPHA),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = if (isDragging) 6.dp else 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = TRACK_ROW_HORIZONTAL_PADDING, vertical = TRACK_ROW_VERTICAL_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadableTrackArtwork(
                    artworkRef = item.artworkRef,
                    description = item.title,
                    size = 52.dp,
                    isOffline = availability == LibraryTrackAvailability.OFFLINE,
                    download = download,
                    onDownload = { if (interactionEnabled) onPinOffline() },
                )
                Spacer(Modifier.size(10.dp))
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
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onSelect() },
                        modifier = Modifier.testTag("queue-select-${item.occurrenceId}"),
                    )
                } else Box(
                    modifier = Modifier
                        .size(48.dp)
                        .onGloballyPositioned {
                            val windowBounds = it.boundsInWindow()
                            handleTopInWindow = windowBounds.top
                            handleBottomInWindow = windowBounds.bottom
                        }
                        .pointerInput(item.occurrenceId, effectiveReorderEnabled) {
                            if (effectiveReorderEnabled) {
                                detectDragGestures(
                                    onDragStart = { localTouch ->
                                        haptics.perform(MeowzixHapticCue.Threshold)
                                        onDragStart(
                                            if (handleTopInWindow.isFinite())
                                                handleTopInWindow + localTouch.y else Float.NaN
                                        )
                                    },
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
                        contentDescription = if (effectiveReorderEnabled) "Drag to reorder"
                            else "Reordering unavailable in covered area or while filtering",
                        tint = (if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            .copy(alpha = if (effectiveReorderEnabled) 1f else 0.28f),
                    )
                }
                if (showOverflowActions) Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = interactionEnabled, modifier = Modifier.size(38.dp)) {
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
                            viewInChatOrigin = chatOrigin,
                            isLocalSource = chatOriginLoaded && chatOrigin == null,
                            onGoToArtist = { item.artist?.let(onGoToArtist) },
                            onAddToPlaylist = onAddToPlaylist,
                            onContinueVibe = { recommendationActions?.continueVibe?.invoke(item.id) },
                            onEditMetadata = { trackToolsActions?.editMetadata?.invoke(item.id) },
                            onManageDuplicates = { trackToolsActions?.manageDuplicates?.invoke(item.id) },
                            onWhy = { recommendationActions?.why?.invoke(item.id) },
                            onRemoveFromLocations = onRemoveTrack,
                            canRemoveOffline = download != null &&
                                download.status != dev.behradhz.meowzix.domain.downloads.DownloadStatus.CANCELED,
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
