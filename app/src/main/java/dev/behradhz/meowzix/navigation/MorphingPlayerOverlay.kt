package dev.behradhz.meowzix.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.QueueActionFeedback
import dev.behradhz.meowzix.domain.playback.QueueActionKind
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.ui.components.AudioSpectrum
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

private val CollapsedPlayerHeight = 72.dp
private val CollapsedPlayerHorizontalInset = 14.dp
private val CollapsedPlayerDockClearance = 90.dp
private val PlayerSettleDistance = 72.dp

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

/**
 * One physical player surface shared by the mini-player and full Now Playing UI.
 *
 * The surface itself changes bounds and corner radius as [expansionFraction] changes. Vertical
 * pointer movement is observed in the Initial event pass and is intentionally not consumed. This
 * lets the player follow the finger even over child gesture zones such as the artwork pager, while
 * those children can continue to own horizontal track-swipe gestures.
 */
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
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    var expansionFraction by remember(track.id) {
        mutableFloatStateOf(if (morphState.targetExpanded) 1f else 0f)
    }
    var queueFeedback by remember { mutableStateOf<QueueActionFeedback?>(null) }
    var queueFeedbackVisible by remember { mutableStateOf(false) }

    suspend fun animateTo(target: Float, durationMillis: Int = 360) {
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
        val horizontalInset = lerp(
            CollapsedPlayerHorizontalInset,
            0.dp,
            expansionFraction,
        )
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
                .padding(
                    start = horizontalInset,
                    end = horizontalInset,
                    bottom = bottomInset,
                )
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
                            val target = if (shouldExpand) 1f else 0f
                            val targetChanged = morphState.targetExpanded != shouldExpand

                            if (shouldExpand) {
                                morphState.expand()
                            } else {
                                morphState.collapse()
                            }

                            if (!targetChanged) {
                                animationScope.launch {
                                    animateTo(target, durationMillis = 280)
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
                spectrum = spectrum,
                onTogglePlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
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
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 2 },
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 2 },
            ) + fadeOut(animationSpec = tween(170)),
        ) {
            queueFeedback?.let { feedback ->
                QueueActionFeedbackPill(
                    hazeState = hazeState,
                    feedback = feedback,
                )
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
    spectrum: AudioSpectrumState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0) {
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
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val visualLimit = with(density) { 24.dp.toPx() }
    var dragDistance by remember(track.id) { mutableFloatStateOf(0f) }
    var transitionDirection by remember { mutableStateOf(1) }

    // A direct Previous gesture reverses the transition once. Automatic advances and normal Next
    // gestures always use the requested right-to-left reveal.
    LaunchedEffect(track.id) {
        delay(340)
        transitionDirection = 1
    }

    Column(
        modifier = modifier
            .graphicsLayer { translationX = dragDistance.coerceIn(-visualLimit, visualLimit) }
            .pointerInput(track.id, state.canSkipPrevious, state.canSkipNext) {
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = {
                        when {
                            dragDistance <= -swipeThreshold && state.canSkipNext -> {
                                transitionDirection = 1
                                onNext()
                            }
                            dragDistance >= swipeThreshold && state.canSkipPrevious -> {
                                transitionDirection = -1
                                onPrevious()
                            }
                        }
                        dragDistance = 0f
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = track,
                contentKey = { it.id },
                transitionSpec = {
                    if (transitionDirection >= 0) {
                        (slideInHorizontally(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> fullWidth },
                        ) + fadeIn(animationSpec = tween(180)))
                            .togetherWith(
                                shrinkHorizontally(
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    shrinkTowards = Alignment.Start,
                                ) + fadeOut(animationSpec = tween(150)),
                            )
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> -fullWidth },
                        ) + fadeIn(animationSpec = tween(180)))
                            .togetherWith(
                                shrinkHorizontally(
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    shrinkTowards = Alignment.End,
                                ) + fadeOut(animationSpec = tween(150)),
                            )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
            ) { animatedTrack ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArtwork(
                        artworkRef = animatedTrack.artworkRef,
                        description = animatedTrack.title,
                        size = 50.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = animatedTrack.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = animatedTrack.artist ?: "Unknown artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (hasWaveform && state.status == PlaybackStatus.PLAYING) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .align(Alignment.CenterStart),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
            ) {}
        }
    }
}
