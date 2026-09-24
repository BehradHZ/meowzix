package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.playback.AudioOutputRoute
import dev.behradhz.meowzix.domain.playback.AudioOutputState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputSheet(
    state: AudioOutputState,
    onTransferTo: (String) -> Unit,
    onSetRouteEnabled: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "Audio output",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = if (state.supportsSimultaneousPlayback) {
                    "Select multiple available outputs to play at the same time."
                } else {
                    "Choose where Meowzix should play audio."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            if (state.routes.isEmpty()) {
                Text(
                    text = "No audio outputs are currently available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                state.routes.forEach { route ->
                    AudioOutputRow(
                        route = route,
                        multiOutputAvailable = state.supportsSimultaneousPlayback,
                        onTransferTo = onTransferTo,
                        onSetRouteEnabled = onSetRouteEnabled,
                    )
                }
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioOutputRow(
    route: AudioOutputRoute,
    multiOutputAvailable: Boolean,
    onTransferTo: (String) -> Unit,
    onSetRouteEnabled: (String, Boolean) -> Unit,
) {
    val canToggleTogether = route.isSelected || route.canSelectTogether
    val useCheckbox = multiOutputAvailable && canToggleTogether

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (useCheckbox) {
                    if (!route.isSelected || route.canDeselect) {
                        onSetRouteEnabled(route.id, !route.isSelected)
                    }
                } else {
                    onTransferTo(route.id)
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (route.isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (multiOutputAvailable) {
                Text(
                    text = if (useCheckbox) "Can play together" else "Switch output",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (useCheckbox) {
            Checkbox(
                checked = route.isSelected,
                enabled = !route.isSelected || route.canDeselect,
                onCheckedChange = { checked -> onSetRouteEnabled(route.id, checked) },
            )
        } else {
            RadioButton(
                selected = route.isSelected,
                onClick = { onTransferTo(route.id) },
            )
        }
    }
}
