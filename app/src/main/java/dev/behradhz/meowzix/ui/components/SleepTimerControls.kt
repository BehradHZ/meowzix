package dev.behradhz.meowzix.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.behradhz.meowzix.domain.playback.SleepTimerMode
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import dev.behradhz.meowzix.domain.settings.SleepTimerPresetMode
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
    lastMinutes: Int,
    lastMode: SleepTimerPresetMode,
    onSetMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onExtend: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
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
                lastMinutes = lastMinutes,
                lastMode = lastMode,
                onSetMinutes = onSetMinutes,
                onEndOfTrack = onEndOfTrack,
                onExtend = onExtend,
                onSetEnabled = onSetEnabled,
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
    lastMinutes: Int,
    lastMode: SleepTimerPresetMode,
    onSetMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onExtend: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customMinutes by remember(lastMinutes) {
        mutableFloatStateOf(lastMinutes.coerceIn(5, 180).toFloat())
    }
    // A newly opened dialog is always compact, even if the last timer used a custom duration.
    var customExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }
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
                Text(
                    "Current timer",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    sleepTimerStatusLabel(state),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.testTag("sleep-timer-status"),
                )
                if (!state.active) {
                    Text(
                        "Last: " + sleepTimerPresetLabel(lastMode, lastMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = state.active,
                enabled = state.active || lastMode != SleepTimerPresetMode.END_OF_TRACK || endOfTrackEnabled,
                onCheckedChange = onSetEnabled,
                modifier = Modifier.testTag("sleep-timer-enabled"),
            )
        }

        // Four always-visible actions. Only Custom reveals the duration controls.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SleepTimerChoice(
                label = "End of\ntrack",
                selected = state.active && state.mode == SleepTimerMode.END_OF_TRACK,
                enabled = endOfTrackEnabled,
                tag = "sleep-timer-end-of-track",
                modifier = Modifier.weight(1f),
            ) {
                customExpanded = false
                onEndOfTrack()
            }
            SleepTimerChoice(
                label = "15 min",
                selected = state.active && state.mode == SleepTimerMode.DURATION &&
                    lastMinutes == 15 && !customExpanded,
                tag = "sleep-timer-preset-15",
                modifier = Modifier.weight(1f),
            ) {
                customExpanded = false
                onSetMinutes(15)
            }
            SleepTimerChoice(
                label = "60 min",
                selected = state.active && state.mode == SleepTimerMode.DURATION &&
                    lastMinutes == 60 && !customExpanded,
                tag = "sleep-timer-preset-60",
                modifier = Modifier.weight(1f),
            ) {
                customExpanded = false
                onSetMinutes(60)
            }
            SleepTimerChoice(
                label = "Custom",
                selected = customExpanded || (
                    state.active && state.mode == SleepTimerMode.DURATION &&
                        lastMinutes != 15 && lastMinutes != 60
                    ),
                tag = "sleep-timer-option-custom",
                modifier = Modifier.weight(1f),
            ) {
                customExpanded = !customExpanded
            }
        }

        AnimatedVisibility(visible = customExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.onSurface.copy(alpha = 0.045f))
                    .border(
                        1.dp,
                        colors.onSurface.copy(alpha = 0.09f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("sleep-timer-custom-panel"),
            ) {
                Text(
                    "Custom duration · " + customMinutes.roundToInt() + " min",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Slider(
                    value = customMinutes,
                    onValueChange = { customMinutes = it },
                    valueRange = 5f..180f,
                    modifier = Modifier.testTag("sleep-timer-custom-slider"),
                )
                Button(
                    onClick = { onSetMinutes(customMinutes.roundToInt()) },
                    modifier = Modifier.fillMaxWidth().testTag("sleep-timer-custom-start"),
                ) {
                    Text("Start custom timer")
                }
            }
        }

        if (state.active && state.mode == SleepTimerMode.DURATION) {
            TextButton(
                onClick = onExtend,
                modifier = Modifier.align(Alignment.End).testTag("sleep-timer-extend"),
            ) {
                Text("+15 min")
            }
        }
    }
}

@Composable
private fun SleepTimerChoice(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 54.dp).testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = colors.primary.copy(alpha = if (selected) 0.32f else 0.105f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.primary.copy(alpha = if (selected) 0.56f else 0.17f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 3.dp, vertical = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
            )
        }
    }
}

fun sleepTimerPresetLabel(mode: SleepTimerPresetMode, lastMinutes: Int): String = when (mode) {
    SleepTimerPresetMode.DURATION -> "${lastMinutes.coerceIn(5, 180)} min"
    SleepTimerPresetMode.END_OF_TRACK -> "End of current track"
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
