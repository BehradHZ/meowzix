package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.ui.theme.LocalMeowzixLogoBackground

/**
 * One Settings destination replaces the old Profile screen and nested settings sheet.
 * Telegram authentication/source management stays in its existing independent route.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    var showEqualizer by remember { mutableStateOf(false) }
    var showFullscreenCat by remember { mutableStateOf(false) }

    UnifiedSettingsScreen(
        onBack = onBack,
        onOpenBrand = { showFullscreenCat = true },
        onOpenEqualizer = { showEqualizer = true },
        onOpenTelegram = onOpenTelegram,
        onOpenDownloads = onOpenOffline,
        onOpenHistory = onOpenHistory,
    )

    if (showFullscreenCat) SettingsBrandDialog(onDismiss = { showFullscreenCat = false })
    if (showEqualizer) EqualizerSheet(onDismiss = { showEqualizer = false })
}

@Composable
internal fun SettingsBrandDialog(onDismiss: () -> Unit) {
    val background = LocalMeowzixLogoBackground.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(background),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.meowzix_launcher_art),
                contentDescription = "Fullscreen Meowzix cat",
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentScale = ContentScale.Fit,
            )
            val foreground = if (background.luminance() > 0.45f) Color.Black else Color.White
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(foreground.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close fullscreen cat", tint = foreground)
            }
        }
    }
}
