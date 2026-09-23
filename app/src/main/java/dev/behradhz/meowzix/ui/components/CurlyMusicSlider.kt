package dev.behradhz.meowzix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material-style squiggly music seek bar.
 *
 * The played portion animates as a sine wave while playback is active. During scrubbing or when
 * playback is paused the wave settles into a straight line, matching Android's media-control
 * behavior while keeping the actual seek interaction delegated to Material3 [Slider].
 */
@Composable
fun CurlyMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
    thumbColor: Color = MaterialTheme.colorScheme.onSurface,
    trackHeight: Dp = 5.dp,
    thumbRadius: Dp = 7.dp,
    waveAmplitude: Dp = 3.dp,
    waveLength: Dp = 42.dp,
    waveAnimationDurationMs: Int = 1_250,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val interactionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "curlySeekInteraction",
    )
    val animatedAmplitude by animateDpAsState(
        targetValue = if (enabled && isPlaying && !isInteracting) waveAmplitude else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "curlySeekAmplitude",
    )

    val phase = remember { Animatable(0f) }
    val shouldAnimateWave = enabled && isPlaying && !isInteracting && waveAnimationDurationMs > 0
    LaunchedEffect(shouldAnimateWave, waveAnimationDurationMs) {
        if (!shouldAnimateWave) return@LaunchedEffect
        val fullCycle = (2.0 * PI).toFloat()
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(
                targetValue = fullCycle,
                animationSpec = tween(
                    durationMillis = waveAnimationDurationMs,
                    easing = LinearEasing,
                ),
            )
        }
    }

    val density = LocalDensity.current
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val amplitudePx = with(density) { animatedAmplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx().coerceAtLeast(1f) }
    val thumbInteractionHeightPx = with(density) { 23.dp.toPx() }
    val thumbGapPx = with(density) { 3.dp.toPx() }
    val visualHeight = 40.dp
    val wavePath = remember { Path() }

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
            val startX = thumbRadiusPx
            val endX = (size.width - thumbRadiusPx).coerceAtLeast(startX)
            val width = (endX - startX).coerceAtLeast(0f)
            val thumbCenterX = startX + width * progress
            val activeEndX = (thumbCenterX - thumbGapPx * interactionFraction).coerceAtLeast(startX)

            if (thumbCenterX < endX) {
                drawLine(
                    color = inactiveTrackColor,
                    start = Offset(thumbCenterX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round,
                )
            }

            if (activeEndX > startX) {
                if (amplitudePx > 0.05f) {
                    val angularFrequency = (2.0 * PI / waveLengthPx).toFloat()
                    val step = (waveLengthPx / 18f).coerceIn(1.2f, 4.5f)
                    wavePath.reset()

                    fun waveY(x: Float): Float =
                        centerY + amplitudePx * sin(angularFrequency * (x - startX) + phase.value)

                    var previousX = startX
                    var previousY = waveY(previousX)
                    wavePath.moveTo(previousX, previousY)
                    var x = previousX + step
                    while (x < activeEndX) {
                        val y = waveY(x)
                        val midX = (previousX + x) / 2f
                        val midY = (previousY + y) / 2f
                        wavePath.quadraticTo(previousX, previousY, midX, midY)
                        previousX = x
                        previousY = y
                        x += step
                    }
                    wavePath.quadraticTo(previousX, previousY, activeEndX, waveY(activeEndX))

                    drawPath(
                        path = wavePath,
                        color = activeTrackColor,
                        style = Stroke(
                            width = trackHeightPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                } else {
                    drawLine(
                        color = activeTrackColor,
                        start = Offset(startX, centerY),
                        end = Offset(activeEndX, centerY),
                        strokeWidth = trackHeightPx,
                        cap = StrokeCap.Round,
                    )
                }
            }

            val thumbWidth = lerpFloat(thumbRadiusPx * 2f, trackHeightPx * 1.2f, interactionFraction)
            val thumbHeight = lerpFloat(thumbRadiusPx * 2f, thumbInteractionHeightPx, interactionFraction)
            drawRoundRect(
                color = if (enabled) thumbColor else thumbColor.copy(alpha = 0.45f),
                topLeft = Offset(
                    x = thumbCenterX - thumbWidth / 2f,
                    y = centerY - thumbHeight / 2f,
                ),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2f),
            )
        }

        Slider(
            value = coercedValue,
            onValueChange = onValueChange,
            enabled = enabled,
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

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)
