package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.RepeatMode
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Open queue")
                }
            }

            if (track == null) {
                EmptyNowPlaying()
                return@Column
            }

            SwipeableArtwork(
                artworkRef = track.artworkRef,
                title = track.title,
                canSkipPrevious = state.canSkipPrevious,
                canSkipNext = state.canSkipNext,
                onPrevious = onPrevious,
                onNext = onNext,
                isPreparing = state.status == PlaybackStatus.BUFFERING || state.status == PlaybackStatus.PREPARING,
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = track.artist ?: "Unknown artist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )

            state.errorMessage?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Slider(
                value = shownPosition.toFloat(),
                onValueChange = { pendingSeek = it },
                onValueChangeFinished = {
                    pendingSeek?.let { onSeek(it.toLong()) }
                    pendingSeek = null
                },
                valueRange = 0f..duration.toFloat(),
                enabled = state.durationMs > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelMedium)
                Text(
                    "-${formatDuration((duration - shownPosition).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = state.canSkipPrevious) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous track",
                        modifier = Modifier.size(34.dp),
                    )
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        imageVector = if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = state.canSkipNext) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                FilterChip(
                    selected = state.playbackMode == PlaybackMode.PURE_SHUFFLE,
                    onClick = onTogglePlaybackMode,
                    label = {
                        Text(if (state.playbackMode == PlaybackMode.PURE_SHUFFLE) "Pure shuffle" else "Ordered")
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
                FilterChip(
                    selected = state.repeatMode != RepeatMode.OFF,
                    onClick = onCycleRepeatMode,
                    label = {
                        Text(
                            when (state.repeatMode) {
                                RepeatMode.OFF -> "Repeat off"
                                RepeatMode.ONE -> "Repeat one"
                                RepeatMode.ALL -> "Repeat all"
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            Text(
                text = if (state.queueSize > 0 && state.queueIndex >= 0) {
                    "${state.queueIndex + 1} of ${state.queueSize}"
                } else {
                    "Queue ready"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun SwipeableArtwork(
    artworkRef: String?,
    title: String,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isPreparing: Boolean,
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 92.dp.toPx() }
    val visualLimit = with(density) { 42.dp.toPx() }
    var dragDistance by remember(title) { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .size(300.dp)
            .graphicsLayer {
                translationX = dragDistance.coerceIn(-visualLimit, visualLimit)
            }
            .pointerInput(title, canSkipPrevious, canSkipNext) {
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragDistance += dragAmount
                    },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = {
                        when {
                            dragDistance <= -swipeThreshold && canSkipNext -> onNext()
                            dragDistance >= swipeThreshold && canSkipPrevious -> onPrevious()
                        }
                        dragDistance = 0f
                    },
                )
            },
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 8.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TrackArtwork(
                artworkRef = artworkRef,
                description = title,
                size = 300.dp,
            )
            if (isPreparing) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNowPlaying() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp),
                )
                Text(
                    "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Choose something from your library and it will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
