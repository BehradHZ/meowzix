package dev.behradhz.meowzix.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.QueueActionFeedback
import dev.behradhz.meowzix.domain.playback.QueueActionKind
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.domain.playback.resolveCurrentQueueIndex
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.ui.components.FloatingChromeGlassSurface
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.floatingChromeFrame
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

private val CollapsedPlayerHeight = 72.dp
private val CollapsedPlayerHorizontalInset = 14.dp
private val CollapsedPlayerDockClearance = 90.dp
private val PlayerSettleDistance = 72.dp
private const val MINI_CARD_COMMIT_THRESHOLD = 0.35f
private const val PLAYER_AXIS_DOMINANCE = 1.25f
private val MiniCardGap = 8.dp
private val MiniCardCornerRadius = 28.dp

/**
 * Swipe-to-collapse must never claim a gesture that starts on the More actions
 * trigger. These bounds are measured on screen, so the guard remains correct
 * across device sizes, insets, and the player's morph animation.
 */
internal fun shouldIgnorePlayerSwipeFromMoreActions(
    downOnScreen: Offset,
    moreButtonBounds: Rect?,
    marginPx: Float,
): Boolean {
    val bounds = moreButtonBounds ?: return false
    return downOnScreen.x >= bounds.left - marginPx &&
        downOnScreen.x <= bounds.right + marginPx &&
        downOnScreen.y >= bounds.top - marginPx &&
        downOnScreen.y <= bounds.bottom + marginPx
}


internal enum class PlayerDragAxis { HORIZONTAL, VERTICAL }

/**
 * A gesture must establish a dominant axis before it can affect either player
 * expansion or the mini-player queue. Ambiguous diagonals remain unclaimed.
 */
internal fun playerDragAxis(totalDrag: Offset, touchSlop: Float): PlayerDragAxis? {
    val horizontal = abs(totalDrag.x)
    val vertical = abs(totalDrag.y)
    if (maxOf(horizontal, vertical) < touchSlop) return null
    return when {
        horizontal > vertical * PLAYER_AXIS_DOMINANCE -> PlayerDragAxis.HORIZONTAL
        vertical > horizontal * PLAYER_AXIS_DOMINANCE -> PlayerDragAxis.VERTICAL
        else -> null
    }
}

/** A vertical/diagonal expansion gesture cannot be interpreted as Next/Previous. */
internal fun shouldCommitMiniTrackSwipe(totalDrag: Offset, pagerWidthPx: Float): Boolean =
    playerDragAxis(totalDrag, 0f) == PlayerDragAxis.HORIZONTAL &&
        abs(totalDrag.x) / pagerWidthPx.coerceAtLeast(1f) >= MINI_CARD_COMMIT_THRESHOLD

internal enum class MiniCardDirection { PREVIOUS, NEXT }

/**
 * The foreground card's physical offset in pager widths.
 * Next slides the incoming card over the stationary current track from the right.
 * Previous slides the current card right, uncovering the stationary previous track.
 */
internal fun miniPlayerForegroundOffsetFraction(
    direction: MiniCardDirection,
    progress: Float,
): Float {
    val fraction = progress.coerceIn(0f, 1f)
    return when (direction) {
        MiniCardDirection.NEXT -> 1f - fraction
        MiniCardDirection.PREVIOUS -> fraction
    }
}

/**
 * Keep the neighboring card separated by a real gap while it slides. The outgoing card
 * is masked before the incoming translucent card reaches it, so there is no text or
 * artwork bleeding through the glass. Clip every moving layer to the same round shape.
 */
@Composable
internal fun MiniPlayerStackLayer(
    direction: MiniCardDirection,
    progress: Float,
    modifier: Modifier = Modifier,
    underlay: @Composable () -> Unit,
    foreground: @Composable () -> Unit,
) {
    val foregroundX = miniPlayerForegroundOffsetFraction(direction, progress)
    val gapPx = with(LocalDensity.current) { MiniCardGap.toPx() }
    val shape = RoundedCornerShape(MiniCardCornerRadius)
    Box(modifier = modifier.clip(shape)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .drawWithContent {
                    val offsetPx = (size.width + gapPx) * foregroundX
                    clipRect(right = (offsetPx - gapPx).coerceIn(0f, size.width)) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            underlay()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = (size.width + gapPx) * foregroundX }
                .clip(shape),
        ) {
            foreground()
        }
    }
}

private data class MiniTrackCard(
    val id: java.util.UUID,
    val title: String,
    val artist: String?,
    val artworkRef: String?,
)

@Stable
internal class MorphingPlayerState internal constructor(initiallyExpanded: Boolean = false) {
    var targetExpanded by mutableStateOf(initiallyExpanded)
        private set

    // Keep background navigation blocked while the player actually covers it.
    private var overlayVisible by mutableStateOf(initiallyExpanded)
    val blocksBackgroundBack: Boolean
        get() = targetExpanded || overlayVisible

    // Every request must settle the current fraction, even when targetExpanded
    // already equals the request. Otherwise a canceled drag can trap Back forever.
    var settleRequestId by mutableIntStateOf(0)
        private set
    var finishCollapseImmediately by mutableStateOf(false)
        private set

    internal fun updateOverlayVisibility(visible: Boolean) {
        overlayVisible = visible
    }

    fun expand() {
        finishCollapseImmediately = false
        targetExpanded = true
        settleRequestId++
    }

    fun collapse() {
        // A second Back during a stalled collapse snaps the overlay closed.
        if (!targetExpanded && overlayVisible) finishCollapseImmediately = true
        targetExpanded = false
        settleRequestId++
    }
}

@Composable
internal fun rememberMorphingPlayerState(): MorphingPlayerState =
    remember { MorphingPlayerState() }

/**
 * Installed only while the player covers the background, after the NavHost's
 * Back callback has been registered. This gives the player priority over route
 * navigation without stealing Back from its own lyrics/inline-queue subpanels.
 */
@Composable
internal fun MorphingPlayerBackHandler(morphState: MorphingPlayerState) {
    if (morphState.blocksBackgroundBack) {
        BackHandler { morphState.collapse() }
    }
}

@Composable
internal fun MorphingPlayerOverlay(
    hazeState: HazeState,
    state: PlaybackState,
    viewModel: NowPlayingViewModel,
    morphState: MorphingPlayerState,
    dockCompact: Boolean = false,
    onMiniPlayerTopInWindow: (Float) -> Unit = {},
    onOpenQueue: () -> Unit,
    onGoToArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    // NavHost and background destinations are composed before this overlay.
    // Register only when expanded/partially visible so Back collapses the player
    // before popping Settings, switching tabs, or returning to Home.
    MorphingPlayerBackHandler(morphState)
    DisposableEffect(Unit) {
        onDispose { onMiniPlayerTopInWindow(Float.POSITIVE_INFINITY) }
    }
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val reduceMotion = appearance.reduceMotion
    val density = LocalDensity.current
    var expansionFraction by remember(track.id) {
        mutableFloatStateOf(if (morphState.targetExpanded) 1f else 0f)
    }
    var lyricsExpanded by remember(track.id) { mutableStateOf(false) }
    var inlineQueueExpanded by remember { mutableStateOf(false) }
    var moreActionsMenuOpen by remember(track.id) { mutableStateOf(false) }
    var moreButtonBounds by remember(track.id) { mutableStateOf<Rect?>(null) }
    var playerSurfaceTopLeft by remember { mutableStateOf(Offset.Zero) }
    var overlayTopLeft by remember { mutableStateOf(Offset.Zero) }
    var queueFeedback by remember { mutableStateOf<QueueActionFeedback?>(null) }
    var queueFeedbackVisible by remember { mutableStateOf(false) }

    suspend fun animateTo(target: Float, durationMillis: Int = 360) {
        if (reduceMotion) {
            expansionFraction = target
            return
        }
        animate(
            initialValue = expansionFraction,
            targetValue = target,
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        ) { value, _ ->
            expansionFraction = value
        }
    }

    LaunchedEffect(morphState.targetExpanded, morphState.settleRequestId, track.id) {
        if (!morphState.targetExpanded) inlineQueueExpanded = false
        if (morphState.finishCollapseImmediately && !morphState.targetExpanded) {
            expansionFraction = 0f
        } else {
            animateTo(if (morphState.targetExpanded) 1f else 0f)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.queueActionFeedback.collectLatest { event ->
            queueFeedback = event
            queueFeedbackVisible = true
            delay(if (event.kind == QueueActionKind.SOURCE_DELETED) 5_000L else 1_550L)
            queueFeedbackVisible = false
            delay(260)
            if (queueFeedback == event) queueFeedback = null
        }
    }

    // The root app owns Back dispatch; this overlay only publishes visual visibility.
    SideEffect { morphState.updateOverlayVisibility(expansionFraction > 0.02f) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            val origin = coordinates.positionOnScreen()
            if (origin != Offset.Unspecified) overlayTopLeft = origin
        },
    ) {
        val navigationBottom = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        // Track the dock on the same reversible keyframe timeline. Artwork and
        // text crossfade while the card changes size, rather than swapping at
        // the first frame of a scroll event.
        val dockMorph by animateFloatAsState(
            targetValue = if (dockCompact) 1f else 0f,
            animationSpec = dockMorphSpec(dockCompact, reduceMotion),
            label = "mini-dock-morph",
        )
        val compactHeight = lerp(CollapsedPlayerHeight, CompactDockButtonDp.dp, dockMorph)
        val miniInset = lerp(
            CollapsedPlayerHorizontalInset,
            CompactMiniPlayerInsetDp.dp,
            dockMorph,
        )
        val dockClearance = lerp(CollapsedPlayerDockClearance, 18.dp, dockMorph)
        val collapsedBottom = navigationBottom + dockClearance
        val horizontalInset = lerp(miniInset, 0.dp, expansionFraction)
        val bottomInset = lerp(collapsedBottom, 0.dp, expansionFraction)
        val playerHeight = lerp(compactHeight, maxHeight, expansionFraction)
        // Anchor feedback to the ACTUAL mini-player rectangle, not a hard-coded dock size.
        // Global coordinates share the same screen origin; converting to this layout's
        // bottom inset follows both dock morph and drag-to-expand on every layout frame.
        val actualPlayerTop = playerSurfaceTopLeft.y - overlayTopLeft.y
        val feedbackClearance = if (actualPlayerTop > 0f) {
            with(density) { (maxHeight.toPx() - actualPlayerTop).toDp() } + 10.dp
        } else {
            collapsedBottom + compactHeight + 10.dp
        }
        val cornerRadius = lerp(28.dp, 0.dp, expansionFraction)
        val playerShape = RoundedCornerShape(cornerRadius)
        val travelPx = with(density) {
            (maxHeight - compactHeight).toPx().coerceAtLeast(1f)
        }
        val settleDistancePx = with(density) { PlayerSettleDistance.toPx() }
        val moreButtonSafeMarginPx = with(density) { 10.dp.toPx() }

        FloatingChromeGlassSurface(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = horizontalInset, end = horizontalInset, bottom = bottomInset)
                .height(playerHeight)
                .floatingChromeFrame(playerShape)
                .onGloballyPositioned { coordinates ->
                    val topLeft = coordinates.positionOnScreen()
                    if (topLeft != Offset.Unspecified) playerSurfaceTopLeft = topLeft
                    onMiniPlayerTopInWindow(coordinates.boundsInWindow().top)
                }
                 .pointerInput(track.id, travelPx, lyricsExpanded, inlineQueueExpanded, moreActionsMenuOpen) {
                    // Keep both the lyrics scroller and the open actions menu independent
                    // of the player-wide swipe-to-collapse recognizer.
                    if (lyricsExpanded || inlineQueueExpanded || moreActionsMenuOpen) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val downOnScreen = playerSurfaceTopLeft + down.position
                        if (shouldIgnorePlayerSwipeFromMoreActions(
                                downOnScreen,
                                moreButtonBounds,
                                moreButtonSafeMarginPx,
                            )
                        ) {
                            // The menu's own pointer recognizer retains this entire touch
                            // stream, including drag-to-select and release, without the
                            // outer player modifying its expansion fraction.
                            return@awaitEachGesture
                        }
                        val startFraction = expansionFraction
                        var lastPosition = down.position
                        var totalDrag = Offset.Zero
                        var axisLocked = false
                        var verticalGesture = false
                        var pointerPressed = true

                        while (pointerPressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.position - lastPosition
                            lastPosition = change.position
                            totalDrag += delta

                            if (!axisLocked) {
                                when (playerDragAxis(totalDrag, viewConfiguration.touchSlop)) {
                                    PlayerDragAxis.VERTICAL -> {
                                        axisLocked = true
                                        verticalGesture = true
                                    }
                                    PlayerDragAxis.HORIZONTAL -> {
                                        axisLocked = true
                                        verticalGesture = false
                                    }
                                    null -> Unit
                                }
                            }

                            if (axisLocked && verticalGesture) {
                                expansionFraction = (
                                    expansionFraction - (delta.y / travelPx)
                                ).coerceIn(0f, 1f)
                            }

                            pointerPressed = change.pressed
                        }

                        if (axisLocked && verticalGesture) {
                            val shouldExpand = if (startFraction >= 0.5f) {
                                totalDrag.y < settleDistancePx
                            } else {
                                totalDrag.y <= -settleDistancePx
                            }
                            // Both branches request a new settle, even if the target is unchanged.
                            if (shouldExpand) morphState.expand() else morphState.collapse()
                        }
                    }
                }
                .clickable(
                    enabled = expansionFraction < 0.08f,
                    onClick = morphState::expand,
                ),
            shape = playerShape,
            expansionFraction = expansionFraction,
        ) {
            val miniAlpha = (1f - expansionFraction / 0.24f).coerceIn(0f, 1f)
            val fullAlpha = ((expansionFraction - 0.10f) / 0.34f).coerceIn(0f, 1f)

            val normalAlpha = ((0.67f - dockMorph) / 0.57f).coerceIn(0f, 1f)
            val compactAlpha = ((dockMorph - 0.27f) / 0.59f).coerceIn(0f, 1f)
            if (normalAlpha > 0.01f) {
                MiniPlayerContent(
                    state = state,
                    queueState = queueState,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPlayQueueItemAt = viewModel::playQueueItemAt,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .height(compactHeight)
                        .graphicsLayer {
                            alpha = miniAlpha * normalAlpha
                            translationX = -dockMorph * with(density) { 9.dp.toPx() }
                        },
                )
            }
            if (compactAlpha > 0.01f) {
                CompactMiniPlayerContent(
                    state = state,
                    queueState = queueState,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPlayQueueItemAt = viewModel::playQueueItemAt,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .height(compactHeight)
                        .graphicsLayer {
                            alpha = miniAlpha * compactAlpha
                            translationX = (1f - dockMorph) * with(density) { 9.dp.toPx() }
                        },
                )
            }

            if (expansionFraction > 0.035f || morphState.targetExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = fullAlpha
                            translationY = (1f - fullAlpha) * with(density) { 18.dp.toPx() }
                        },
                ) {
                    NowPlayingRoute(
                        onBack = morphState::collapse,
                        onOpenQueue = {
                            morphState.collapse()
                            onOpenQueue()
                        },
                        onGoToArtist = { artist ->
                            morphState.collapse()
                            onGoToArtist(artist)
                        },
                        viewModel = viewModel,
                        onLyricsExpandedChange = { lyricsExpanded = it },
                        onInlineQueueExpandedChange = { inlineQueueExpanded = it },
                        onMoreActionsMenuExpandedChange = { moreActionsMenuOpen = it },
                        onMoreActionsButtonBoundsChanged = { moreButtonBounds = it },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = queueFeedbackVisible && queueFeedback != null && expansionFraction < 0.16f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 30.dp,
                    end = 30.dp,
                    bottom = feedbackClearance,
                ),
            enter = slideInVertically(
                animationSpec = tween(if (reduceMotion) 0 else 260, easing = FastOutSlowInEasing),
                initialOffsetY = { if (reduceMotion) 0 else it / 2 },
            ) + fadeIn(animationSpec = tween(if (reduceMotion) 0 else 180)),
            exit = slideOutVertically(
                animationSpec = tween(if (reduceMotion) 0 else 240, easing = FastOutSlowInEasing),
                targetOffsetY = { if (reduceMotion) 0 else it / 2 },
            ) + fadeOut(animationSpec = tween(if (reduceMotion) 0 else 170)),
        ) {
            queueFeedback?.let { feedback ->
                QueueActionFeedbackPill(hazeState = hazeState, feedback = feedback)
            }
        }
    }
}

@Composable
private fun QueueActionFeedbackPill(
    hazeState: HazeState,
    feedback: QueueActionFeedback,
) {
    val prefix = when (feedback.kind) {
        QueueActionKind.PLAY_NEXT -> "Playing next"
        QueueActionKind.ADD_TO_END -> "Added to queue"
        QueueActionKind.REMOVED -> "Removed from queue"
        QueueActionKind.CLEARED -> "Queue cleared"
        QueueActionKind.SOURCE_DELETED -> "Deleted from Telegram"
    }
    GlassSurface(
        hazeState = hazeState,
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
                text = if (feedback.kind == QueueActionKind.SOURCE_DELETED) "!" else "✓",
                style = MaterialTheme.typography.titleMedium,
                color = if (feedback.kind == QueueActionKind.SOURCE_DELETED)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = when (feedback.kind) {
                    QueueActionKind.CLEARED -> prefix
                    QueueActionKind.SOURCE_DELETED ->
                        "Deleted: ${feedback.trackTitle} · Next in 5s"
                    else -> "$prefix · ${feedback.trackTitle}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactMiniPlayerContent(
    state: PlaybackState,
    queueState: QueueState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPlayQueueItemAt: (Int) -> Unit,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    val activeIndex = remember(
        track.id, state.queueIndex, queueState.currentIndex, queueState.items,
    ) { resolveCurrentQueueIndex(state, queueState) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reuse the exact swipe pager from the regular mini-player. The
            // compact dock is smaller, but left/right navigation (including
            // history, gesture threshold, and haptics) must behave identically.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (activeIndex !in queueState.items.indices) {
                    MiniPlayerTrackSummary(
                        track = track.toMiniCard(),
                        reduceMotion = reduceMotion,
                        compact = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    InteractiveMiniTrackPager(
                        state = state,
                        queueState = queueState,
                        activeIndex = activeIndex,
                        onNext = onNext,
                        onPlayQueueItemAt = onPlayQueueItemAt,
                        reduceMotion = reduceMotion,
                        compact = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause
                    else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                )
            }
        }
        MiniPlayerProgressBar(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}

@Composable
private fun MiniPlayerContent(
    state: PlaybackState,
    queueState: QueueState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPlayQueueItemAt: (Int) -> Unit,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playingTrack = state.currentTrack ?: return
    val progressValue = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val activeIndex = remember(
        playingTrack.id,
        state.queueIndex,
        queueState.currentIndex,
        queueState.items,
    ) { resolveCurrentQueueIndex(state, queueState) }

    // Both the controls and the progress indicator share the same 72.dp coordinate space.
    // This anchors progress to the card's bottom edge, regardless of text direction.
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                if (activeIndex !in queueState.items.indices) {
                    MiniPlayerTrackSummary(
                        track = playingTrack.toMiniCard(),
                        reduceMotion = reduceMotion,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    InteractiveMiniTrackPager(
                        state = state,
                        queueState = queueState,
                        activeIndex = activeIndex,
                        onNext = onNext,
                        onPlayQueueItemAt = onPlayQueueItemAt,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (state.status == PlaybackStatus.PLAYING) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                )
            }
        }

        MiniPlayerProgressBar(
            progress = progressValue,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}

/**
 * Draw the mini-player seek progress from the physical left edge, not the logical
 * "start" edge. Canvas coordinates stay left-to-right in both LTR and RTL layouts.
 */
@Composable
internal fun MiniPlayerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
) {
    Canvas(modifier = modifier.height(2.dp).testTag("mini-player-progress")) {
        drawRect(color = trackColor)
        drawRect(
            color = progressColor,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
        )
    }
}

@Composable
private fun InteractiveMiniTrackPager(
    state: PlaybackState,
    queueState: QueueState,
    activeIndex: Int,
    onNext: () -> Unit,
    onPlayQueueItemAt: (Int) -> Unit,
    reduceMotion: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMeowzixHaptics()
    val scope = rememberCoroutineScope()

    var displayedIndex by remember { mutableIntStateOf(activeIndex) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var pendingUserTargetIndex by remember { mutableIntStateOf(-1) }
    var direction by remember { mutableStateOf<MiniCardDirection?>(null) }
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var thresholdDirection by remember { mutableIntStateOf(0) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }

    val latestActiveIndex by rememberUpdatedState(activeIndex)
    val latestCanSkipPrevious by rememberUpdatedState(state.canSkipPrevious)
    val latestCanSkipNext by rememberUpdatedState(state.canSkipNext)
    val latestNext by rememberUpdatedState(onNext)
    val latestPlayQueueItemAt by rememberUpdatedState(onPlayQueueItemAt)

    fun resetTransition(index: Int = latestActiveIndex) {
        if (index in queueState.items.indices) displayedIndex = index
        targetIndex = -1
        pendingUserTargetIndex = -1
        direction = null
        swipeProgress = 0f
        totalDragX = 0f
        thresholdDirection = 0
    }

    fun animateBack() {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            if (!reduceMotion) {
                animate(
                    initialValue = swipeProgress,
                    targetValue = 0f,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                ) { value, _ -> swipeProgress = value }
            }
            resetTransition(displayedIndex)
        }
    }

    fun commitTransition() {
        val activeDirection = direction ?: return
        val requestedTarget = targetIndex.takeIf { it in queueState.items.indices } ?: return
        transitionJob?.cancel()
        transitionJob = scope.launch {
            pendingUserTargetIndex = requestedTarget
            when (activeDirection) {
                MiniCardDirection.NEXT -> latestNext()
                MiniCardDirection.PREVIOUS -> latestPlayQueueItemAt(requestedTarget)
            }
            if (!reduceMotion) {
                animate(
                    initialValue = swipeProgress,
                    targetValue = 1f,
                    animationSpec = tween(160, easing = FastOutSlowInEasing),
                ) { value, _ -> swipeProgress = value }
                delay(24)
            }
            if (latestActiveIndex == requestedTarget || reduceMotion) {
                resetTransition(requestedTarget)
            } else {
                animate(
                    initialValue = swipeProgress,
                    targetValue = 0f,
                    animationSpec = tween(140, easing = FastOutSlowInEasing),
                ) { value, _ -> swipeProgress = value }
                resetTransition(latestActiveIndex)
            }
        }
    }

    LaunchedEffect(activeIndex, queueState.items.size) {
        if (activeIndex !in queueState.items.indices) return@LaunchedEffect
        if (displayedIndex !in queueState.items.indices) {
            resetTransition(activeIndex)
            return@LaunchedEffect
        }
        if (activeIndex == pendingUserTargetIndex || activeIndex == displayedIndex) return@LaunchedEffect
        val delta = activeIndex - displayedIndex
        if (abs(delta) != 1) {
            transitionJob?.cancel()
            resetTransition(activeIndex)
            return@LaunchedEffect
        }
        transitionJob?.cancel()
        transitionJob = scope.launch {
            direction = if (delta > 0) MiniCardDirection.NEXT else MiniCardDirection.PREVIOUS
            targetIndex = activeIndex
            swipeProgress = 0f
            if (!reduceMotion) {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(160, easing = FastOutSlowInEasing),
                ) { value, _ -> swipeProgress = value }
            }
            resetTransition(activeIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .semantics {
                // No visible skip buttons: TalkBack gets equivalent navigation actions.
                customActions = buildList {
                    if (state.canSkipPrevious && activeIndex - 1 in queueState.items.indices) {
                        add(CustomAccessibilityAction("Previous track") {
                            latestPlayQueueItemAt(activeIndex - 1)
                            true
                        })
                    }
                    if (state.canSkipNext && activeIndex + 1 in queueState.items.indices) {
                        add(CustomAccessibilityAction("Next track") {
                            latestNext()
                            true
                        })
                    }
                }
            }
            .pointerInput(
                activeIndex,
            state.canSkipPrevious,
            state.canSkipNext,
            queueState.items.size,
        ) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var axis: PlayerDragAxis? = null
                var totalMovement = Offset.Zero
                var cancelled = false
                var pressed = true

                while (pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        cancelled = true
                        break
                    }
                    totalMovement = change.position - down.position
                    if (axis == null) {
                        axis = playerDragAxis(totalMovement, viewConfiguration.touchSlop)
                        if (axis == PlayerDragAxis.HORIZONTAL) {
                            transitionJob?.cancel()
                            resetTransition(activeIndex)
                        }
                    }

                    if (axis == PlayerDragAxis.HORIZONTAL && change.pressed) {
                        if (change.isConsumed) {
                            cancelled = true
                            break
                        }
                        change.consume()
                        totalDragX = totalMovement.x
                        val requestedDirection = when {
                            totalDragX < 0f && latestCanSkipNext && displayedIndex + 1 in queueState.items.indices ->
                                MiniCardDirection.NEXT
                            totalDragX > 0f && latestCanSkipPrevious && displayedIndex - 1 in queueState.items.indices ->
                                MiniCardDirection.PREVIOUS
                            else -> null
                        }
                        if (requestedDirection == null) {
                            direction = null
                            targetIndex = -1
                            swipeProgress = 0f
                            thresholdDirection = 0
                        } else {
                            direction = requestedDirection
                            targetIndex = when (requestedDirection) {
                                MiniCardDirection.NEXT -> displayedIndex + 1
                                MiniCardDirection.PREVIOUS -> displayedIndex - 1
                            }
                            val nextProgress = (abs(totalDragX) / size.width.toFloat().coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            val nextThreshold = if (nextProgress >= MINI_CARD_COMMIT_THRESHOLD) {
                                if (requestedDirection == MiniCardDirection.NEXT) 1 else -1
                            } else {
                                0
                            }
                            if (nextThreshold != 0 && nextThreshold != thresholdDirection) {
                                haptics.perform(MeowzixHapticCue.Threshold)
                            }
                            thresholdDirection = nextThreshold
                            swipeProgress = nextProgress
                        }
                    }
                    pressed = change.pressed
                }

                if (axis == PlayerDragAxis.HORIZONTAL) {
                    when {
                        cancelled -> {
                            if (direction != null && swipeProgress > 0f) animateBack()
                            else resetTransition(activeIndex)
                        }
                        direction != null &&
                            shouldCommitMiniTrackSwipe(totalMovement, size.width.toFloat()) ->
                            commitTransition()
                        swipeProgress > 0f -> animateBack()
                        else -> resetTransition(activeIndex)
                    }
                }
            }
        },
    ) {
        val safeDisplayed = displayedIndex.coerceIn(queueState.items.indices)
        val currentCard = queueState.items[safeDisplayed].toMiniCard()
        val activeDirection = direction
        val hasTarget = activeDirection != null && targetIndex in queueState.items.indices
        if (!hasTarget) {
            MiniPlayerTrackSummary(
                track = currentCard,
                reduceMotion = reduceMotion,
                compact = compact,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val targetCard = queueState.items[targetIndex].toMiniCard()
            val p = swipeProgress.coerceIn(0f, 1f)
            val underlayCard = if (activeDirection == MiniCardDirection.NEXT) currentCard else targetCard
            val movingCard = if (activeDirection == MiniCardDirection.NEXT) targetCard else currentCard

            MiniPlayerStackLayer(
                direction = activeDirection,
                progress = p,
                modifier = Modifier.fillMaxSize(),
                underlay = {
                    MiniPlayerTrackSummary(
                        track = underlayCard,
                        reduceMotion = reduceMotion,
                        compact = compact,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                foreground = {
                    MiniPlayerTrackSummary(
                        track = movingCard,
                        reduceMotion = reduceMotion,
                        compact = compact,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }
}

@Composable
private fun MiniPlayerTrackSummary(
    track: MiniTrackCard,
    reduceMotion: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = if (compact) 2.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            Box(
                modifier = Modifier.size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                TrackArtwork(
                    artworkRef = track.artworkRef,
                    description = track.title,
                    size = 42.dp,
                    modifier = Modifier.requiredSize(42.dp),
                )
            }
        } else {
            TrackArtwork(
                artworkRef = track.artworkRef,
                description = track.title,
                size = 50.dp,
            )
        }
        Spacer(Modifier.width(if (compact) 6.dp else 12.dp))
        if (compact) {
            Text(
                text = track.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column(Modifier.weight(1f)) {
                dev.behradhz.meowzix.ui.components.DirectionalMarqueeTitle(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    reduceMotion = reduceMotion,
                )
                Text(
                    text = track.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun QueueItem.toMiniCard() = MiniTrackCard(id, title, artist, artworkRef)

private fun NowPlayingTrack.toMiniCard() = MiniTrackCard(id, title, artist, artworkRef)
