package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.playback.SleepTimerMode
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import kotlin.math.roundToInt

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

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            sleepTimerStatusLabel(state),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30).forEach { minutes ->
                OutlinedButton(onClick = { onSetMinutes(minutes, fade) }) { Text("$minutes min") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(45, 60).forEach { minutes ->
                OutlinedButton(onClick = { onSetMinutes(minutes, fade) }) { Text("$minutes min") }
            }
        }
        Text("Custom duration · " + customMinutes.roundToInt() + " min", style = MaterialTheme.typography.bodyMedium)
        Slider(value = customMinutes, onValueChange = { customMinutes = it }, valueRange = 5f..180f)
        Button(onClick = { onSetMinutes(customMinutes.roundToInt(), fade) }) { Text("Start custom timer") }
        OutlinedButton(onClick = { onEndOfTrack(fade) }, enabled = endOfTrackEnabled) {
            Text(if (endOfTrackEnabled) "Stop at end of current track" else "No current track")
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Short fade", style = MaterialTheme.typography.bodyMedium)
                Text("Fade for the final 5 seconds", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = fade, onCheckedChange = { fade = it })
        }
        if (state.active) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.mode == SleepTimerMode.DURATION) {
                    TextButton(onClick = onExtend) { Text("+15 min") }
                }
                TextButton(onClick = onCancel) { Text("Cancel timer") }
            }
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
