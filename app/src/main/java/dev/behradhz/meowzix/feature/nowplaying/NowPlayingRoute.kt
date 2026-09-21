package dev.behradhz.meowzix.feature.nowplaying

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs

private val PlayerPrimaryContent = Color(0xFFF5F0EB)
private val PlayerSecondaryContent = Color(0xFFC9C1BA)
private val PlayerGlass = Color(0xFF181716)

@Composable
fun NowPlayingRoute(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
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
        onVolumeUp = {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI,
            )
        },
        onVolumeDown = {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI,
            )
        },
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
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            val compact = maxHeight < 720.dp
            val fixedReserve = if (compact) 346.dp else 364.dp
            val artworkHeightBudget = (maxHeight - fixedReserve).coerceAtLeast(156.dp)
            val artworkSize = minOf(
                maxWidth,
                if (compact) 278.dp else 326.dp,
                artworkHeightBudget,
            )
            val sectionGap = if (compact) 10.dp else 14.dp
            val spectrumHeight = if (compact) 26.dp else 32.dp
            val playButtonSize = if (compact) 68.dp else 74.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlayerHeader(
                    hazeState = hazeState,
                    onBack = onBack,
                    onOpenQueue = onOpenQueue,
                )

                Spacer(Modifier.height(sectionGap))

                GestureArtwork(
                    artworkRef = track.artworkRef,
                    title = track.title,
                    artworkSize = artworkSize,
                    canSkipPrevious = state.canSkipPrevious,
                    canSkipNext = state.canSkipNext,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onVolumeUp = onVolumeUp,
                    onVolumeDown = onVolumeDown,
                    onTogglePlayPause = onTogglePlayPause,
                    isPreparing = state.status == PlaybackStatus.BUFFERING ||
                        state.status == PlaybackStatus.PREPARING,
                )

                Spacer(Modifier.height(sectionGap))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PlayerPrimaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.titleMedium,
                        color = PlayerSecondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                TimelineSpectrum(
                    spectrum = spectrum,
                    permissionGranted = spectrumPermissionGranted,
                    onRequestPermission = onRequestSpectrumPermission,
                    height = spectrumHeight,
                )

                Slider(
                    value = shownPosition.toFloat(),
                    onValueChange = { pendingSeek = it },
                    onValueChangeFinished = {
                        pendingSeek?.let { onSeek(it.toLong()) }
                        pendingSeek = null
                    },
                    valueRange = 0f..duration.toFloat(),
                    enabled = state.durationMs > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = PlayerPrimaryContent,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = PlayerPrimaryContent.copy(alpha = 0.20f),
                        disabledThumbColor = PlayerSecondaryContent.copy(alpha = 0.46f),
                        disabledActiveTrackColor = PlayerSecondaryContent.copy(alpha = 0.30f),
                        disabledInactiveTrackColor = PlayerSecondaryContent.copy(alpha = 0.14f),
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(shownPosition),
                        style = MaterialTheme.typography.labelMedium,
                        color = PlayerSecondaryContent,
                    )
                    Text(
                        "-${formatDuration((duration - shownPosition).coerceAtLeast(0))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PlayerSecondaryContent,
                    )
                }

                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

                PlaybackControls(
                    state = state,
                    playButtonSize = playButtonSize,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onTogglePlayPause = onTogglePlayPause,
                    onTogglePlaybackMode = onTogglePlaybackMode,
                    onCycleRepeatMode = onCycleRepeatMode,
                )

                Text(
                    text = if (state.queueSize > 0 && state.queueIndex >= 0) {
                        "${state.queueIndex + 1} / ${state.queueSize}"
                    } else {
                        "Queue ready"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerSecondaryContent.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = if (compact) 4.dp else 7.dp),
                )
            }

            state.errorMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp, start = 8.dp, end = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerHeader(
    hazeState: HazeState,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        fallbackColor = PlayerGlass.copy(alpha = 0.88f),
        tint = PlayerPrimaryContent.copy(alpha = 0.06f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close now playing",
                    tint = PlayerPrimaryContent,
                    modifier = Modifier.size(29.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Now Playing",
                color = PlayerPrimaryContent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenQueue) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = "Open queue",
                    tint = PlayerPrimaryContent,
                )
            }
        }
    }
}

@Composable
private fun TimelineSpectrum(
    spectrum: AudioSpectrumState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    height: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        if (permissionGranted) {
            AudioSpectrum(
                bands = spectrum.bands,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            )
        } else {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = "Enable live spectrum",
                tint = PlayerSecondaryContent.copy(alpha = 0.72f),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onRequestPermission),
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    state: PlaybackState,
    playButtonSize: Dp,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onTogglePlaybackMode: () -> Unit,
    onCycleRepeatMode: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeIconButton(
            selected = state.playbackMode == PlaybackMode.PURE_SHUFFLE,
            onClick = onTogglePlaybackMode,
            contentDescription = if (state.playbackMode == PlaybackMode.PURE_SHUFFLE) {
                "Pure shuffle on"
            } else {
                "Ordered playback"
            },
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
        }

        IconButton(
            onClick = onPrevious,
            enabled = state.canSkipPrevious,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                tint = if (state.canSkipPrevious) {
                    PlayerPrimaryContent
                } else {
                    PlayerSecondaryContent.copy(alpha = 0.30f)
                },
                modifier = Modifier.size(34.dp),
            )
        }

        Surface(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(playButtonSize),
            shape = RoundedCornerShape(playButtonSize / 2),
            color = PlayerPrimaryContent,
            contentColor = Color(0xFF171411),
            shadowElevation = 10.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (state.status == PlaybackStatus.PLAYING) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        IconButton(
            onClick = onNext,
            enabled = state.canSkipNext,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next track",
                tint = if (state.canSkipNext) {
                    PlayerPrimaryContent
                } else {
                    PlayerSecondaryContent.copy(alpha = 0.30f)
                },
                modifier = Modifier.size(34.dp),
            )
        }

        ModeIconButton(
            selected = state.repeatMode != RepeatMode.OFF,
            onClick = onCycleRepeatMode,
            contentDescription = when (state.repeatMode) {
                RepeatMode.OFF -> "Repeat off"
                RepeatMode.ONE -> "Repeat one"
                RepeatMode.ALL -> "Repeat all"
            },
        ) {
            Icon(
                imageVector = if (state.repeatMode == RepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun ModeIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            PlayerGlass.copy(alpha = 0.76f)
        },
        contentColor = if (selected) MaterialTheme.colorScheme.primary else PlayerSecondaryContent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
    }
}

@Composable
private fun GestureArtwork(
    artworkRef: String?,
    title: String,
    artworkSize: Dp,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onTogglePlayPause: () -> Unit,
    isPreparing: Boolean,
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 58.dp.toPx() }
    val visualLimit = with(density) { 34.dp.toPx() }
    var dragDistance by remember(title) { mutableStateOf(Offset.Zero) }

    Surface(
        modifier = Modifier
            .size(artworkSize)
            .graphicsLayer {
                translationX = dragDistance.x.coerceIn(-visualLimit, visualLimit)
                translationY = dragDistance.y.coerceIn(-visualLimit, visualLimit) * 0.16f
                rotationZ = (dragDistance.x / visualLimit).coerceIn(-1f, 1f) * 1.2f
            }
            .pointerInput(title, canSkipPrevious, canSkipNext) {
                detectDragGestures(
                    onDragStart = { dragDistance = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDistance += dragAmount
                    },
                    onDragCancel = { dragDistance = Offset.Zero },
                    onDragEnd = {
                        val horizontal = abs(dragDistance.x) >= abs(dragDistance.y)
                        if (horizontal) {
                            when {
                                dragDistance.x <= -swipeThreshold && canSkipNext -> onNext()
                                dragDistance.x >= swipeThreshold && canSkipPrevious -> onPrevious()
                            }
                        } else {
                            when {
                                dragDistance.y <= -swipeThreshold -> onVolumeUp()
                                dragDistance.y >= swipeThreshold -> onVolumeDown()
                            }
                        }
                        dragDistance = Offset.Zero
                    },
                )
            }
            .clickable(onClick = onTogglePlayPause),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 18.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TrackArtwork(
                artworkRef = artworkRef,
                description = title,
                size = artworkSize,
            )

            if (isPreparing) {
                Surface(
                    color = Color.Black.copy(alpha = 0.34f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
            .statusBarsPadding()
            .navigationBarsPadding()
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
