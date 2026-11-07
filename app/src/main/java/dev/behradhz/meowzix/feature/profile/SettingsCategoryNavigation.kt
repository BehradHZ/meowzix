package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.feature.library.PlaylistGlassPanel
import dev.chrisbanes.haze.HazeState

/** The whole settings hierarchy is the index and one category detail; no nested destinations. */
internal enum class SettingsCategory(val title: String, val icon: ImageVector) {
    APPEARANCE("Appearance", Icons.Rounded.Palette),
    PLAYBACK("Playback", Icons.Rounded.PlayCircle),
    SOUND("Sound", Icons.Rounded.GraphicEq),
    DISCOVERY("Discovery", Icons.Rounded.AutoAwesome),
    CONNECTIONS("Telegram & downloads", Icons.Rounded.CloudDownload),
    DATA("Privacy & data", Icons.Rounded.Shield),
    INFORMATION("About & how it works", Icons.Rounded.Info),
}

@Composable
internal fun SettingsCategoryIndex(
    hazeState: HazeState,
    onOpen: (SettingsCategory) -> Unit,
) {
    PlaylistGlassPanel(hazeState, modifier = Modifier.fillMaxWidth(), radius = 26.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            SettingsCategory.entries.forEachIndexed { index, category ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
                        .testTag("settings-category-" + category.name.lowercase())
                        .clickable(role = Role.Button) { onOpen(category) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        category.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != SettingsCategory.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

/** All explanatory and read-only text is consolidated here, away from the controls. */
@Composable
internal fun SettingsInformationContent(
    hazeState: HazeState,
    telegramStatus: String,
    downloadsStatus: String,
    managedStorage: String,
    onOpenBrand: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsInformationCard(
            hazeState,
            "Privacy & recommendations",
            listOf(
                "Personalization is processed on this device. Smart recommendations use listening behavior and available local audio features; they do not require uploading your music library.",
                "Listening history controls whether new listening events are recorded for learning. Turning it off does not automatically erase older events. Clear listening history and Reset personalization are separate actions in Privacy & data.",
                "Local audio analysis runs only for audio the app can read. Active recommendation feedback can be undone under Discovery.",
            ),
        )
        SettingsInformationCard(
            hazeState,
            "Playback",
            listOf(
                "Gapless playback is automatic where supported. Progressive Telegram streams, equalizer, sleep timer, repeat-one, very short tracks, queue changes and preload failures can limit gapless playback or crossfade.",
                "Loudness normalization prioritizes ReplayGain. For locally readable audio, a conservative RMS-based estimate may be used; it is not an LUFS measurement.",
                "The Live song card requires notification permission on supported Android versions. Android determines whether it appears prominently on the lock screen or Always On Display.",
            ),
        )
        SettingsInformationCard(
            hazeState,
            "Telegram & storage",
            listOf(
                telegramStatus,
                downloadsStatus,
                managedStorage,
                "Only selected Telegram chats and channels are used as music sources. Deselecting a source does not remove completed offline downloads. Clearing incomplete transfers removes interrupted partial files, not completed songs.",
            ),
        )
        SettingsInformationCard(
            hazeState,
            "Backup & appearance",
            listOf(
                "Backup export excludes listening history unless you explicitly include it. Learned model weights are not exported. Restore merges data and does not replace your Telegram login or music library.",
                "Wallpaper colors require Android 12 or newer. Custom and preset colors are adapted into a readable Material palette. The full-color launcher icon uses a packaged matching background; the Android launcher controls monochrome themed icons.",
            ),
        )
        PlaylistGlassPanel(hazeState, modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenBrand)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("View Meowzix artwork", modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SettingsInformationCard(
    hazeState: HazeState,
    title: String,
    paragraphs: List<String>,
) {
    PlaylistGlassPanel(hazeState, modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            paragraphs.forEach { paragraph ->
                Text(
                    paragraph,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
