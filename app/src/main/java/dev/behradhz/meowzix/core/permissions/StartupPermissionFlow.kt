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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Request OS permissions on the first launch only; a denial never blocks normal playback. */
internal enum class StartupPermissionStage {
    LOCAL_AUDIO,
    LIVE_SPECTRUM,
    OVERLAY,
    COMPLETE;

    fun next(): StartupPermissionStage = when (this) {
        LOCAL_AUDIO -> LIVE_SPECTRUM
        LIVE_SPECTRUM -> OVERLAY
        OVERLAY, COMPLETE -> COMPLETE
    }

    companion object {
        fun restore(value: String?): StartupPermissionStage =
            // The old flow stored INTRO before showing its pre-permission dialog.
            // Migrate that state to the first direct OS permission request.
            entries.firstOrNull { it.name == value } ?: LOCAL_AUDIO
    }
}

@Composable
internal fun StartupPermissionFlow() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(
            "meowzix_first_launch_permissions", Context.MODE_PRIVATE,
        )
    }
    var stage by remember {
        mutableStateOf(StartupPermissionStage.restore(prefs.getString("stage", null)))
    }

    fun advance(next: StartupPermissionStage) {
        // Record denials as well as grants: never nag at the next app launch.
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
                if (ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
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
                if (!Settings.canDrawOverlays(context)) {
                    // SYSTEM_ALERT_WINDOW has no runtime dialog. Go directly to the
                    // Android special-access screen; on Android 11+ the package URI
                    // is ignored and the system may display the list of apps.
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    }
                    withFrameNanos { }
                    // Mark the flow complete before leaving the app so returning
                    // from Settings does not reopen it, even when access was denied.
                    advance(StartupPermissionStage.COMPLETE)
                    runCatching { context.startActivity(intent) }
                } else {
                    advance(StartupPermissionStage.COMPLETE)
                }
            }
            StartupPermissionStage.COMPLETE -> Unit
        }
    }
}
