package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.R

@Composable
fun ProfileRoute(
    onOpenOffline: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    var showEqualizer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 188.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("profile-header") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(modifier = Modifier.size(88.dp), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)) {
                    Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.meowzix_logo),
                            contentDescription = "Meowzix",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                        )
                    }
                }
                Text("Meowzix", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text("Your music sources and listening activity", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
        item("settings") {
            ProfileDestinationRow(
                title = "Settings",
                subtitle = "Appearance, playback, recommendations, storage and backup",
                icon = Icons.Rounded.Settings,
                onClick = { showSettings = true },
            )
        }
        item("equalizer") {
            ProfileDestinationRow("Equalizer", "Device-aware frequency controls and presets", Icons.Rounded.Tune) { showEqualizer = true }
        }
        item("offline") {
            ProfileDestinationRow("Offline", "Downloaded tracks available without a connection", Icons.Rounded.DownloadForOffline, onOpenOffline)
        }
        item("history") {
            ProfileDestinationRow("Listening history", "Recent plays and personalization controls", Icons.Rounded.History, onOpenHistory)
        }
        item("telegram") {
            ProfileDestinationRow("Telegram", "Accounts, sources and Telegram music settings", Icons.Rounded.Cloud, onOpenTelegram)
        }
    }

    if (showEqualizer) EqualizerSheet(onDismiss = { showEqualizer = false })
    if (showSettings) UnifiedSettingsSheet(onDismiss = { showSettings = false })
}

@Composable
private fun ProfileDestinationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.53f))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }
    }
}
