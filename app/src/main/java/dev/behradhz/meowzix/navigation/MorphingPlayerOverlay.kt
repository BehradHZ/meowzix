package dev.behradhz.meowzix.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateBottomPadding
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.ui.components.AudioSpectrum
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.chrisbanes.haze.HazeState

private val CollapsedPlayerHeight = 72.dp
private val CollapsedPlayerHorizontalInset = 14.dp
private val CollapsedPlayerDockClearance = 90.dp
private const val PlayerExpandVelocityThresholdDp = 720f

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
 * The surface itself changes bounds and corner radius as [expansionFraction] changes. Vertical drag
 * writes directly to that fraction, so the UI follows the finger instead of waiting for navigation
 * to finish. Releasing the gesture settles to the nearest state with a short eased animation.
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
    var expansionFraction by remember(track.id) {
        mutableFloatStateOf(if (morphState.targetExpanded) 1f else 0f)
    }

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
        val velocityThresholdPx = with(density) {
            PlayerExpandVelocityThresholdDp.dp.toPx()
        }
        val draggableState = rememberDraggableState { delta ->
            expansionFraction = (expansionFraction - (delta / travelPx)).coerceIn(0f, 1f)
        }

        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = horizontalInset, bottom = bottomInset)
                .height(playerHeight)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val shouldExpand = when {
                            velocity <= -velocityThresholdPx -> true
                            velocity >= velocityThresholdPx -> false
                            else -> expansionFraction >= 0.48f
                        }
                        val target = if (shouldExpand) 1f else 0f
                        val targetChanged = morphState.targetExpanded != shouldExpand
                        if (shouldExpand) morphState.expand() else morphState.collapse()
                        if (!targetChanged) {
                            animateTo(target, durationMillis = 280)
                        }
                    },
                )
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
                    .fillMaxSize()
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
                            dragDistance <= -swipeThreshold && state.canSkipNext -> onNext()
                            dragDistance >= swipeThreshold && state.canSkipPrevious -> onPrevious()
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
