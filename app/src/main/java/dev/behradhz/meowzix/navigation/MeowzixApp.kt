package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.feature.library.LibraryRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.feature.queue.QueueRoute
import dev.behradhz.meowzix.feature.telegramauth.TelegramAuthRoute
import dev.behradhz.meowzix.ui.components.TrackArtwork

private const val LIBRARY_ROUTE = "library"
private const val NOW_PLAYING_ROUTE = "now-playing"
private const val QUEUE_ROUTE = "queue"
private const val TELEGRAM_AUTH_ROUTE = "telegram-auth"

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun MeowzixApp(
    playerViewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()

    val destinations = listOf(
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
            route = TELEGRAM_AUTH_ROUTE,
            label = "Telegram",
            icon = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
        ),
    )
    val isTopLevelDestination = destinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                Column {
                    if (playbackState.currentTrack != null) {
                        MiniPlayer(
                            state = playbackState,
                            onOpenNowPlaying = {
                                navController.navigate(NOW_PLAYING_ROUTE) { launchSingleTop = true }
                            },
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onPrevious = playerViewModel::previous,
                            onNext = playerViewModel::next,
                        )
                    }
                    ShortNavigationBar {
                        destinations.forEach { destination ->
                            ShortNavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = destination.icon,
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = LIBRARY_ROUTE,
            modifier = Modifier.padding(outerPadding),
        ) {
            composable(LIBRARY_ROUTE) {
                LibraryRoute(
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
                    onOpenQueue = { navController.navigate(QUEUE_ROUTE) { launchSingleTop = true } },
                )
            }
            composable(QUEUE_ROUTE) {
                QueueRoute(onBack = navController::popBackStack)
            }
            composable(TELEGRAM_AUTH_ROUTE) {
                TelegramAuthRoute(onBack = navController::popBackStack)
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    state: PlaybackState,
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
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val visualLimit = with(density) { 24.dp.toPx() }
    var dragDistance by remember(track.id) { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 6.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(
                    artworkRef = track.artworkRef,
                    description = track.title,
                    size = 48.dp,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        }
    }
}
