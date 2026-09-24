package dev.behradhz.meowzix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Waveform seek bar driven by the live playback spectrum.
 *
 * During playback and scrubbing the played region keeps using the continuously updating live FFT;
 * dragging the pointer only changes where that equalizer is cropped and tapered to zero. No fixed
 * per-track waveform is introduced during seek. Loading is represented by a traveling equalizer
 * peak whose neighboring bars gradually decrease in height, with a short pause between passes.
 */
@Composable
fun SyntheticWaveformSeekBar(
    trackKey: String,
    liveBands: FloatArray,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    activeBarColor: Color = MaterialTheme.colorScheme.primary,
    inactiveBarColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
    pointerColor: Color = MaterialTheme.colorScheme.onSurface,
    loadingShimmerColor: Color = Color(0xFFFF4D4D),
    barCount: Int = 64,
    barWidth: Dp = 3.dp,
    pointerRadius: Dp = 7.dp,
    decayBarCount: Int = 8,
    shimmerDurationMs: Int = 1_050,
) {
    val resolvedBarCount = barCount.coerceAtLeast(12)
    val liveProfile = remember(liveBands, resolvedBarCount) {
        expandLiveSpectrum(liveBands, resolvedBarCount)
    }
    val hasLiveSpectrum = remember(liveBands) { liveBands.any { it > 0.001f } }
    var stableLiveProfile by remember(trackKey, resolvedBarCount) {
        mutableStateOf(FloatArray(resolvedBarCount))
    }

    // Retain the last valid FFT when Media3 briefly stops producing bands around a seek/buffer.
    // While valid bands keep arriving, this profile continues updating during the drag itself.
    LaunchedEffect(trackKey, resolvedBarCount, liveBands.contentHashCode(), hasLiveSpectrum) {
        if (hasLiveSpectrum) stableLiveProfile = liveProfile.copyOf()
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed
    var suppressLoadingWaveform by remember(trackKey) { mutableStateOf(false) }
    var seekReleaseGeneration by remember(trackKey) { mutableStateOf(0) }

    // Media3 commonly reports a short BUFFERING state immediately after seekTo(). Keep the loading
    // animation suppressed across that transient state so releasing the pointer stays on the live
    // equalizer and returns smoothly to the post-seek FFT instead of switching visual modes.
    LaunchedEffect(trackKey, seekReleaseGeneration) {
        if (seekReleaseGeneration == 0) return@LaunchedEffect
        delay(750L)
        suppressLoadingWaveform = false
    }
    val effectiveLoading = isLoading && !suppressLoadingWaveform

    val pointerInteraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "waveformPointerInteraction",
    )
    val playbackEnergy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isPlaying) 220 else 460,
            easing = FastOutSlowInEasing,
        ),
        label = "waveformPlaybackEnergy",
    )

    // The loading indicator is height-driven only: one tall bar with symmetric, progressively
    // shorter neighbors travels from the first bar to the last, pauses, then repeats. The old red
    // shimmer color is intentionally not used.
    val loadingTravel = remember { Animatable(0f) }
    LaunchedEffect(effectiveLoading, shimmerDurationMs) {
        if (!effectiveLoading) {
            loadingTravel.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            loadingTravel.snapTo(0f)
            loadingTravel.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = shimmerDurationMs.coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
            delay(320L)
        }
    }

    val density = LocalDensity.current
    val requestedBarWidthPx = with(density) { barWidth.toPx() }
    val pointerRadiusPx = with(density) { pointerRadius.toPx() }
    val minimumHalfHeightPx = with(density) { 1.15.dp.toPx() }
    val interactionPointerHeightPx = with(density) { 23.dp.toPx() }
    val visualHeight = 58.dp

    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val rangeLength = valueRange.endInclusive - valueRange.start
    val progress = if (rangeLength <= 0f) {
        0f
    } else {
        ((coercedValue - valueRange.start) / rangeLength).coerceIn(0f, 1f)
    }

    Box(modifier = modifier.height(visualHeight)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val startX = pointerRadiusPx
            val endX = (size.width - pointerRadiusPx).coerceAtLeast(startX)
            val drawableWidth = (endX - startX).coerceAtLeast(0f)
            val slotWidth = if (resolvedBarCount > 1) {
                drawableWidth / (resolvedBarCount - 1).toFloat()
            } else {
                drawableWidth
            }
            val actualBarWidth = requestedBarWidthPx
                .coerceAtMost((slotWidth * 0.58f).coerceAtLeast(1f))
                .coerceAtLeast(1f)
            val maxHalfHeight = size.height * 0.42f
            val pointerX = startX + drawableWidth * progress
            val pointerIndex = progress * (resolvedBarCount - 1).toFloat()
            val decayWindow = decayBarCount.coerceAtLeast(1).toFloat()
            val loadingCenterIndex = loadingTravel.value * (resolvedBarCount - 1).toFloat()
            val loadingWingSpan = 5.5f

            for (index in 0 until resolvedBarCount) {
                val normalizedX = if (resolvedBarCount <= 1) {
                    0f
                } else {
                    index.toFloat() / (resolvedBarCount - 1).toFloat()
                }
                val x = startX + drawableWidth * normalizedX

                val halfHeight: Float
                val color: Color

                if (effectiveLoading) {
                    val distanceFromLoadingPeak = abs(index.toFloat() - loadingCenterIndex)
                    val linearStrength =
                        (1f - distanceFromLoadingPeak / loadingWingSpan).coerceIn(0f, 1f)
                    val loadingStrength = smoothStep(linearStrength)
                    halfHeight = if (loadingStrength > 0f) {
                        minimumHalfHeightPx +
                            (maxHalfHeight * 0.92f - minimumHalfHeightPx) * loadingStrength
                    } else {
                        minimumHalfHeightPx
                    }
                    color = if (loadingStrength > 0f) activeBarColor else inactiveBarColor
                } else if (normalizedX <= progress && progress > 0f) {
                    val distanceFromPointer = (pointerIndex - index.toFloat()).coerceAtLeast(0f)
                    val linearDecay = (distanceFromPointer / decayWindow).coerceIn(0f, 1f)
                    val decay = smoothStep(linearDecay)
                    val liveHeight = stableLiveProfile[index].coerceIn(0f, 1f)
                    halfHeight = (liveHeight * maxHalfHeight * decay * playbackEnergy)
                        .coerceAtLeast(minimumHalfHeightPx)
                    color = activeBarColor
                } else {
                    halfHeight = minimumHalfHeightPx
                    color = inactiveBarColor
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        x = x - actualBarWidth / 2f,
                        y = centerY - halfHeight,
                    ),
                    size = Size(actualBarWidth, halfHeight * 2f),
                    cornerRadius = CornerRadius(actualBarWidth / 2f),
                )
            }

            if (!effectiveLoading) {
                val pointerWidth = lerpFloat(
                    pointerRadiusPx * 2f,
                    (requestedBarWidthPx * 1.35f).coerceAtLeast(2f),
                    pointerInteraction,
                )
                val pointerHeight = lerpFloat(
                    pointerRadiusPx * 2f,
                    interactionPointerHeightPx,
                    pointerInteraction,
                )
                drawRoundRect(
                    color = if (enabled) pointerColor else pointerColor.copy(alpha = 0.45f),
                    topLeft = Offset(
                        x = pointerX - pointerWidth / 2f,
                        y = centerY - pointerHeight / 2f,
                    ),
                    size = Size(pointerWidth, pointerHeight),
                    cornerRadius = CornerRadius(pointerWidth / 2f),
                )
            }
        }

        Slider(
            value = coercedValue,
            onValueChange = { newValue ->
                suppressLoadingWaveform = true
                onValueChange(newValue)
            },
            enabled = enabled && !effectiveLoading,
            valueRange = valueRange,
            onValueChangeFinished = {
                onValueChangeFinished?.invoke()
                seekReleaseGeneration += 1
            },
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxSize(),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

private fun expandLiveSpectrum(bands: FloatArray, barCount: Int): FloatArray {
    if (bands.isEmpty()) return FloatArray(barCount)
    return FloatArray(barCount) { index -> bands[index % bands.size].coerceIn(0f, 1f) }
}

private fun smoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)
