package dev.behradhz.meowzix.feature.library

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.behradhz.meowzix.core.permissions.AudioPermission

@Composable
fun LibraryRoute(
    onSwipePastEnd: () -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onOpenTelegram: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permission = AudioPermission.requiredPermission()
    fun granted(): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var wasGranted by remember(permission) { mutableStateOf(granted()) }

    // V2 owns the permission-request UI. This outer observer handles the persistence side effect:
    // if access is revoked while the app is backgrounded, stale MediaStore sources stop pretending
    // to be playable immediately when the app resumes.
    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = granted()
                if (wasGranted && !nowGranted) viewModel.localMediaPermissionRevoked()
                wasGranted = nowGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LibraryRouteV2(
        onSwipePastEnd = onSwipePastEnd,
        onOpenNowPlaying = onOpenNowPlaying,
        onOpenTelegram = onOpenTelegram,
        viewModel = viewModel,
    )
}
