package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.feature.telegram.TelegramForwardSheet
import dev.behradhz.meowzix.ui.components.AudioSpectrum
import dev.behradhz.meowzix.ui.components.CurlyMusicSlider
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.TrackArtworkBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val PlayerPrimaryContent = Color(0xFFF7F3EF)
private val PlayerSecondaryContent = Color(0xFFCFC7C0)
private val PlayerGlass = Color(0xFF171615)

@Composable
fun NowPlayingRoute(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()

    NowPlayingScreen(
        state = state,
        queueState = queueState,
        spectrum = spectrum,
        isFavorite = isFavorite,
        onBack = onBack,
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onToggleFavorite = viewModel::toggleFavorite,
        onTogglePlaybackMode = viewModel::togglePlaybackMode,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onOpenForward = viewModel::openForwardPicker,
        onOpenQueue = onOpenQueue,
    )

    val track = state.currentTrack
    if (forwardState.isOpen && track != null) {
        TelegramForwardSheet(
            track = track,
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
    }
}

@Composable
private fun NowPlayingScreen(
    state: PlaybackState,
    queueState: QueueState,
    spectrum: AudioSpectrumState,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlaybackMode: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpenForward: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val track = state.currentTrack
    if (track == null) {
        EmptyNowPlaying(onBack)
        return
    }

    val hazeState = rememberHazeState()
    var pendingSeek by remember(track.id) { mutableStateOf<Float?>(null) }
    var backdropTransition by remember { mutableStateOf<ArtworkBackdropTransition?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val shownPosition = pendingSeek?.toLong() ?: state.positionMs.coerceIn(0L, duration)

    Box(Modifier.fillMaxSize()) {
        val activeBackdropTransition = backdropTransition
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            TrackArtworkBackdrop(
                artworkRef = activeBackdropTransition?.fromArtworkRef ?: track.artworkRef,
                modifier = Modifier.fillMaxSize(),
            )

            activeBackdropTransition?.let { transition ->
                if (transition.progress > 0f) {
                    TrackArtworkBackdrop(
                        artworkRef = transition.toArtworkRef,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = transition.progress.coerceIn(0f, 1f)
                            },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            PlayerHeader(
                hazeState = hazeState,
                onBack = onBack,
                onOpenForward = onOpenForward,
                onOpenQueue = onOpenQueue,
            )

            ArtworkGestureZone(
                state = state,
                queueState = queueState,
                artworkRef = track.artworkRef,
                artworkDescription = "${track.title} cover art",
                onBack = onBack,
                onPrevious = onPrevious,
                onNext = onNext,
                onBackdropTransition = { backdropTransition = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            TrackIdentity(
                title = track.title,
                artist = track.artist ?: "Unknown artist",
                favorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
            )

            Spacer(Modifier.height(12.dp))

            FileWaveform(
                spectrum = spectrum,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
            )

            CurlyMusicSlider(
                value = shownPosition.toFloat(),
                onValueChange = { pendingSeek = it },
                onValueChangeFinished = {
                    pendingSeek?.let { onSeek(it.toLong()) }
                    pendingSeek = null
                },
                valueRange = 0f..duration.toFloat(),
                enabled = state.durationMs > 0L,
                isPlaying = state.status == PlaybackStatus.PLAYING,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = PlayerPrimaryContent.copy(alpha = 0.24f),
                thumbColor = PlayerPrimaryContent,
                modifier = Modifier.fillMaxWidth(),
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
                    "-${formatDuration((duration - shownPosition).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerSecondaryContent,
                )
            }

            Spacer(Modifier.height(14.dp))

            PlaybackControls(
                state = state,
                onPrevious = onPrevious,
                onNext = onNext,
                onTogglePlayPause = onTogglePlayPause,
                onTogglePlaybackMode = onTogglePlaybackMode,
                onCycleRepeatMode = onCycleRepeatMode,
            )

            Spacer(Modifier.height(12.dp))
        }

        if (
            state.status == PlaybackStatus.DOWNLOADING ||
            state.status == PlaybackStatus.BUFFERING ||
            state.status == PlaybackStatus.PREPARING
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.Black.copy(alpha = 0.50f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = PlayerPrimaryContent,
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp, start = 22.dp, end = 22.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
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

@Composable
private fun ArtworkGestureZone(
    state: PlaybackState,
    queueState: QueueState,
    artworkRef: String?,
    artworkDescription: String,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackdropTransition: (ArtworkBackdropTransition?) -> Unit,
    modifier: Modifier = Modifier,
) {
    NowPlayingArtworkPager(
        state = state,
        queueState = queueState,
        fallbackArtworkRef = artworkRef,
        fallbackArtworkDescription = artworkDescription,
        onBack = onBack,
        onPrevious = onPrevious,
        onNext = onNext,
        onBackdropTransition = onBackdropTransition,
        modifier = modifier,
    )
}

@Composable
private fun TrackIdentity(
    title: String,
    artist: String,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PlayerPrimaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = PlayerSecondaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) MaterialTheme.colorScheme.primary else PlayerPrimaryContent,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun FileWaveform(
    spectrum: AudioSpectrumState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AudioSpectrum(
            bands = spectrum.bands,
            color = PlayerPrimaryContent,
            modifier = Modifier.fillMaxSize(),
        )
        if (spectrum.isAnalyzing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    hazeState: HazeState,
    onBack: () -> Unit,
    onOpenForward: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(25.dp),
        fallbackColor = PlayerGlass.copy(alpha = 0.78f),
        tint = PlayerPrimaryContent.copy(alpha = 0.05f),
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
                    modifier = Modifier.size(30.dp),
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
            IconButton(onClick = onOpenForward) {
                Icon(
                    Icons.Rounded.Send,
                    contentDescription = "Forward track on Telegram",
                    tint = PlayerPrimaryContent,
                )
            }
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
private fun PlaybackControls(
    state: PlaybackState,
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
            selected = state.playbackMode != PlaybackMode.ORDERED,
            onClick = onTogglePlaybackMode,
            contentDescription = when (state.playbackMode) {
                PlaybackMode.ORDERED -> "Ordered playback"
                PlaybackMode.PURE_SHUFFLE -> "Pure shuffle on"
                PlaybackMode.SMART_SHUFFLE -> "Smart shuffle on"
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
                tint = if (state.canSkipPrevious) PlayerPrimaryContent else PlayerSecondaryContent.copy(alpha = 0.30f),
                modifier = Modifier.size(35.dp),
            )
        }

        Surface(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(36.dp),
            color = PlayerPrimaryContent,
            contentColor = Color(0xFF171411),
            shadowElevation = 10.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                    modifier = Modifier.size(39.dp),
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
                tint = if (state.canSkipNext) PlayerPrimaryContent else PlayerSecondaryContent.copy(alpha = 0.30f),
                modifier = Modifier.size(35.dp),
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
                imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
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
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else PlayerGlass.copy(alpha = 0.70f),
        contentColor = if (selected) MaterialTheme.colorScheme.primary else PlayerSecondaryContent,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
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
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
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
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
