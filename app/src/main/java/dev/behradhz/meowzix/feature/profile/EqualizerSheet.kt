package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.EqualizerBand
import dev.behradhz.meowzix.domain.playback.EqualizerPreset
import java.util.Locale

@Composable
fun EqualizerSheet(
    onDismiss: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        when {
                            !state.supported -> "Not supported by this device or audio path"
                            state.bands.isEmpty() -> "Starts when an audio session is available"
                            else -> "${state.bands.size} device bands · ${state.preset.displayName()}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled,
                    enabled = state.supported,
                    modifier = Modifier.semantics {
                        contentDescription = "Equalizer"
                        stateDescription = if (state.enabled) "Enabled" else "Disabled"
                    },
                )
            }

            state.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    EqualizerPreset.FLAT,
                    EqualizerPreset.BASS,
                    EqualizerPreset.VOCAL,
                    EqualizerPreset.BRIGHT,
                ).forEach { preset ->
                    AssistChip(
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text(preset.displayName()) },
                        enabled = state.supported && state.bands.isNotEmpty(),
                    )
                }
                if (state.preset == EqualizerPreset.CUSTOM) {
                    AssistChip(onClick = {}, label = { Text("Custom") }, enabled = false)
                }
            }

            if (state.supported && state.bands.isNotEmpty()) {
                state.bands.forEach { band ->
                    EqualizerBandSlider(
                        band = band,
                        enabled = state.enabled,
                        onCommit = { value -> viewModel.setBandLevel(band.index, value) },
                    )
                }
                Button(onClick = viewModel::resetFlat, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset to Flat")
                }
            }
            Text(
                "Band centers and gain range come from the active Android audio effect. Positive boosts are capped at +6 dB for conservative headroom.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EqualizerBandSlider(
    band: EqualizerBand,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    var draft by remember(band.index, band.levelDb) { mutableFloatStateOf(band.levelDb) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatFrequency(band.centerFrequencyHz), style = MaterialTheme.typography.labelLarge)
            Text(formatDb(draft), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = draft.coerceIn(band.minLevelDb, band.maxLevelDb),
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) },
            valueRange = band.minLevelDb..band.maxLevelDb,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "${formatFrequency(band.centerFrequencyHz)} equalizer band"
                stateDescription = "${formatDb(draft)}, range ${formatDb(band.minLevelDb)} to ${formatDb(band.maxLevelDb)}"
            },
        )
    }
}

private fun EqualizerPreset.displayName(): String = when (this) {
    EqualizerPreset.FLAT -> "Flat"
    EqualizerPreset.BASS -> "Bass"
    EqualizerPreset.VOCAL -> "Vocal"
    EqualizerPreset.BRIGHT -> "Bright"
    EqualizerPreset.CUSTOM -> "Custom"
}

private fun formatFrequency(hz: Int): String = if (hz >= 1_000) {
    String.format(Locale.US, "%.1f kHz", hz / 1_000f)
} else {
    "$hz Hz"
}

private fun formatDb(db: Float): String = String.format(Locale.US, "%+.1f dB", db)
