package dev.behradhz.meowzix.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.QueueActionFeedback
import dev.behradhz.meowzix.domain.playback.QueueActionKind
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.ui.components.AudioSpectrum
import dev.behradhz.meowzix.ui.components.GlassSurface
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
 * Two actual full-width cards stacked in the same frame. The foreground translates without
 * resizing; the underlay is stationary and masked only where the foreground covers it.
 * Masking prevents transparent card content from showing through the foreground on glass.
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
    Box(modifier = modifier.clipToBounds()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * foregroundX) { drawContent() }
                },
        ) {
            underlay()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = size.width * foregroundX },
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

    fun expand() {
        targetExpanded = true
    }

    fun collapse() {
        targetExpanded = false
    }
}

@Composable
internal fun rememberMorphingPlayerState(): MorphingPlayerState =
    remember { MorphingPlayerState() }

@Composable
internal fun MorphingPlayerOverlay(
    hazeState: HazeState,
    state: PlaybackState,
    spectrum: AudioSpectrumState,
    viewModel: NowPlayingViewModel,
    morphState: MorphingPlayerState,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val reduceMotion = appearance.reduceMotion
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    var expansionFraction by remember(track.id) {
        mutableFloatStateOf(if (morphState.targetExpanded) 1f else 0f)
    }
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

    LaunchedEffect(morphState.targetExpanded, track.id) {
        animateTo(if (morphState.targetExpanded) 1f else 0f)
    }

    LaunchedEffect(viewModel) {
        viewModel.queueActionFeedback.collectLatest { event ->
            queueFeedback = event
            queueFeedbackVisible = true
            delay(1_550)
            queueFeedbackVisible = false
            delay(260)
            if (queueFeedback == event) queueFeedback = null
        }
    }

    BackHandler(enabled = morphState.targetExpanded || expansionFraction > 0.02f) {
        morphState.collapse()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val navigationBottom = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val collapsedBottom = navigationBottom + CollapsedPlayerDockClearance
        val horizontalInset = lerp(CollapsedPlayerHorizontalInset, 0.dp, expansionFraction)
        val bottomInset = lerp(collapsedBottom, 0.dp, expansionFraction)
        val playerHeight = lerp(CollapsedPlayerHeight, maxHeight, expansionFraction)
        val cornerRadius = lerp(28.dp, 0.dp, expansionFraction)
        val travelPx = with(density) {
            (maxHeight - CollapsedPlayerHeight).toPx().coerceAtLeast(1f)
        }
        val settleDistancePx = with(density) { PlayerSettleDistance.toPx() }

        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = horizontalInset, end = horizontalInset, bottom = bottomInset)
                .height(playerHeight)
                .pointerInput(track.id, travelPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
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

                            if (
                                !axisLocked &&
                                maxOf(abs(totalDrag.x), abs(totalDrag.y)) >= viewConfiguration.touchSlop
                            ) {
                                axisLocked = true
                                verticalGesture = abs(totalDrag.y) > abs(totalDrag.x)
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
                            val targetFraction = if (shouldExpand) 1f else 0f
                            val targetChanged = morphState.targetExpanded != shouldExpand

                            if (shouldExpand) morphState.expand() else morphState.collapse()

                            if (!targetChanged) {
                                animationScope.launch {
                                    animateTo(targetFraction, durationMillis = 280)
                                }
                            }
                        }
                    }
                }
                .clickable(
                    enabled = expansionFraction < 0.08f,
                    onClick = morphState::expand,
                ),
            shape = RoundedCornerShape(cornerRadius),
            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
            tint = Color.White.copy(alpha = 0.10f),
        ) {
            val miniAlpha = (1f - expansionFraction / 0.24f).coerceIn(0f, 1f)
            val fullAlpha = ((expansionFraction - 0.10f) / 0.34f).coerceIn(0f, 1f)

            MiniPlayerContent(
                state = state,
                queueState = queueState,
                spectrum = spectrum,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPlayQueueItemAt = viewModel::playQueueItemAt,
                reduceMotion = reduceMotion,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(CollapsedPlayerHeight)
                    .graphicsLayer {
                        alpha = miniAlpha
                        scaleX = 1f - (0.025f * expansionFraction)
                        scaleY = 1f - (0.025f * expansionFraction)
                    },
            )

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
                        viewModel = viewModel,
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
                    bottom = collapsedBottom + CollapsedPlayerHeight + 10.dp,
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
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$prefix · ${feedback.trackTitle}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniPlayerContent(
    state: PlaybackState,
    queueState: QueueState,
    spectrum: AudioSpectrumState,
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
    val compactBands = remember(spectrum.bands) {
        val source = spectrum.bands
        FloatArray(8) { compactIndex ->
            val start = compactIndex * source.size / 8
            val endExclusive = ((compactIndex + 1) * source.size / 8)
                .coerceAtLeast(start + 1)
                .coerceAtMost(source.size)
            var peak = 0f
            for (index in start until endExclusive) {
                if (source[index] > peak) peak = source[index]
            }
            peak
        }
    }
    val hasWaveform = remember(compactBands) { compactBands.any { it > 0.001f } }
    val activeIndex = remember(
        playingTrack.id,
        state.queueIndex,
        queueState.currentIndex,
        queueState.items,
    ) { resolveMiniActiveIndex(state, queueState) }

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
                        showWaveform = !reduceMotion && hasWaveform && state.status == PlaybackStatus.PLAYING,
                        compactBands = compactBands,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    InteractiveMiniTrackPager(
                        state = state,
                        queueState = queueState,
                        activeIndex = activeIndex,
                        compactBands = compactBands,
                        hasWaveform = hasWaveform,
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
    compactBands: FloatArray,
    hasWaveform: Boolean,
    onNext: () -> Unit,
    onPlayQueueItemAt: (Int) -> Unit,
    reduceMotion: Boolean = false,
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
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
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
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
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
            detectHorizontalDragGestures(
                onDragStart = {
                    transitionJob?.cancel()
                    resetTransition(activeIndex)
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount
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
                },
                onDragCancel = {
                    if (direction != null && swipeProgress > 0f) animateBack()
                    else resetTransition(activeIndex)
                },
                onDragEnd = {
                    if (direction != null && swipeProgress >= MINI_CARD_COMMIT_THRESHOLD) {
                        commitTransition()
                    } else if (swipeProgress > 0f) {
                        animateBack()
                    } else {
                        resetTransition(activeIndex)
                    }
                },
            )
        },
    ) {
        val safeDisplayed = displayedIndex.coerceIn(queueState.items.indices)
        val currentCard = queueState.items[safeDisplayed].toMiniCard()
        val activeDirection = direction
        val hasTarget = activeDirection != null && targetIndex in queueState.items.indices
        if (!hasTarget) {
            MiniPlayerTrackSummary(
                track = currentCard,
                showWaveform = !reduceMotion && currentCard.id == state.currentTrack?.id &&
                    hasWaveform && state.status == PlaybackStatus.PLAYING,
                compactBands = compactBands,
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
                        showWaveform = !reduceMotion && underlayCard.id == state.currentTrack?.id &&
                            hasWaveform && state.status == PlaybackStatus.PLAYING,
                        compactBands = compactBands,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                foreground = {
                    MiniPlayerTrackSummary(
                        track = movingCard,
                        showWaveform = !reduceMotion && movingCard.id == state.currentTrack?.id &&
                            hasWaveform && state.status == PlaybackStatus.PLAYING,
                        compactBands = compactBands,
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
    showWaveform: Boolean,
    compactBands: FloatArray,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(
            artworkRef = track.artworkRef,
            description = track.title,
            size = 50.dp,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist ?: "Unknown artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showWaveform) {
            AudioSpectrum(
                bands = compactBands,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .width(44.dp)
                    .height(22.dp)
                    .padding(horizontal = 2.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

private fun QueueItem.toMiniCard() = MiniTrackCard(id, title, artist, artworkRef)

private fun NowPlayingTrack.toMiniCard() = MiniTrackCard(id, title, artist, artworkRef)

private fun resolveMiniActiveIndex(state: PlaybackState, queueState: QueueState): Int {
    val currentTrackId = state.currentTrack?.id ?: return -1
    if (
        state.queueIndex in queueState.items.indices &&
        queueState.items[state.queueIndex].id == currentTrackId
    ) {
        return state.queueIndex
    }
    if (
        queueState.currentIndex in queueState.items.indices &&
        queueState.items[queueState.currentIndex].id == currentTrackId
    ) {
        return queueState.currentIndex
    }
    return queueState.items.indexOfFirst { it.id == currentTrackId }
}
