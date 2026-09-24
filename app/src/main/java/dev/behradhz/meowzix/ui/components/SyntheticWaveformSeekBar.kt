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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Lightweight deterministic waveform seek bar.
 *
 * No audio bytes are decoded for this UI. A stable synthetic waveform is derived from [trackKey],
 * so a track keeps the same visual shape across recompositions and app sessions. While playback is
 * active, only the played bars animate and the last bars decay into the seek pointer. Loading uses
 * the same waveform as the progress indicator: a red shimmer sweeps across it instead of showing a
 * separate spinner.
 */
@Composable
fun SyntheticWaveformSeekBar(
    trackKey: String,
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
    animationDurationMs: Int = 820,
    shimmerDurationMs: Int = 1_050,
) {
    val resolvedBarCount = barCount.coerceAtLeast(12)
    val waveform = remember(trackKey, resolvedBarCount) {
        buildSyntheticWaveform(trackKey, resolvedBarCount)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed
    val interactionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "waveformSeekInteraction",
    )

    val phase = remember { Animatable(0f) }
    val shouldAnimateBars = enabled && isPlaying && !isLoading && !isInteracting
    LaunchedEffect(shouldAnimateBars, animationDurationMs) {
        if (!shouldAnimateBars) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        val fullCycle = (2.0 * PI).toFloat()
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(
                targetValue = fullCycle,
                animationSpec = tween(
                    durationMillis = animationDurationMs.coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
        }
    }

    val shimmer = remember { Animatable(-0.24f) }
    LaunchedEffect(isLoading, shimmerDurationMs) {
        if (!isLoading) {
            shimmer.snapTo(-0.24f)
            return@LaunchedEffect
        }
        while (true) {
            shimmer.snapTo(-0.24f)
            shimmer.animateTo(
                targetValue = 1.24f,
                animationSpec = tween(
                    durationMillis = shimmerDurationMs.coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
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

            waveform.forEachIndexed { index, baseHeight ->
                val normalizedX = if (resolvedBarCount <= 1) {
                    0f
                } else {
                    index.toFloat() / (resolvedBarCount - 1).toFloat()
                }
                val x = startX + drawableWidth * normalizedX

                val halfHeight: Float
                val color: Color

                if (isLoading) {
                    halfHeight = (baseHeight * maxHalfHeight * 0.72f)
                        .coerceAtLeast(minimumHalfHeightPx)
                    val shimmerDistance = abs(normalizedX - shimmer.value)
                    val shimmerStrength = (1f - shimmerDistance / 0.16f).coerceIn(0f, 1f)
                    color = if (shimmerStrength > 0.001f) {
                        loadingShimmerColor.copy(alpha = 0.28f + 0.72f * shimmerStrength)
                    } else {
                        inactiveBarColor.copy(alpha = 0.34f)
                    }
                } else if (normalizedX <= progress && progress > 0f) {
                    val distanceFromPointer = (pointerIndex - index.toFloat()).coerceAtLeast(0f)
                    val decay = (distanceFromPointer / decayWindow).coerceIn(0f, 1f)
                    val activity = if (shouldAnimateBars) {
                        val oscillator = (
                            sin(
                                phase.value +
                                    index * 0.83f +
                                    baseHeight * PI.toFloat(),
                            ) + 1f
                        ) / 2f
                        0.56f + 0.44f * oscillator
                    } else {
                        0.10f
                    }
                    halfHeight = (baseHeight * maxHalfHeight * activity * decay)
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

            if (!isLoading) {
                val pointerWidth = lerpFloat(
                    pointerRadiusPx * 2f,
                    (requestedBarWidthPx * 1.35f).coerceAtLeast(2f),
                    interactionFraction,
                )
                val pointerHeight = lerpFloat(
                    pointerRadiusPx * 2f,
                    interactionPointerHeightPx,
                    interactionFraction,
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
            onValueChange = onValueChange,
            enabled = enabled && !isLoading,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
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

private val WaveformTemplates = arrayOf(
    floatArrayOf(
        0.24f, 0.38f, 0.62f, 0.84f, 0.56f, 0.34f, 0.68f, 0.92f,
        0.74f, 0.42f, 0.58f, 0.78f, 0.48f, 0.30f, 0.64f, 0.86f,
    ),
    floatArrayOf(
        0.54f, 0.82f, 0.66f, 0.36f, 0.72f, 0.46f, 0.88f, 0.60f,
        0.34f, 0.52f, 0.76f, 0.94f, 0.62f, 0.40f, 0.70f, 0.50f,
    ),
    floatArrayOf(
        0.30f, 0.46f, 0.58f, 0.76f, 0.90f, 0.72f, 0.52f, 0.64f,
        0.82f, 0.56f, 0.38f, 0.68f, 0.84f, 0.60f, 0.44f, 0.74f,
    ),
)

private fun buildSyntheticWaveform(trackKey: String, barCount: Int): FloatArray {
    val seed = trackKey.hashCode()
    val template = WaveformTemplates[(seed and Int.MAX_VALUE) % WaveformTemplates.size]
    val output = FloatArray(barCount)
    if (barCount == 1) {
        output[0] = template.first()
        return output
    }

    for (index in 0 until barCount) {
        val templatePosition =
            index.toFloat() / (barCount - 1).toFloat() * (template.size - 1).toFloat()
        val lower = floor(templatePosition).toInt().coerceIn(0, template.lastIndex)
        val upper = (lower + 1).coerceAtMost(template.lastIndex)
        val fraction = templatePosition - lower.toFloat()
        val interpolated = lerpFloat(template[lower], template[upper], fraction)
        val jitter = deterministicNoise(seed, index) * 0.10f
        output[index] = (interpolated + jitter).coerceIn(0.18f, 0.96f)
    }
    return output
}

private fun deterministicNoise(seed: Int, index: Int): Float {
    var value = seed xor (index * -1_640_531_527)
    value = value xor (value shl 13)
    value = value xor (value ushr 17)
    value = value xor (value shl 5)
    val normalized = (value and Int.MAX_VALUE).toFloat() / Int.MAX_VALUE.toFloat()
    return normalized * 2f - 1f
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)
