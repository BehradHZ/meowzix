package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.EqualizerPreset

/** The equalizer expands in the sound settings rather than opening another destination. */
@Composable
internal fun SettingsEqualizerControls(
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxWidth().testTag("settings-equalizer-controls")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Enabled", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.enabled,
                onCheckedChange = viewModel::setEnabled,
                enabled = state.supported,
                modifier = Modifier.semantics { contentDescription = "Enable equalizer" },
            )
        }
        if (!state.supported || state.bands.isEmpty()) {
            Text(
                if (!state.supported) "Equalizer unavailable for this audio output"
                else "Equalizer activates when an audio session is available",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    EqualizerPreset.FLAT to "Flat",
                    EqualizerPreset.BASS to "Bass",
                    EqualizerPreset.VOCAL to "Vocal",
                    EqualizerPreset.BRIGHT to "Bright",
                ).forEach { (preset, label) ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text(label) },
                    )
                }
            }
            state.bands.forEach { band ->
                EqualizerBandSlider(
                    band = band,
                    enabled = state.enabled,
                    onCommit = { level -> viewModel.setBandLevel(band.index, level) },
                )
            }
            if (state.preset == EqualizerPreset.CUSTOM) {
                Text(
                    "Custom",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        state.errorMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
