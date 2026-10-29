package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.behradhz.meowzix.domain.playback.SleepTimerMode
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import kotlin.math.roundToInt

/**
 * The same layered tinted glass, soft corners and thin border as More options.
 * A platform Dialog owns focus and outside-dismiss; unlike a generic popup it keeps the
 * timer accessible while the user scrubs the slider. Its visual glass tint remains visible
 * when a cross-window backdrop blur cannot be captured.
 */
@Composable
fun SleepTimerGlassDialog(
    state: SleepTimerState,
    endOfTrackEnabled: Boolean,
    onDismiss: () -> Unit,
    onSetMinutes: (Int, Boolean) -> Unit,
    onEndOfTrack: (Boolean) -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    // Blend the current track's accent into the middle glass layer rather than using
    // the untinted surfaceVariant; both the header controls and panel now match.
    val accentGlass = colors.primary.copy(alpha = 0.16f).compositeOver(colors.surface)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.90f)
                .heightIn(max = 700.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.surface.copy(alpha = 0.94f),
                            accentGlass.copy(alpha = 0.97f),
                            colors.surface.copy(alpha = 0.995f),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .testTag("glass-sleep-timer-dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sleep timer", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text("Choose when playback stops", style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("sleep-timer-close")) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close sleep timer")
                }
            }
            SleepTimerControls(
                state = state,
                endOfTrackEnabled = endOfTrackEnabled,
                onSetMinutes = onSetMinutes,
                onEndOfTrack = onEndOfTrack,
                onExtend = onExtend,
                onCancel = onCancel,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).testTag("sleep-timer-done"),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
fun SleepTimerControls(
    state: SleepTimerState,
    endOfTrackEnabled: Boolean,
    onSetMinutes: (Int, Boolean) -> Unit,
    onEndOfTrack: (Boolean) -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fade by remember { mutableStateOf(true) }
    var customMinutes by remember { mutableFloatStateOf(90f) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.primary.copy(alpha = 0.09f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.primary,
            )
            Column(Modifier.weight(1f)) {
                Text("Current timer", style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant)
                Text(
                    sleepTimerStatusLabel(state),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.testTag("sleep-timer-status"),
                )
            }
        }

        Text("Quick durations", style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SleepTimerPreset(15, Modifier.weight(1f)) { onSetMinutes(15, fade) }
            SleepTimerPreset(30, Modifier.weight(1f)) { onSetMinutes(30, fade) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SleepTimerPreset(45, Modifier.weight(1f)) { onSetMinutes(45, fade) }
            SleepTimerPreset(60, Modifier.weight(1f)) { onSetMinutes(60, fade) }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(colors.onSurface.copy(alpha = 0.045f))
                .border(1.dp, colors.onSurface.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("Custom duration · ${customMinutes.roundToInt()} min",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Slider(
                value = customMinutes,
                onValueChange = { customMinutes = it },
                valueRange = 5f..180f,
                modifier = Modifier.testTag("sleep-timer-custom-slider"),
            )
            Button(
                onClick = { onSetMinutes(customMinutes.roundToInt(), fade) },
                modifier = Modifier.fillMaxWidth().testTag("sleep-timer-custom-start"),
            ) {
                Text("Start custom timer")
            }
        }

        Surface(
            onClick = { onEndOfTrack(fade) },
            enabled = endOfTrackEnabled,
            modifier = Modifier.fillMaxWidth().testTag("sleep-timer-end-of-track"),
            shape = RoundedCornerShape(14.dp),
            color = colors.primary.copy(alpha = if (endOfTrackEnabled) 0.11f else 0.04f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, colors.primary.copy(alpha = if (endOfTrackEnabled) 0.20f else 0.06f),
            ),
        ) {
            Text(
                if (endOfTrackEnabled) "Stop at end of current track" else "No current track",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (endOfTrackEnabled) colors.onSurface else colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Short fade", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text("Fade for the final 5 seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            Switch(
                checked = fade,
                onCheckedChange = { fade = it },
                modifier = Modifier.testTag("sleep-timer-fade"),
            )
        }

        if (state.active) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.mode == SleepTimerMode.DURATION) {
                    TextButton(onClick = onExtend, modifier = Modifier.testTag("sleep-timer-extend")) {
                        Text("+15 min")
                    }
                }
                TextButton(onClick = onCancel, modifier = Modifier.testTag("sleep-timer-cancel")) {
                    Text("Cancel timer", color = colors.error)
                }
            }
        }
    }
}

@Composable
private fun SleepTimerPreset(minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).testTag("sleep-timer-preset-$minutes"),
        shape = RoundedCornerShape(14.dp),
        color = colors.primary.copy(alpha = 0.105f),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.17f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
            Text("$minutes min", style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface, fontWeight = FontWeight.Medium)
        }
    }
}

fun sleepTimerStatusLabel(state: SleepTimerState): String = when (state.mode) {
    SleepTimerMode.OFF -> "Off"
    SleepTimerMode.END_OF_TRACK -> "End of current track"
    SleepTimerMode.DURATION -> {
        val totalSeconds = (state.remainingMs.coerceAtLeast(0L) + 999L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        if (hours > 0L) "%d:%02d:%02d remaining".format(hours, minutes, seconds)
        else "%d:%02d remaining".format(minutes, seconds)
    }
}
