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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.CompositingStrategy
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
import dev.behradhz.meowzix.ui.components.PreloadNowPlayingArtwork
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class ArtworkTransitionDirection { PREVIOUS, NEXT }
private enum class ArtworkCropRole { CURRENT, TARGET }
private enum class ArtworkGestureAxis { UNDECIDED, HORIZONTAL, VERTICAL }
private const val ArtworkCommitThreshold = 0.35f

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
    val haptics = rememberMeowzixHaptics()

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
                        // Avoid alpha's default offscreen buffer clipping the elevated artwork shadow
                        // to a hard rectangular layer while the paused cover is scaled down.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .pointerInput(viewModel) {
                        detectTapGestures(onTap = { viewModel.togglePlayPause() })
                    },
            )
        }
        return
    }

    // Warm both adjacent covers while the current track is visible. The player and backdrop use
    // the same 2048px cache bucket, so Next/Previous can transition without flashing placeholder art.
    PreloadNowPlayingArtwork(queueState.items.getOrNull(activeIndex - 1)?.artworkRef)
    PreloadNowPlayingArtwork(queueState.items.getOrNull(activeIndex + 1)?.artworkRef)

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
    var thresholdDirection by remember { mutableIntStateOf(0) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }

    val latestActiveIndex by rememberUpdatedState(activeIndex)
    val latestCanSkipPrevious by rememberUpdatedState(state.canSkipPrevious)
    val latestCanSkipNext by rememberUpdatedState(state.canSkipNext)
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

            // A right-swipe is explicit track navigation, so it must bypass seekToPrevious's
            // restart-current behavior. The Previous button still uses the regular previous action.
            when (transitionDirection) {
                ArtworkTransitionDirection.NEXT -> latestNext()
                ArtworkTransitionDirection.PREVIOUS -> viewModel.playQueueItemAt(requestedTarget)
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
                    thresholdDirection = 0
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
                                thresholdDirection = 0
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
                                val nextThresholdDirection = if (newProgress >= ArtworkCommitThreshold) {
                                    when (requestedDirection) {
                                        ArtworkTransitionDirection.NEXT -> 1
                                        ArtworkTransitionDirection.PREVIOUS -> -1
                                    }
                                } else {
                                    0
                                }
                                if (nextThresholdDirection != 0 && nextThresholdDirection != thresholdDirection) {
                                    haptics.perform(MeowzixHapticCue.Threshold)
                                }
                                thresholdDirection = nextThresholdDirection
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
                    thresholdDirection = 0
                    totalDrag = Offset.Zero
                    gestureAxis = ArtworkGestureAxis.UNDECIDED
                },
                onDragEnd = {
                    when (gestureAxis) {
                        ArtworkGestureAxis.HORIZONTAL -> {
                            if (direction != null && progress >= ArtworkCommitThreshold) {
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
                    thresholdDirection = 0
                    totalDrag = Offset.Zero
                    gestureAxis = ArtworkGestureAxis.UNDECIDED
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        val artworkSize = minOf(maxWidth * 0.90f, maxHeight * 0.90f)
        val artworkShape = RoundedCornerShape(28.dp)
        val safeDisplayedIndex = displayedIndex.coerceIn(queueState.items.indices)
        val currentItem = queueState.items[safeDisplayedIndex]
        val currentArtworkRef = currentItem.artworkRef ?: if (currentItem.id == state.currentTrack?.id) {
            fallbackArtworkRef
        } else {
            null
        }
        val activeDirection = direction
        val hasTarget = activeDirection != null && targetIndex in queueState.items.indices
        val artworkShadowShape = if (hasTarget) {
            artworkTransitionShadowShape(
                direction = activeDirection,
                progress = progress,
                gapPx = separatorGapPx,
                cornerRadiusPx = artworkCornerRadiusPx,
            )
        } else {
            artworkShape
        }

        Box(
            modifier = Modifier
                .size(artworkSize)
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                    alpha = artworkAlpha
                    // The shadow intentionally lives inside this transform so it scales with the
                    // cover. ModulateAlpha avoids creating a rectangular offscreen buffer that would
                    // clip that shadow when paused.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .shadow(
                    elevation = 18.dp,
                    shape = artworkShadowShape,
                    clip = false,
                )
                .pointerInput(latestTogglePlayPause) {
                    detectTapGestures(onTap = { latestTogglePlayPause() })
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!hasTarget) {
                NowPlayingArtwork(
                    artworkRef = currentArtworkRef,
                    description = "${currentItem.title} cover art",
                    modifier = Modifier.fillMaxSize(),
                    showShadow = false,
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
                        showShadow = false,
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
                        showShadow = false,
                    )
                }
            }
        }
    }
}

private fun artworkTransitionShadowShape(
    direction: ArtworkTransitionDirection,
    progress: Float,
    gapPx: Float,
    cornerRadiusPx: Float,
) = GenericShape { size, _ ->
    val clampedProgress = progress.coerceIn(0f, 1f)
    val seam = when (direction) {
        ArtworkTransitionDirection.NEXT -> size.width * (1f - clampedProgress)
        ArtworkTransitionDirection.PREVIOUS -> size.width * clampedProgress
    }
    val halfGap = animatedSeparatorHalfGap(gapPx, clampedProgress)
    val path = this

    fun addFragment(left: Float, right: Float) {
        if (right <= left) return
        val visibleWidth = right - left
        val radius = cornerRadiusPx
            .coerceAtLeast(0f)
            .coerceAtMost(minOf(visibleWidth, size.height) / 2f)
        val cornerRadius = CornerRadius(radius, radius)
        path.addRoundRect(
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

    when (direction) {
        ArtworkTransitionDirection.NEXT -> {
            addFragment(
                left = 0f,
                right = (seam - halfGap).coerceIn(0f, size.width),
            )
            addFragment(
                left = (seam + halfGap).coerceIn(0f, size.width),
                right = size.width,
            )
        }

        ArtworkTransitionDirection.PREVIOUS -> {
            addFragment(
                left = (seam + halfGap).coerceIn(0f, size.width),
                right = size.width,
            )
            addFragment(
                left = 0f,
                right = (seam - halfGap).coerceIn(0f, size.width),
            )
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
    val halfGap = animatedSeparatorHalfGap(gapPx, clampedProgress)

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

private fun animatedSeparatorHalfGap(gapPx: Float, progress: Float): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val visibility = (4f * clampedProgress * (1f - clampedProgress)).coerceIn(0f, 1f)
    return gapPx.coerceAtLeast(0f) * visibility / 2f
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
