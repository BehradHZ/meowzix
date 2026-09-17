package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.ui.components.TrackArtwork

@Composable
fun NowPlayingRoute(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NowPlayingScreen(
        state = state,
        onBack = onBack,
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onTogglePlaybackMode = viewModel::togglePlaybackMode,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onOpenQueue = onOpenQueue,
    )
}

@Composable
private fun NowPlayingScreen(
    state: PlaybackState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlaybackMode: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val track = state.currentTrack
    var pendingSeek by remember(track?.id) { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val shownPosition = pendingSeek?.toLong() ?: state.positionMs.coerceIn(0, duration)

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Button(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onOpenQueue) { Text("Queue") }
            }
            Spacer(Modifier.height(32.dp))
            if (track == null) {
                Text("Nothing playing", style = MaterialTheme.typography.headlineSmall)
                Text("Choose a track from your local library.", modifier = Modifier.padding(top = 12.dp))
                return@Column
            }

            TrackArtwork(track.artworkRef, track.title, size = 240.dp)
            Spacer(Modifier.height(28.dp))
            Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
            Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodyLarge)
            if (state.status == PlaybackStatus.BUFFERING || state.status == PlaybackStatus.PREPARING) {
                CircularProgressIndicator(Modifier.padding(top = 16.dp))
            }
            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(24.dp))
            Slider(
                value = shownPosition.toFloat(),
                onValueChange = { pendingSeek = it },
                onValueChangeFinished = {
                    pendingSeek?.let { onSeek(it.toLong()) }
                    pendingSeek = null
                },
                valueRange = 0f..duration.toFloat(),
                enabled = state.durationMs > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(shownPosition))
                Text("-${formatDuration((duration - shownPosition).coerceAtLeast(0))}")
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onPrevious, enabled = state.canSkipPrevious) { Text("Previous") }
                Button(onClick = onTogglePlayPause) {
                    Text(if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play")
                }
                Button(onClick = onNext, enabled = state.canSkipNext) { Text("Next") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = onTogglePlaybackMode) {
                    Text(if (state.playbackMode.name == "PURE_SHUFFLE") "Pure Shuffle" else "Ordered")
                }
                Button(onClick = onCycleRepeatMode) { Text("Repeat ${state.repeatMode.name}") }
            }
            Text(
                "${state.queueIndex + 1} of ${state.queueSize}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
