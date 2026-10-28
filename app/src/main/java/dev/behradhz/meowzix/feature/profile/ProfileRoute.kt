package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.behradhz.meowzix.ui.theme.LocalMeowzixLogoBackground
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
import androidx.compose.ui.layout.ContentScale
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
    var showFullscreenCat by remember { mutableStateOf(false) }
    val logoBackground = LocalMeowzixLogoBackground.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 188.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("profile-header") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.size(88.dp).clickable(role = Role.Button) { showFullscreenCat = true },
                    shape = RoundedCornerShape(30.dp),
                    color = logoBackground,
                ) {
                    Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.meowzix_logo_transparent),
                            contentDescription = "Open Meowzix cat fullscreen",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
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
        item("telegram") {
            ProfileDestinationRow("Telegram", "Accounts, sources and Telegram music settings", Icons.Rounded.Cloud, onOpenTelegram)
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
    }

    if (showFullscreenCat) {
        Dialog(
            onDismissRequest = { showFullscreenCat = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(logoBackground),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.meowzix_logo_transparent),
                    contentDescription = "Fullscreen Meowzix cat",
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
                val closeTint = if (logoBackground.luminance() > 0.45f) Color.Black else Color.White
                IconButton(
                    onClick = { showFullscreenCat = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .background(
                            closeTint.copy(alpha = 0.12f),
                            CircleShape,
                        ),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close fullscreen cat", tint = closeTint)
                }
            }
        }
    }

    if (showEqualizer) EqualizerSheet(onDismiss = { showEqualizer = false })
    if (showSettings) {
        UnifiedSettingsSheet(
            onDismiss = { showSettings = false },
            onOpenEqualizer = {
                showSettings = false
                showEqualizer = true
            },
            onOpenTelegram = {
                showSettings = false
                onOpenTelegram()
            },
            onOpenDownloads = {
                showSettings = false
                onOpenOffline()
            },
        )
    }
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
