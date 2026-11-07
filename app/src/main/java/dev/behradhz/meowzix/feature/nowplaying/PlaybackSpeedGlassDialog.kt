package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.ui.components.MeowzixGlassDialog
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val speedPresets = listOf(0.5f, 1f, 1.5f, 2f, 5f)
private val speedSteps = (5..50).map { it / 10f }

/** Fine speed selection with restrained dial feedback. */
@Composable
internal fun PlaybackSpeedGlassDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSpeed by remember { mutableFloatStateOf(currentSpeed.coerceIn(0.5f, 5f)) }
    var custom by remember { mutableStateOf(speedPresets.none { abs(it - selectedSpeed) < 0.001f }) }
    val colors = MaterialTheme.colorScheme
    val haptics = rememberMeowzixHaptics()
    val animatedSpeed by animateFloatAsState(
        targetValue = selectedSpeed,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "speed-dial-value",
    )

    fun updateSpeed(value: Float, fromDial: Boolean = false) {
        val bounded = value.coerceIn(0.5f, 5f)
        if (abs(selectedSpeed - bounded) > 0.001f) {
            val tick = fromDial && crossesHalfSpeedMark(selectedSpeed, bounded)
            selectedSpeed = bounded
            onSpeedChange(bounded)
            if (tick) haptics.perform(MeowzixHapticCue.Selection)
        }
    }

    val onDialChange by rememberUpdatedState<(Float) -> Unit>({ updateSpeed(it, fromDial = true) })

    MeowzixGlassDialog(onDismiss = onDismiss, tag = "playback-speed-dialog") { dismiss ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Playback speed", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
            }
            IconButton(
                onClick = {
                    haptics.perform(MeowzixHapticCue.LightClick)
                    dismiss()
                },
                modifier = Modifier.testTag("playback-speed-close"),
            ) { Icon(Icons.Rounded.Close, contentDescription = "Close playback speed") }
        }
        HorizontalDivider(color = colors.onSurface.copy(alpha = 0.10f))
        val choices = listOf("0.5×", "Normal", "1.5×", "2×", "5×", "Custom")
        choices.chunked(3).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { columnIndex, title ->
                    val index = rowIndex * 3 + columnIndex
                    val selected = if (index == 5) custom
                        else !custom && abs(selectedSpeed - speedPresets[index]) < 0.001f
                    SpeedChoice(
                        label = title,
                        selected = selected,
                        modifier = Modifier.weight(1f).testTag("speed-option-" + index),
                    ) {
                        haptics.perform(MeowzixHapticCue.LightClick)
                        if (index == 5) custom = !custom
                        else {
                            updateSpeed(speedPresets[index])
                            custom = false
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = custom,
            enter = fadeIn(tween(170)) + expandVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(190)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(colors.onSurface.copy(alpha = 0.045f), RoundedCornerShape(16.dp))
                    .border(1.dp, colors.onSurface.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .testTag("speed-custom-panel"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeedStepButton(
                        increase = false,
                        enabled = selectedSpeed > 0.5001f,
                        modifier = Modifier.testTag("speed-step-minus"),
                    ) {
                        haptics.perform(MeowzixHapticCue.LightClick)
                        updateSpeed(stepPlaybackSpeed(selectedSpeed, -1))
                    }
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val dialTrack = colors.onSurface.copy(alpha = 0.14f)
                        val dialAccent = colors.primary
                        val dialThumb = colors.surface
                        Canvas(
                            modifier = Modifier.matchParentSize()
                                .semantics { contentDescription = "Custom speed dial" }
                                .testTag("custom-speed-gauge")
                                .pointerInput(Unit) {
                                    detectTapGestures { point ->
                                        onDialChange(gaugeSpeed(point, size.width.toFloat(), size.height.toFloat()))
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { point ->
                                            onDialChange(gaugeSpeed(point, size.width.toFloat(), size.height.toFloat()))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            onDialChange(gaugeSpeed(
                                                change.position,
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                            ))
                                        },
                                    )
                                },
                        ) {
                            val center = Offset(size.width / 2f, size.height * 0.54f)
                            val radius = min(size.width, size.height) * 0.40f
                            val topLeft = Offset(center.x - radius, center.y - radius)
                            val arcSize = Size(radius * 2f, radius * 2f)
                            val stroke = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                            drawArc(dialTrack, 150f, 240f, false, topLeft, arcSize, style = stroke)
                            val fraction = gaugeFraction(animatedSpeed)
                            drawArc(dialAccent, 150f, 240f * fraction, false, topLeft, arcSize, style = stroke)
                            val angle = Math.toRadians(150.0 + fraction * 240.0)
                            val thumb = Offset(
                                center.x + radius * cos(angle).toFloat(),
                                center.y + radius * sin(angle).toFloat(),
                            )
                            drawCircle(dialAccent, radius = 10.dp.toPx(), center = thumb)
                            drawCircle(dialThumb, radius = 3.5.dp.toPx(), center = thumb)
                        }
                        Text(speedLabel(selectedSpeed), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = colors.onSurface,
                            modifier = Modifier.padding(top = 12.dp).testTag("playback-speed-current"))
                    }
                    SpeedStepButton(
                        increase = true,
                        enabled = selectedSpeed < 4.9999f,
                        modifier = Modifier.testTag("speed-step-plus"),
                    ) {
                        haptics.perform(MeowzixHapticCue.LightClick)
                        updateSpeed(stepPlaybackSpeed(selectedSpeed, 1))
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = colors.primary.copy(alpha = if (selected) 0.32f else 0.105f),
        animationSpec = tween(220), label = "speed-choice-fill",
    )
    val stroke by animateColorAsState(
        targetValue = colors.primary.copy(alpha = if (selected) 0.56f else 0.17f),
        animationSpec = tween(220), label = "speed-choice-stroke",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.975f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "speed-choice-scale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(14.dp),
        color = fill,
        border = BorderStroke(1.dp, stroke),
    ) {
        Box(Modifier.padding(horizontal = 3.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = colors.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun SpeedStepButton(
    increase: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(44.dp),
        shape = RoundedCornerShape(15.dp),
        color = colors.primary.copy(alpha = if (enabled) 0.16f else 0.05f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = if (enabled) 0.34f else 0.10f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (increase) Icons.Rounded.Add else Icons.Rounded.Remove,
                contentDescription = if (increase) "Increase speed" else "Decrease speed",
                tint = colors.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

internal fun crossesHalfSpeedMark(before: Float, after: Float): Boolean =
    (before.coerceIn(0.5f, 5f) * 2f + 0.0001f).toInt() !=
        (after.coerceIn(0.5f, 5f) * 2f + 0.0001f).toInt()

internal fun speedLabel(speed: Float): String {
    val formatted = String.format(Locale.US, if (speed % 1f == 0f) "%.0f" else "%.2f", speed)
    return formatted.trimEnd('0').trimEnd('.') + "×"
}

/** All supported increments are 0.1×. */
internal fun stepPlaybackSpeed(current: Float, direction: Int): Float {
    require(direction == -1 || direction == 1)
    val index = if (direction > 0) {
        speedSteps.indexOfFirst { it > current + 0.0001f }
            .let { if (it < 0) speedSteps.lastIndex else it }
    } else {
        speedSteps.indexOfLast { it < current - 0.0001f }.coerceAtLeast(0)
    }
    return speedSteps[index]
}

/** Reserve 60% of the arc for 0.5–2× and 40% for 2–5×. */
private fun gaugeFraction(speed: Float): Float =
    if (speed <= 2f) ((speed - 0.5f) / 1.5f * 0.6f).coerceIn(0f, 1f)
    else (0.6f + (speed - 2f) / 3f * 0.4f).coerceIn(0f, 1f)

/** Clockwise 240-degree dial, snapped to 0.1×. */
internal fun gaugeSpeed(point: Offset, width: Float, height: Float): Float {
    val dx = point.x - width / 2f
    val dy = point.y - height * 0.54f
    val degrees = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
    val fromStart = (degrees - 150f + 360f) % 360f
    val onArc = if (fromStart > 240f) {
        if (fromStart - 240f < 360f - fromStart) 240f else 0f
    } else fromStart
    val fraction = onArc / 240f
    val candidate = if (fraction <= 0.6f) 0.5f + fraction / 0.6f * 1.5f
        else 2f + (fraction - 0.6f) / 0.4f * 3f
    return speedSteps.minByOrNull { abs(it - candidate) } ?: 1f
}
