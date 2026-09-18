package dev.behradhz.meowzix.feature.library

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.core.permissions.AudioPermission
import dev.behradhz.meowzix.core.permissions.AudioPermissionStatus
import dev.behradhz.meowzix.core.permissions.audioPermissionStatus
import dev.behradhz.meowzix.domain.library.LibraryTrack
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.ui.components.TrackArtwork

@Composable
fun LibraryRoute(
    onOpenNowPlaying: () -> Unit,
    onOpenTelegram: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = AudioPermission.requiredPermission()

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasPermission()) }
    var permissionRequestAttempted by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequestAttempted = true
        permissionGranted = granted
    }

    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentlyGranted = hasPermission()
                if (permissionGranted && !currentlyGranted) permissionRequestAttempted = true
                permissionGranted = currentlyGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.refresh()
    }

    LibraryScreen(
        state = state,
        permissionStatus = audioPermissionStatus(permissionGranted, permissionRequestAttempted),
        onRequestPermission = { permissionLauncher.launch(permission) },
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        onRefresh = viewModel::refresh,
        onPlayTrack = { item ->
            viewModel.playTrack(item)
            onOpenNowPlaying()
        },
        onOpenNowPlaying = onOpenNowPlaying,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onOpenTelegram = onOpenTelegram,
    )
}

@Composable
private fun LibraryScreen(
    state: LibraryUiState,
    permissionStatus: AudioPermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (LibraryTrack) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayNext: (LibraryTrack) -> Unit,
    onAddToQueue: (LibraryTrack) -> Unit,
    onOpenTelegram: () -> Unit,
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Meowzix", style = MaterialTheme.typography.headlineLarge)
                    Text("Unified library", style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenTelegram) { Text("Telegram") }
                    if (state.playback.currentTrack != null) Button(onClick = onOpenNowPlaying) { Text("Now playing") }
                    if (permissionStatus == AudioPermissionStatus.GRANTED) {
                        Button(onClick = onRefresh, enabled = !state.isRefreshing) { Text("Rescan") }
                    }
                }
            }

            if (state.tracks.isNotEmpty() && permissionStatus != AudioPermissionStatus.GRANTED) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Device music access is off. Telegram tracks are still available in the library.", modifier = Modifier.weight(1f))
                    TextButton(onClick = if (permissionStatus == AudioPermissionStatus.REQUIRED) onRequestPermission else onOpenSettings) {
                        Text(if (permissionStatus == AudioPermissionStatus.REQUIRED) "Allow" else "Settings")
                    }
                }
            }

            when {
                state.isRefreshing && state.tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.errorMessage != null && state.tracks.isEmpty() -> MessageState("Couldn't load music", state.errorMessage, "Try again", onRefresh)
                state.tracks.isEmpty() && permissionStatus == AudioPermissionStatus.REQUIRED -> MessageState(
                    "Music access needed",
                    "Allow device audio access, or connect Telegram to import cloud music.",
                    "Allow access",
                    onRequestPermission,
                )
                state.tracks.isEmpty() && permissionStatus == AudioPermissionStatus.DENIED -> MessageState(
                    "No music indexed yet",
                    "Device audio access is disabled. You can enable it in settings or import a Telegram music source.",
                    "Open settings",
                    onOpenSettings,
                )
                state.tracks.isEmpty() -> MessageState("No music found", "Add device music or choose a Telegram music source.", "Telegram", onOpenTelegram)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.tracks, key = { it.track.id.toString() }) { item ->
                        TrackRow(
                            item = item,
                            onClick = { onPlayTrack(item) },
                            onPlayNext = { onPlayNext(item) },
                            onAddToQueue = { onAddToQueue(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    item: LibraryTrack,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val playableNow = item.availability == LibraryTrackAvailability.OFFLINE
    val track = item.track
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = playableNow, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(track.artworkRef, track.title)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            when (item.availability) {
                LibraryTrackAvailability.OFFLINE -> Text(
                    "Offline",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                LibraryTrackAvailability.CLOUD -> Text(
                    "Cloud",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                LibraryTrackAvailability.UNAVAILABLE -> Text(
                    "Unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall)
            if (playableNow) {
                Row {
                    TextButton(onClick = onPlayNext) { Text("Next") }
                    TextButton(onClick = onAddToQueue) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun MessageState(title: String, message: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "—"
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
