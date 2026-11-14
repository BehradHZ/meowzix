package dev.behradhz.meowzix.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** One-time, non-blocking first-launch permission setup. The user can skip at either dialog. */
internal enum class StartupPermissionStage {
    INTRO,
    LOCAL_AUDIO,
    LIVE_SPECTRUM,
    OVERLAY,
    COMPLETE;

    fun next(): StartupPermissionStage = when (this) {
        INTRO -> LOCAL_AUDIO
        LOCAL_AUDIO -> LIVE_SPECTRUM
        LIVE_SPECTRUM -> OVERLAY
        OVERLAY, COMPLETE -> COMPLETE
    }

    companion object {
        fun restore(value: String?): StartupPermissionStage =
            entries.firstOrNull { it.name == value } ?: INTRO
    }
}

@Composable
internal fun StartupPermissionFlow() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences("meowzix_first_launch_permissions", Context.MODE_PRIVATE)
    }
    var stage by remember {
        mutableStateOf(StartupPermissionStage.restore(prefs.getString("stage", null)))
    }

    fun advance(next: StartupPermissionStage) {
        // Persist every result, including denials, so restarting the app never
        // automatically repeats previously answered prompts.
        prefs.edit().putString("stage", next.name).apply()
        stage = next
    }

    val localAudioRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { advance(StartupPermissionStage.LIVE_SPECTRUM) }
    val visualizerRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { advance(StartupPermissionStage.OVERLAY) }

    LaunchedEffect(stage) {
        when (stage) {
            StartupPermissionStage.LOCAL_AUDIO -> {
                val permission = AudioPermission.requiredPermission()
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    advance(stage.next())
                } else {
                    withFrameNanos { }
                    try {
                        localAudioRequest.launch(permission)
                    } catch (_: Exception) {
                        advance(StartupPermissionStage.LIVE_SPECTRUM)
                    }
                }
            }
            StartupPermissionStage.LIVE_SPECTRUM -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    advance(stage.next())
                } else {
                    withFrameNanos { }
                    try {
                        visualizerRequest.launch(Manifest.permission.RECORD_AUDIO)
                    } catch (_: Exception) {
                        advance(StartupPermissionStage.OVERLAY)
                    }
                }
            }
            StartupPermissionStage.OVERLAY -> {
                if (Settings.canDrawOverlays(context)) advance(StartupPermissionStage.COMPLETE)
            }
            StartupPermissionStage.INTRO, StartupPermissionStage.COMPLETE -> Unit
        }
    }

    if (stage == StartupPermissionStage.INTRO) {
        AlertDialog(
            onDismissRequest = { advance(StartupPermissionStage.COMPLETE) },
            title = { Text("Set up Meowzix") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Meowzix will ask for these permissions once:")
                    Text("Music access — find songs stored on this device.")
                    Text("Microphone — Android requires this for the live playback spectrum. Meowzix does not record your voice.")
                    Text("Display over other apps — show the floating music controls and optional lock-screen player.")
                }
            },
            confirmButton = {
                TextButton(onClick = { advance(StartupPermissionStage.LOCAL_AUDIO) }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { advance(StartupPermissionStage.COMPLETE) }) {
                    Text("Not now")
                }
            },
        )
    }

    if (stage == StartupPermissionStage.OVERLAY && !Settings.canDrawOverlays(context)) {
        AlertDialog(
            onDismissRequest = { advance(StartupPermissionStage.COMPLETE) },
            title = { Text("Allow floating music controls") },
            text = {
                Text("Enable 'Display over other apps' for Meowzix in Android Settings. This is optional; music playback works without it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    // Overlay is special app access, not a runtime permission dialog.
                    // On Android 11+ the OS opens its general overlay-permission list.
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    }
                    advance(StartupPermissionStage.COMPLETE)
                    runCatching { context.startActivity(intent) }
                }) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { advance(StartupPermissionStage.COMPLETE) }) {
                    Text("Not now")
                }
            },
        )
    }
}
