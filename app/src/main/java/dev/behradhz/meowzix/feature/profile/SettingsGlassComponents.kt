package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.feature.library.PlaylistGlassPanel
import dev.chrisbanes.haze.HazeState

/** A section has one glass layer; rows never blur other rows or add nested cards. */
@Composable
internal fun SettingsGlassSection(
    hazeState: HazeState,
    title: String,
    description: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PlaylistGlassPanel(hazeState, modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) { content() }
        }
    }
}

@Composable
internal fun SettingsGlassHero(hazeState: HazeState, connected: Boolean, smartEnabled: Boolean, downloads: DownloadSettingsSummary) {
    PlaylistGlassPanel(hazeState, modifier = Modifier.fillMaxWidth(), accented = true, radius = 30.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Make Meowzix yours.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsStatusChip(if (connected) "Telegram connected" else "Telegram offline", Modifier.weight(1f))
                SettingsStatusChip(if (smartEnabled) "Smart on" else "Smart off", Modifier.weight(1f))
            }
            Text(
                "${downloads.activeCount} active downloads · ${downloads.pinnedCount} offline files",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsStatusChip(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun SettingsLeadingIcon(icon: ImageVector, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SettingsRowLabel(title: String, subtitle: String?, modifier: Modifier = Modifier, danger: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLeadingIcon(icon)
        SettingsRowLabel(title, subtitle, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun SettingsChoiceRow(title: String, subtitle: String?, icon: ImageVector, current: String, options: List<String>, onChoice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsLeadingIcon(icon)
            SettingsRowLabel(title, subtitle, Modifier.weight(1f))
            Text(current, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.lowercase().replaceFirstChar(Char::uppercase)) }, onClick = {
                    expanded = false
                    onChoice(option)
                })
            }
        }
    }
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValue: (Float) -> Unit,
    enabled: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsLeadingIcon(icon)
            SettingsRowLabel(title, subtitle, Modifier.weight(1f))
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            enabled = enabled,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 5.dp).semantics {
                contentDescription = title
                stateDescription = valueLabel
            },
        )
    }
}

@Composable
internal fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLeadingIcon(icon)
        SettingsRowLabel(title, subtitle, Modifier.weight(1f))
        Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SettingsInfoRow(title: String, subtitle: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLeadingIcon(icon)
        SettingsRowLabel(title, subtitle, Modifier.weight(1f))
    }
}

@Composable
internal fun SettingsDangerRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLeadingIcon(icon, danger = true)
        SettingsRowLabel(title, subtitle, Modifier.weight(1f), danger = true)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
    }
}

internal enum class SettingsDangerAction(val title: String, val message: String, val confirm: String) {
    CLEAR_CACHE(
        "Clear temporary cache?",
        "Only evictable app-managed cache will be cleared. Pinned offline and MediaStore files are preserved.",
        "Clear cache",
    ),
    CLEAR_HISTORY(
        "Clear listening history?",
        "This deletes raw listening events and rebuild-derived state from that history. This cannot be undone.",
        "Clear history",
    ),
    RESET_PERSONALIZATION(
        "Reset personalization?",
        "Learned recommendation state and feedback will be reset. Your music library is preserved; listening history is handled separately.",
        "Reset",
    ),
    DISCONNECT_TELEGRAM(
        "Disconnect Telegram?",
        "Your Telegram session will be disconnected. Local and previously available offline music remain usable.",
        "Disconnect",
    ),
    CLEAR_FEEDBACK(
        "Clear recommendation feedback?",
        "All active recommendation feedback will be cleared. You can provide feedback again later.",
        "Clear feedback",
    ),
}
