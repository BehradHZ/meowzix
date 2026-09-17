package dev.behradhz.meowzix.feature.nowplaying

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.ui.components.AudioSpectrum
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.behradhz.meowzix.ui.components.TrackArtworkBackdrop
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun NowPlayingRoute(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()
    var spectrumPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        spectrumPermissionGranted = granted
    }

    LaunchedEffect(spectrumPermissionGranted, state.status) {
        viewModel.setSpectrumCaptureEnabled(
            spectrumPermissionGranted && state.status == PlaybackStatus.PLAYING,
        )
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.setSpectrumCaptureEnabled(false) }
    }

    NowPlayingScreen(
        state = state,
        spectrum = spectrum,
        spectrumPermissionGranted = spectrumPermissionGranted,
        onRequestSpectrumPermission = {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
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
    spectrum: AudioSpectrumState,
    spectrumPermissionGranted: Boolean,
    onRequestSpectrumPermission: () -> Unit,
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
    if (track == null) {
        EmptyNowPlaying(onBack = onBack)
        return
    }

    val hazeState = rememberHazeState()
    var pendingSeek by remember(track.id) { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val shownPosition = pendingSeek?.toLong() ?: state.positionMs.coerceIn(0, duration)

    Box(modifier = Modifier.fillMaxSize()) {
        TrackArtworkBackdrop(
            artworkRef = track.artworkRef,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val artworkSize = minOf(maxWidth - 48.dp, 330.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 14.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassSurface(
                    hazeState = hazeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(29.dp),
                    fallbackColor = Color.Black.copy(alpha = 0.30f),
                    tint = Color.White.copy(alpha = 0.09f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Close now playing",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Now Playing",
                            color = Color.White.copy(alpha = 0.84f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onOpenQueue) {
                            Icon(
                                Icons.Rounded.QueueMusic,
                                contentDescription = "Open queue",
                                tint = Color.White,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                SwipeableArtwork(
                    artworkRef = track.artworkRef,
                    title = track.title,
                    artworkSize = artworkSize,
                    canSkipPrevious = state.canSkipPrevious,
                    canSkipNext = state.canSkipNext,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    isPreparing = state.status == PlaybackStatus.BUFFERING ||
                        state.status == PlaybackStatus.PREPARING,
                )

                Spacer(Modifier.height(30.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(18.dp))

                LiveSpectrumPanel(
                    hazeState = hazeState,
                    spectrum = spectrum,
                    permissionGranted = spectrumPermissionGranted,
                    onRequestPermission = onRequestSpectrumPermission,
                )

                if (state.errorMessage != null) {
                    GlassSurface(
                        hazeState = hazeState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                        fallbackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.28f),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = state.errorMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

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
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                        disabledThumbColor = Color.White.copy(alpha = 0.34f),
                        disabledActiveTrackColor = Color.White.copy(alpha = 0.22f),
                        disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(shownPosition),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Text(
                        "-${formatDuration((duration - shownPosition).coerceAtLeast(0))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = state.canSkipPrevious,
                        modifier = Modifier.size(62.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = if (state.canSkipPrevious) Color.White else Color.White.copy(alpha = 0.24f),
                            modifier = Modifier.size(38.dp),
                        )
                    }

                    Surface(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(78.dp),
                        shape = RoundedCornerShape(39.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        contentColor = Color.Black,
                        shadowElevation = 12.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.status == PlaybackStatus.PLAYING) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = onNext,
                        enabled = state.canSkipNext,
                        modifier = Modifier.size(62.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next track",
                            tint = if (state.canSkipNext) Color.White else Color.White.copy(alpha = 0.24f),
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassModeButton(
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        selected = state.playbackMode == PlaybackMode.PURE_SHUFFLE,
                        label = if (state.playbackMode == PlaybackMode.PURE_SHUFFLE) {
                            "Pure Shuffle"
                        } else {
                            "Ordered"
                        },
                        icon = Icons.Rounded.Shuffle,
                        onClick = onTogglePlaybackMode,
                    )

                    GlassModeButton(
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        selected = state.repeatMode != RepeatMode.OFF,
                        label = when (state.repeatMode) {
                            RepeatMode.OFF -> "Repeat Off"
                            RepeatMode.ONE -> "Repeat One"
                            RepeatMode.ALL -> "Repeat All"
                        },
                        icon = if (state.repeatMode == RepeatMode.ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        onClick = onCycleRepeatMode,
                    )
                }

                Text(
                    text = if (state.queueSize > 0 && state.queueIndex >= 0) {
                        "${state.queueIndex + 1} of ${state.queueSize}"
                    } else {
                        "Queue ready"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.52f),
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveSpectrumPanel(
    hazeState: dev.chrisbanes.haze.HazeState,
    spectrum: AudioSpectrumState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        shape = RoundedCornerShape(24.dp),
        fallbackColor = Color.Black.copy(alpha = 0.22f),
        tint = Color.White.copy(alpha = 0.06f),
    ) {
        if (permissionGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "LIVE SPECTRUM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.58f),
                    )
                    Spacer(Modifier.weight(1f))
                    if (!spectrum.isCapturing) {
                        Text(
                            text = if (spectrum.sessionId > 0) "Ready" else "Waiting for audio",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.42f),
                        )
                    }
                }

                AudioSpectrum(
                    bands = spectrum.bands,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 6.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onRequestPermission)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.76f),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = "Enable live spectrum",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Android requires audio permission for playback visualization. Meowzix reads only its own player session.",
                        color = Color.White.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassModeButton(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier,
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        fallbackColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else {
            Color.Black.copy(alpha = 0.24f)
        },
        tint = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.White.copy(alpha = 0.07f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}

@Composable
private fun SwipeableArtwork(
    artworkRef: String?,
    title: String,
    artworkSize: androidx.compose.ui.unit.Dp,
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
            .size(artworkSize)
            .graphicsLayer {
                translationX = dragDistance.coerceIn(-visualLimit, visualLimit)
                rotationZ = (dragDistance / visualLimit).coerceIn(-1f, 1f) * 1.5f
            }
            .pointerInput(title, canSkipPrevious, canSkipNext) {
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragDistance += dragAmount },
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
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 22.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TrackArtwork(
                artworkRef = artworkRef,
                description = title,
                size = artworkSize,
            )

            if (isPreparing) {
                Surface(
                    color = Color.Black.copy(alpha = 0.28f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNowPlaying(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close now playing")
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
