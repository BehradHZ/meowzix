package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.chrisbanes.haze.rememberHazeState
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val speedPresets = listOf(0.5f, 1f, 1.5f, 2f, 5f)

/** Five presets and Custom occupy two rows of three. Changes apply immediately. */
@Composable
internal fun PlaybackSpeedGlassDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSpeed by remember { mutableFloatStateOf(currentSpeed.coerceIn(0.5f, 5f)) }
    var custom by remember { mutableStateOf(speedPresets.none { it == selectedSpeed }) }
    val haze = rememberHazeState()
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(28.dp)

    fun updateSpeed(value: Float) {
        val bounded = value.coerceIn(0.5f, 5f)
        if (selectedSpeed != bounded) {
            selectedSpeed = bounded
            onSpeedChange(bounded)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Match the Sleep Timer floating glass surface without dimming the entire player.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
        GlassSurface(
            hazeState = haze,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            shape = shape,
            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp).testTag("playback-speed-dialog"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Playback speed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(speedLabel(selectedSpeed), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, color = accent)

                val choices = listOf("0.5×", "Normal", "1.5×", "2×", "5×", "Custom")
                choices.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { columnIndex, title ->
                            val index = rowIndex * 3 + columnIndex
                            val selected = if (index == 5) custom
                                else !custom && selectedSpeed == speedPresets[index]
                            Surface(
                                onClick = {
                                    if (index == 5) custom = true
                                    else {
                                        updateSpeed(speedPresets[index])
                                        custom = false
                                    }
                                },
                                modifier = Modifier.weight(1f).height(52.dp).testTag("speed-option-$index"),
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) accent.copy(alpha = 0.23f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                if (custom) {
                    Text("Drag the gauge to set a custom speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(contentAlignment = Alignment.Center) {
                        val background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
                        val thumbFill = MaterialTheme.colorScheme.surface
                        Canvas(
                            modifier = Modifier.size(226.dp)
                                .semantics { contentDescription = "Custom speed dial" }
                                .testTag("custom-speed-gauge")
                                .pointerInput(Unit) {
                                    detectTapGestures { point ->
                                        updateSpeed(gaugeSpeed(point, size.width.toFloat(), size.height.toFloat()))
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { point ->
                                            updateSpeed(gaugeSpeed(point, size.width.toFloat(), size.height.toFloat()))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            updateSpeed(gaugeSpeed(change.position,
                                                size.width.toFloat(), size.height.toFloat()))
                                        },
                                    )
                                },
                        ) {
                            val center = Offset(size.width / 2f, size.height * 0.54f)
                            val radius = min(size.width, size.height) * 0.405f
                            val topLeft = Offset(center.x - radius, center.y - radius)
                            val arcSize = Size(radius * 2f, radius * 2f)
                            val stroke = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round)
                            drawArc(background, 150f, 240f, false, topLeft, arcSize, style = stroke)
                            val fraction = (selectedSpeed - 0.5f) / 4.5f
                            drawArc(accent, 150f, 240f * fraction, false, topLeft, arcSize, style = stroke)
                            val angle = Math.toRadians(150.0 + fraction * 240.0)
                            val thumb = Offset(center.x + radius * cos(angle).toFloat(),
                                center.y + radius * sin(angle).toFloat())
                            drawCircle(accent, radius = 11.dp.toPx(), center = thumb)
                            drawCircle(thumbFill, radius = 4.dp.toPx(), center = thumb)
                        }
                        Text(speedLabel(selectedSpeed), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0.5×", style = MaterialTheme.typography.labelMedium)
                        Text("5×", style = MaterialTheme.typography.labelMedium)
                    }
                }

            }
        }
        }
    }
}

internal fun speedLabel(speed: Float): String {
    val formatted = String.format(Locale.US, if (speed % 1f == 0f) "%.0f" else "%.2f", speed)
    return formatted.trimEnd('0').trimEnd('.') + "×"
}

/** Clockwise 240-degree gauge, with 0.05x precision and hard limits. */
internal fun gaugeSpeed(point: Offset, width: Float, height: Float): Float {
    val dx = point.x - width / 2f
    val dy = point.y - height * 0.54f
    val degrees = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
    val fromStart = (degrees - 150f + 360f) % 360f
    val onArc = if (fromStart > 240f) {
        if (fromStart - 240f < 360f - fromStart) 240f else 0f
    } else fromStart
    return ((0.5f + onArc / 240f * 4.5f) * 20f).roundToInt().div(20f).coerceIn(0.5f, 5f)
}
