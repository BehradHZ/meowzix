package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.components.NowPlayingArtwork
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class ArtworkTransitionDirection { PREVIOUS, NEXT }
private enum class ArtworkCropRole { CURRENT, TARGET }
private enum class ArtworkGestureAxis { UNDECIDED, HORIZONTAL, VERTICAL }

internal data class ArtworkBackdropTransition(
    val fromArtworkRef: String?,
    val toArtworkRef: String?,
    val progress: Float,
)

/**
 * Queue-backed artwork transition for Now Playing.
 *
 * Both covers remain at exactly the same position and size for the whole gesture. Moving to Next
 * crops the current cover from the right while removing the next cover's crop from the left. Moving
 * to Previous mirrors that behavior. A narrow undrawn strip follows the shared seam so the actual
 * player backdrop is visible between the two covers. Each visible artwork fragment keeps rounded
 * corners on both its outer edge and its separator edge throughout the gesture.
 */
@Composable
internal fun NowPlayingArtworkPager(
    state: PlaybackState,
    queueState: QueueState,
    fallbackArtworkRef: String?,
    fallbackArtworkDescription: String,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackdropTransition: (ArtworkBackdropTransition?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // NowPlayingArtworkPager is only hosted by NowPlayingRoute, so this resolves to the same scoped
    // view model as the route. Keeping the tap action here avoids duplicating gesture ownership.
    val viewModel: NowPlayingViewModel = hiltViewModel()

    val activeIndex = remember(
        state.currentTrack?.id,
        state.queueIndex,
        queueState.currentIndex,
        queueState.items,
    ) {
        resolveActiveQueueIndex(state, queueState)
    }

    val isPaused = state.status == PlaybackStatus.PAUSED
    val artworkScale by animateFloatAsState(
        targetValue = if (isPaused) 0.90f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "now-playing-artwork-scale",
    )
    val artworkAlpha by animateFloatAsState(
        targetValue = if (isPaused) 0.72f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "now-playing-artwork-alpha",
    )

    val queueAvailable = activeIndex in queueState.items.indices && queueState.items.isNotEmpty()

    if (!queueAvailable) {
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val artworkSize = minOf(maxWidth * 0.90f, maxHeight * 0.90f)
            NowPlayingArtwork(
                artworkRef = fallbackArtworkRef,
                description = fallbackArtworkDescription,
                modifier = Modifier
                    .size(artworkSize)
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                        alpha = artworkAlpha
                    }
                    .pointerInput(viewModel) {
                        detectTapGestures(onTap = { viewModel.togglePlayPause() })
                    },
            )
        }
        return
    }

    val density = LocalDensity.current
    val axisThresholdPx = with(density) { 10.dp.toPx() }
    val closeThresholdPx = with(density) { 96.dp.toPx() }
    val separatorGapPx = with(density) { 12.dp.toPx() }
    val artworkCornerRadiusPx = with(density) { 28.dp.toPx() }
    val scope = rememberCoroutineScope()

    var displayedIndex by remember { mutableIntStateOf(activeIndex) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var pendingUserTargetIndex by remember { mutableIntStateOf(-1) }
    var direction by remember { mutableStateOf<ArtworkTransitionDirection?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var totalDrag by remember { mutableStateOf(Offset.Zero) }
    var gestureAxis by remember { mutableStateOf(ArtworkGestureAxis.UNDECIDED) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }

    val latestActiveIndex by rememberUpdatedState(activeIndex)
    val latestCanSkipPrevious by rememberUpdatedState(state.canSkipPrevious)
    val latestCanSkipNext by rememberUpdatedState(state.canSkipNext)
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestNext by rememberUpdatedState(onNext)
    val latestTogglePlayPause by rememberUpdatedState(viewModel::togglePlayPause)
    val latestBackdropTransition by rememberUpdatedState(onBackdropTransition)

    fun artworkRefAt(index: Int): String? {
        val item = queueState.items.getOrNull(index) ?: return null
        return item.artworkRef ?: if (item.id == state.currentTrack?.id) fallbackArtworkRef else null
    }

    fun publishBackdropTransition(value: Float = progress) {
        val sourceIndex = displayedIndex
        val destinationIndex = targetIndex
        if (
            direction == null ||
            sourceIndex !in queueState.items.indices ||
            destinationIndex !in queueState.items.indices
        ) {
            latestBackdropTransition(null)
            return
        }

        latestBackdropTransition(
            ArtworkBackdropTransition(
                fromArtworkRef = artworkRefAt(sourceIndex),
                toArtworkRef = artworkRefAt(destinationIndex),
                progress = value.coerceIn(0f, 1f),
            ),
        )
    }

    fun resetTransition(index: Int = latestActiveIndex) {
        displayedIndex = index.coerceIn(queueState.items.indices)
        targetIndex = -1
        pendingUserTargetIndex = -1
        direction = null
        progress = 0f
        latestBackdropTransition(null)
    }

    fun animateBackToCurrent() {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            val start = progress
            animate(
                initialValue = start,
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            ) { value, _ ->
                progress = value
                publishBackdropTransition(value)
            }
            targetIndex = -1
            direction = null
            progress = 0f
            latestBackdropTransition(null)
        }
    }

    fun commitGestureTransition() {
        val transitionDirection = direction ?: return
        val requestedTarget = targetIndex.takeIf { it in queueState.items.indices } ?: return

        transitionJob?.cancel()
        transitionJob = scope.launch {
            pendingUserTargetIndex = requestedTarget

            // Playback changes only after the gesture has crossed the halfway commitment point.
            when (transitionDirection) {
                ArtworkTransitionDirection.NEXT -> latestNext()
                ArtworkTransitionDirection.PREVIOUS -> latestPrevious()
            }

            val start = progress
            animate(
                initialValue = start,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            ) { value, _ ->
                progress = value
                publishBackdropTransition(value)
            }

            if (latestActiveIndex == requestedTarget) {
                resetTransition(requestedTarget)
            } else {
                // If playback rejected the skip for any reason, restore the current artwork instead
                // of leaving the target cover visually selected.
                animate(
                    initialValue = progress,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                ) { value, _ ->
                    progress = value
                    publishBackdropTransition(value)
                }
                resetTransition(latestActiveIndex)
            }
        }
    }

    // Button presses, auto-advance, and other playback changes all use the same stationary artwork
    // crop and backdrop crossfade. A user-driven transition is already animating toward
    // pendingUserTargetIndex, so it is not restarted when playback reports that same index.
    LaunchedEffect(activeIndex, queueState.items.size) {
        if (activeIndex !in queueState.items.indices) return@LaunchedEffect

        if (displayedIndex !in queueState.items.indices) {
            resetTransition(activeIndex)
            return@LaunchedEffect
        }

        if (activeIndex == pendingUserTargetIndex) return@LaunchedEffect
        if (activeIndex == displayedIndex) return@LaunchedEffect

        val delta = activeIndex - displayedIndex
        if (abs(delta) != 1) {
            transitionJob?.cancel()
            resetTransition(activeIndex)
            return@LaunchedEffect
        }

        transitionJob?.cancel()
        transitionJob = scope.launch {
            direction = if (delta > 0) {
                ArtworkTransitionDirection.NEXT
            } else {
                ArtworkTransitionDirection.PREVIOUS
            }
            targetIndex = activeIndex
            progress = 0f
            publishBackdropTransition(0f)

            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            ) { value, _ ->
                progress = value
                publishBackdropTransition(value)
            }

            resetTransition(activeIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier.pointerInput(
            activeIndex,
            state.canSkipPrevious,
            state.canSkipNext,
            queueState.items.size,
        ) {
            detectDragGestures(
                onDragStart = {
                    transitionJob?.cancel()
                    if (activeIndex in queueState.items.indices) {
                        resetTransition(activeIndex)
                    }
                    totalDrag = Offset.Zero
                    gestureAxis = ArtworkGestureAxis.UNDECIDED
                },
                onDrag = { change, dragAmount ->
                    totalDrag += dragAmount

                    if (
                        gestureAxis == ArtworkGestureAxis.UNDECIDED &&
                        maxOf(abs(totalDrag.x), abs(totalDrag.y)) >= axisThresholdPx
                    ) {
                        gestureAxis = if (abs(totalDrag.x) > abs(totalDrag.y)) {
                            ArtworkGestureAxis.HORIZONTAL
                        } else {
                            ArtworkGestureAxis.VERTICAL
                        }
                    }

                    when (gestureAxis) {
                        ArtworkGestureAxis.HORIZONTAL -> {
                            change.consume()
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val requestedDirection = when {
                                totalDrag.x < 0f &&
                                    latestCanSkipNext &&
                                    displayedIndex + 1 in queueState.items.indices ->
                                    ArtworkTransitionDirection.NEXT

                                totalDrag.x > 0f &&
                                    latestCanSkipPrevious &&
                                    displayedIndex - 1 in queueState.items.indices ->
                                    ArtworkTransitionDirection.PREVIOUS

                                else -> null
                            }

                            if (requestedDirection == null) {
                                direction = null
                                targetIndex = -1
                                progress = 0f
                                latestBackdropTransition(null)
                            } else {
                                direction = requestedDirection
                                targetIndex = when (requestedDirection) {
                                    ArtworkTransitionDirection.NEXT -> displayedIndex + 1
                                    ArtworkTransitionDirection.PREVIOUS -> displayedIndex - 1
                                }
                                val newProgress = (abs(totalDrag.x) / width).coerceIn(0f, 1f)
                                progress = newProgress
                                publishBackdropTransition(newProgress)
                            }
                        }

                        ArtworkGestureAxis.VERTICAL -> {
                            change.consume()
                        }

                        ArtworkGestureAxis.UNDECIDED -> Unit
                    }
                },
                onDragCancel = {
                    if (direction != null && progress > 0f) animateBackToCurrent()
                    totalDrag = Offset.Zero
                    gestureAxis = ArtworkGestureAxis.UNDECIDED
                },
                onDragEnd = {
                    when (gestureAxis) {
                        ArtworkGestureAxis.HORIZONTAL -> {
                            if (direction != null && progress >= 0.50f) {
                                commitGestureTransition()
                            } else if (progress > 0f) {
                                animateBackToCurrent()
                            }
                        }

                        ArtworkGestureAxis.VERTICAL -> {
                            if (totalDrag.y >= closeThresholdPx && abs(totalDrag.y) > abs(totalDrag.x)) {
                                onBack()
                            }
                        }

                        ArtworkGestureAxis.UNDECIDED -> Unit
                    }
                    totalDrag = Offset.Zero
                    gestureAxis = ArtworkGestureAxis.UNDECIDED
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        val artworkSize = minOf(maxWidth * 0.90f, maxHeight * 0.90f)
        val safeDisplayedIndex = displayedIndex.coerceIn(queueState.items.indices)
        val currentItem = queueState.items[safeDisplayedIndex]
        val currentArtworkRef = currentItem.artworkRef ?: if (currentItem.id == state.currentTrack?.id) {
            fallbackArtworkRef
        } else {
            null
        }

        Box(
            modifier = Modifier
                .size(artworkSize)
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                    alpha = artworkAlpha
                }
                .pointerInput(latestTogglePlayPause) {
                    detectTapGestures(onTap = { latestTogglePlayPause() })
                },
            contentAlignment = Alignment.Center,
        ) {
            val activeDirection = direction
            val hasTarget = activeDirection != null && targetIndex in queueState.items.indices

            if (!hasTarget) {
                NowPlayingArtwork(
                    artworkRef = currentArtworkRef,
                    description = "${currentItem.title} cover art",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val targetItem = queueState.items[targetIndex]
                val targetArtworkRef = targetItem.artworkRef ?: if (targetItem.id == state.currentTrack?.id) {
                    fallbackArtworkRef
                } else {
                    null
                }

                // Both full-size covers stay fixed in place. Their visible fragments are masked as
                // independent rounded cards, so the moving separator never creates square inner edges.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .cropArtwork(
                            direction = activeDirection,
                            role = ArtworkCropRole.CURRENT,
                            progress = progress,
                            gapPx = separatorGapPx,
                            cornerRadiusPx = artworkCornerRadiusPx,
                        ),
                ) {
                    NowPlayingArtwork(
                        artworkRef = currentArtworkRef,
                        description = "${currentItem.title} cover art",
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .cropArtwork(
                            direction = activeDirection,
                            role = ArtworkCropRole.TARGET,
                            progress = progress,
                            gapPx = separatorGapPx,
                            cornerRadiusPx = artworkCornerRadiusPx,
                        ),
                ) {
                    NowPlayingArtwork(
                        artworkRef = targetArtworkRef,
                        description = "${targetItem.title} cover art",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun Modifier.cropArtwork(
    direction: ArtworkTransitionDirection,
    role: ArtworkCropRole,
    progress: Float,
    gapPx: Float,
    cornerRadiusPx: Float,
): Modifier = drawWithContent {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val seam = when (direction) {
        ArtworkTransitionDirection.NEXT -> size.width * (1f - clampedProgress)
        ArtworkTransitionDirection.PREVIOUS -> size.width * clampedProgress
    }
    val halfGap = gapPx.coerceAtLeast(0f) / 2f

    val left: Float
    val right: Float
    when (direction) {
        ArtworkTransitionDirection.NEXT -> when (role) {
            ArtworkCropRole.CURRENT -> {
                left = 0f
                right = (seam - halfGap).coerceIn(0f, size.width)
            }

            ArtworkCropRole.TARGET -> {
                left = (seam + halfGap).coerceIn(0f, size.width)
                right = size.width
            }
        }

        ArtworkTransitionDirection.PREVIOUS -> when (role) {
            ArtworkCropRole.CURRENT -> {
                left = (seam + halfGap).coerceIn(0f, size.width)
                right = size.width
            }

            ArtworkCropRole.TARGET -> {
                left = 0f
                right = (seam - halfGap).coerceIn(0f, size.width)
            }
        }
    }

    if (right > left) {
        val visibleWidth = right - left
        val radius = cornerRadiusPx
            .coerceAtLeast(0f)
            .coerceAtMost(minOf(visibleWidth, size.height) / 2f)
        val cornerRadius = CornerRadius(radius, radius)
        val roundedMask = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = 0f,
                    right = right,
                    bottom = size.height,
                    topLeftCornerRadius = cornerRadius,
                    topRightCornerRadius = cornerRadius,
                    bottomRightCornerRadius = cornerRadius,
                    bottomLeftCornerRadius = cornerRadius,
                ),
            )
        }

        clipPath(roundedMask) {
            this@drawWithContent.drawContent()
        }
    }
}

private fun resolveActiveQueueIndex(
    state: PlaybackState,
    queueState: QueueState,
): Int {
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
