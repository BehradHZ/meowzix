package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.feature.downloads.DownloadsRoute
import dev.behradhz.meowzix.feature.history.HistoryRoute
import dev.behradhz.meowzix.feature.library.LibraryRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.feature.queue.QueueRoute
import dev.behradhz.meowzix.feature.telegramauth.TelegramAuthRoute
import dev.behradhz.meowzix.ui.components.AudioSpectrum
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private const val LIBRARY_ROUTE = "library"
private const val NOW_PLAYING_ROUTE = "now-playing"
private const val QUEUE_ROUTE = "queue"
private const val TELEGRAM_AUTH_ROUTE = "telegram-auth"
private const val DOWNLOADS_ROUTE = "downloads"
private const val HISTORY_ROUTE = "history"

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun MeowzixApp(
    openNowPlayingRequest: Boolean = false,
    onNowPlayingRequestConsumed: () -> Unit = {},
    playerViewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val hazeState = rememberHazeState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()
    val spectrum by playerViewModel.spectrum.collectAsStateWithLifecycle()

    LaunchedEffect(openNowPlayingRequest) {
        if (openNowPlayingRequest) {
            navController.navigate(NOW_PLAYING_ROUTE) { launchSingleTop = true }
            onNowPlayingRequestConsumed()
        }
    }

    // Keep capture ownership
    val destinations = remember {
        listOf(
            TopLevelDestination(
                route = LIBRARY_ROUTE,
                label = "Library",
                icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            ),
            TopLevelDestination(
                route = QUEUE_ROUTE,
                label = "Queue",
                icon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
            ),
            TopLevelDestination(
                route = DOWNLOADS_ROUTE,
                label = "Offline",
                icon = { Icon(Icons.Rounded.DownloadForOffline, contentDescription = null) },
            ),
            TopLevelDestination(
                route = HISTORY_ROUTE,
                label = "History",
                icon = { Icon(Icons.Rounded.History, contentDescription = null) },
            ),
            TopLevelDestination(
                route = TELEGRAM_AUTH_ROUTE,
                label = "Telegram",
                icon = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
            ),
        )
    }
    val isTopLevelDestination = destinations.any { it.route == currentRoute }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = LIBRARY_ROUTE,
            modifier = Modifier
                .fillMaxSize()
                .horizontalSwipeNavigation(
                    enabled = isTopLevelDestination && currentRoute != LIBRARY_ROUTE,
                    onSwipeLeft = {
                        val index = destinations.indexOfFirst { it.route == currentRoute }
                        if (index in 0 until destinations.lastIndex) {
                            navController.navigate(destinations[index + 1].route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onSwipeRight = {
                        val index = destinations.indexOfFirst { it.route == currentRoute }
                        if (index > 0) {
                            navController.navigate(destinations[index - 1].route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
                .hazeSource(hazeState),
        ) {
            composable(LIBRARY_ROUTE) {
                LibraryRoute(
                    onSwipePastEnd = {
                        navController.navigate(QUEUE_ROUTE) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenNowPlaying = {
                        navController.navigate(NOW_PLAYING_ROUTE) { launchSingleTop = true }
                    },
                    onOpenTelegram = {
                        navController.navigate(TELEGRAM_AUTH_ROUTE) { launchSingleTop = true }
                    },
                )
            }
            composable(NOW_PLAYING_ROUTE) {
                NowPlayingRoute(
                    onBack = navController::popBackStack,
                    onOpenQueue = {
                        navController.navigate(QUEUE_ROUTE) { launchSingleTop = true }
                    },
                )
            }
            composable(QUEUE_ROUTE) {
                QueueRoute(onBack = navController::popBackStack)
            }
            composable(TELEGRAM_AUTH_ROUTE) {
                TelegramAuthRoute(onBack = navController::popBackStack)
            }
            composable(DOWNLOADS_ROUTE) {
                DownloadsRoute()
            }
            composable(HISTORY_ROUTE) {
                HistoryRoute()
            }
        }

        if (isTopLevelDestination) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (playbackState.currentTrack != null) {
                    GlassMiniPlayer(
                        hazeState = hazeState,
                        state = playbackState,
                        spectrum = spectrum,
                        onOpenNowPlaying = {
                            navController.navigate(NOW_PLAYING_ROUTE) { launchSingleTop = true }
                        },
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onPrevious = playerViewModel::previous,
                        onNext = playerViewModel::next,
                    )
                }

                FloatingDock(
                    hazeState = hazeState,
                    destinations = destinations,
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FloatingDock(
    hazeState: dev.chrisbanes.haze.HazeState,
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(36.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tint = Color.White.copy(alpha = 0.09f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clickable { onSelect(destination.route) },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    },
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            destination.icon()
                        }
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassMiniPlayer(
    hazeState: dev.chrisbanes.haze.HazeState,
    state: PlaybackState,
    spectrum: AudioSpectrumState,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val compactBands = remember(spectrum.bands) {
        val source = spectrum.bands
        FloatArray(8) { compactIndex ->
            val start = compactIndex * source.size / 8
            val endExclusive = ((compactIndex + 1) * source.size / 8)
                .coerceAtLeast(start + 1)
                .coerceAtMost(source.size)
            var peak = 0f
            for (index in start until endExclusive) {
                if (source[index] > peak) peak = source[index]
            }
            peak
        }
    }
    val hasWaveform = remember(compactBands) { compactBands.any { it > 0.001f } }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val visualLimit = with(density) { 24.dp.toPx() }
    var dragDistance by remember(track.id) { mutableFloatStateOf(0f) }

    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer { translationX = dragDistance.coerceIn(-visualLimit, visualLimit) }
            .pointerInput(track.id, state.canSkipPrevious, state.canSkipNext) {
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = {
                        when {
                            dragDistance <= -swipeThreshold && state.canSkipNext -> onNext()
                            dragDistance >= swipeThreshold && state.canSkipPrevious -> onPrevious()
                        }
                        dragDistance = 0f
                    },
                )
            }
            .clickable(onClick = onOpenNowPlaying),
        shape = RoundedCornerShape(28.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        tint = Color.White.copy(alpha = 0.10f),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(
                    artworkRef = track.artworkRef,
                    description = track.title,
                    size = 50.dp,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (hasWaveform && state.status == PlaybackStatus.PLAYING) {
                    AudioSpectrum(
                        bands = compactBands,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .width(44.dp)
                            .height(22.dp)
                            .padding(horizontal = 2.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }

                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (state.status == PlaybackStatus.PLAYING) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .align(Alignment.CenterStart),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                    ) {}
                }
            }
        }
    }
}
