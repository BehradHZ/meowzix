package dev.behradhz.meowzix.feature.telegram

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.ui.components.TelegramGlassChrome
import dev.behradhz.meowzix.ui.components.TelegramGlassFrame
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID

/**
 * Direct notification PendingIntent target. Acts only as a user-visible permission gateway.
 * When access is granted, starts the independent WindowManager overlay and finishes, restoring
 * the app the user was using. The old in-app picker remains the opt-in fallback when denied.
 */
@AndroidEntryPoint
class TelegramForwardActivity : ComponentActivity() {
    private var inAppMode by mutableStateOf(false)
    private var permissionError by mutableStateOf<String?>(null)

    private val overlayPermissionRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (launchOverlayIfAllowed()) finish()
            else permissionError = "Overlay access is not enabled. You can retry or use the in-app picker."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (launchOverlayIfAllowed()) {
            finish()
            return
        }
        setContent {
            MeowzixTheme(darkTheme = true) {
                if (!inAppMode) {
                    PermissionGateway(
                        message = permissionError,
                        onGrant = ::requestOverlayPermission,
                        onFallback = { inAppMode = true },
                        onClose = ::finish,
                    )
                } else {
                    InAppForwardPicker()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (launchOverlayIfAllowed()) finish()
    }

    private fun launchOverlayIfAllowed(): Boolean {
        if (!Settings.canDrawOverlays(this)) return false
        return runCatching {
            startService(
                TelegramForwardOverlayService.intent(
                    this,
                    intent?.getStringExtra(EXTRA_TRACK_ID),
                    intent?.getStringExtra(EXTRA_TITLE),
                    intent?.getStringExtra(EXTRA_ARTIST),
                ),
            ) != null
        }.getOrElse {
            permissionError = "Unable to display the floating picker. Use the in-app option."
            false
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                data = Uri.parse("package:$packageName")
            }
        }
        runCatching { overlayPermissionRequest.launch(intent) }.onFailure {
            permissionError = "System overlay settings are unavailable. Use the in-app picker."
        }
    }

    @Composable
    private fun InAppForwardPicker() {
        val viewModel: NowPlayingViewModel = hiltViewModel()
        val playbackState by viewModel.state.collectAsStateWithLifecycle()
        val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
        var openRequested by remember { mutableStateOf(false) }
        val gate = remember { ForwardPickerVisibilityGate() }

        LaunchedEffect(playbackState.currentTrack?.id) {
            val pinned = intent?.getStringExtra(EXTRA_TRACK_ID)?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            val id = pinned ?: playbackState.currentTrack?.id
            if (!openRequested && id != null) {
                openRequested = true
                viewModel.openForwardPickerForTrack(id)
            }
        }
        LaunchedEffect(forwardState.isOpen) {
            if (gate.shouldFinish(forwardState.isOpen)) finish()
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val selectedTrack = forwardState.track
            if (forwardState.isOpen && selectedTrack != null) {
                TelegramForwardSheet(
                    track = selectedTrack,
                    query = forwardState.query,
                    chats = forwardState.chats,
                    isSearching = forwardState.isSearching,
                    isSending = forwardState.isSending,
                    errorMessage = forwardState.errorMessage,
                    defaults = forwardState.defaults,
                    onQueryChange = viewModel::searchForwardChats,
                    onForward = viewModel::forwardCurrentTrack,
                    onDismiss = viewModel::dismissForwardPicker,
                )
            } else if (!gate.hasShownPicker) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xED171716),
                    contentColor = Color.White,
                    modifier = Modifier.padding(24.dp).border(
                        1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(3.dp), strokeWidth = 2.dp)
                        Text("Preparing Telegram sharing…")
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionGateway(
        message: String?,
        onGrant: () -> Unit,
        onFallback: () -> Unit,
        onClose: () -> Unit,
    ) {
        val haze = rememberHazeState()
        Box(
            modifier = Modifier.fillMaxSize()
                .background(TelegramGlassChrome.base)
                .background(TelegramGlassChrome.backdrop)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            TelegramGlassFrame(hazeState = haze, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(48.dp), shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.primary) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Send, contentDescription = null)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Floating Telegram share", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text("Display over other apps", style = MaterialTheme.typography.bodySmall,
                                color = TelegramGlassChrome.muted)
                        }
                        TextButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }
                    Text(
                        "Allow Meowzix to show the Forward picker on top of your current app. " +
                            "Android will open its special-access settings; you can turn this off at any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    message?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onGrant, shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Enable floating sharing")
                    }
                    OutlinedButton(onClick = onFallback, shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Continue with in-app picker")
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TRACK_ID = "forward_track_id"
        private const val EXTRA_TITLE = "forward_track_title"
        private const val EXTRA_ARTIST = "forward_track_artist"

        fun intent(context: Context, trackId: String?, title: String?, artist: String?): Intent =
            Intent(context, TelegramForwardActivity::class.java).apply {
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
    }
}
