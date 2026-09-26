package dev.behradhz.meowzix.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.feature.downloads.DownloadsRoute
import dev.behradhz.meowzix.feature.history.HistoryRoute
import dev.behradhz.meowzix.feature.home.HomeRoute
import dev.behradhz.meowzix.feature.library.LibraryRoute
import dev.behradhz.meowzix.feature.library.LibraryViewModel
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.feature.profile.ProfileRoute
import dev.behradhz.meowzix.feature.queue.QueueRoute
import dev.behradhz.meowzix.feature.search.SearchRoute
import dev.behradhz.meowzix.feature.telegramauth.TelegramAuthRoute
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private const val HOME_ROUTE = "home"
private const val LIBRARY_ROUTE = "library"
private const val SEARCH_ROUTE = "search"
private const val QUEUE_ROUTE = "queue"
private const val PROFILE_ROUTE = "profile"
private const val TELEGRAM_AUTH_ROUTE = "telegram-auth"
private const val DOWNLOADS_ROUTE = "downloads"
private const val HISTORY_ROUTE = "history"

private data class DockDestination(
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
    val morphingPlayerState = rememberMorphingPlayerState()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()
    val spectrum by playerViewModel.spectrum.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: HOME_ROUTE
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFieldFocused by remember { mutableStateOf(false) }

    val destinations = remember {
        listOf(
            DockDestination(
                route = HOME_ROUTE,
                label = "Home",
                icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            ),
            DockDestination(
                route = LIBRARY_ROUTE,
                label = "Library",
                icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            ),
            DockDestination(
                route = SEARCH_ROUTE,
                label = "Search",
                icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            ),
            DockDestination(
                route = QUEUE_ROUTE,
                label = "Queue",
                icon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
            ),
            DockDestination(
                route = PROFILE_ROUTE,
                label = "Profile",
                icon = {
                    Image(
                        painter = painterResource(R.drawable.meowzix_logo),
                        contentDescription = null,
                        modifier = Modifier.size(25.dp),
                    )
                },
            ),
        )
    }

    val secondaryProfileRoutes = remember { setOf(DOWNLOADS_ROUTE, HISTORY_ROUTE, TELEGRAM_AUTH_ROUTE) }
    val selectedDockRoute = if (currentRoute in secondaryProfileRoutes) PROFILE_ROUTE else currentRoute
    val searchActive = currentRoute == SEARCH_ROUTE

    fun dismissSearchKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        searchFieldFocused = false
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goHome() {
        dismissSearchKeyboard()
        navigateTopLevel(HOME_ROUTE)
    }

    LaunchedEffect(openNowPlayingRequest) {
        if (openNowPlayingRequest) {
            morphingPlayerState.expand()
            onNowPlayingRequestConsumed()
        }
    }

    BackHandler(
        enabled = currentRoute != HOME_ROUTE || searchFieldFocused || searchQuery.isNotBlank(),
    ) {
        when {
            morphingPlayerState.targetExpanded -> morphingPlayerState.collapse()
            currentRoute == SEARCH_ROUTE && searchFieldFocused -> dismissSearchKeyboard()
            currentRoute == SEARCH_ROUTE && searchQuery.isNotBlank() -> searchQuery = ""
            currentRoute in secondaryProfileRoutes -> navController.popBackStack()
            currentRoute != HOME_ROUTE -> goHome()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            composable(HOME_ROUTE) {
                HomeRoute(
                    currentTrack = playbackState.currentTrack,
                    onPlayTrack = { track, queue -> libraryViewModel.playTrack(track, queue) },
                    onPlayCollection = { tracks -> libraryViewModel.playCollection(tracks, dev.behradhz.meowzix.domain.playback.PlaybackMode.ORDERED) },
                    onOpenLibrary = { navigateTopLevel(LIBRARY_ROUTE) },
                )
            }
            composable(LIBRARY_ROUTE) {
                LibraryRoute(
                    onOpenNowPlaying = morphingPlayerState::expand,
                    viewModel = libraryViewModel,
                )
            }
            composable(SEARCH_ROUTE) {
                SearchRoute(
                    query = searchQuery,
                    tracks = libraryState.tracks,
                    currentTrackId = playbackState.currentTrack?.id,
                    onPlayTrack = { track, queue ->
                        dismissSearchKeyboard()
                        libraryViewModel.playTrack(track, queue)
                    },
                    onPlayArtist = { tracks ->
                        dismissSearchKeyboard()
                        libraryViewModel.playCollection(tracks, dev.behradhz.meowzix.domain.playback.PlaybackMode.ORDERED)
                    },
                    onPlayAlbum = { tracks ->
                        dismissSearchKeyboard()
                        libraryViewModel.playCollection(tracks, dev.behradhz.meowzix.domain.playback.PlaybackMode.ORDERED)
                    },
                )
            }
            composable(QUEUE_ROUTE) {
                QueueRoute()
            }
            composable(PROFILE_ROUTE) {
                ProfileRoute(
                    onOpenOffline = { navController.navigate(DOWNLOADS_ROUTE) },
                    onOpenHistory = { navController.navigate(HISTORY_ROUTE) },
                    onOpenTelegram = { navController.navigate(TELEGRAM_AUTH_ROUTE) },
                )
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MorphingDock(
                hazeState = hazeState,
                destinations = destinations,
                selectedRoute = selectedDockRoute,
                searchActive = searchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchFocusChanged = { searchFieldFocused = it },
                onSelect = { route ->
                    if (route != SEARCH_ROUTE) dismissSearchKeyboard()
                    navigateTopLevel(route)
                },
                onCloseSearch = {
                    dismissSearchKeyboard()
                    if (searchQuery.isNotBlank()) searchQuery = "" else goHome()
                },
            )
        }

        if (playbackState.currentTrack != null) {
            MorphingPlayerOverlay(
                hazeState = hazeState,
                state = playbackState,
                spectrum = spectrum,
                viewModel = playerViewModel,
                morphState = morphingPlayerState,
                onOpenQueue = { navigateTopLevel(QUEUE_ROUTE) },
            )
        }
    }
}

@Composable
private fun MorphingDock(
    hazeState: HazeState,
    destinations: List<DockDestination>,
    selectedRoute: String,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onCloseSearch: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) {
            delay(110)
            runCatching { focusRequester.requestFocus() }
        }
    }

    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .animateContentSize(animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)),
        shape = RoundedCornerShape(36.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = if (searchActive) 0.86f else 0.78f),
        tint = Color.White.copy(alpha = if (searchActive) 0.13f else 0.09f),
    ) {
        AnimatedContent(
            targetState = searchActive,
            transitionSpec = {
                (fadeIn(tween(210, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.98f))
            },
            label = "dock-search-morph",
        ) { isSearch ->
            if (isSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { onSearchFocusChanged(it.isFocused) },
                        decorationBox = { innerField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search songs, artists, albums…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                                    )
                                }
                                innerField()
                            }
                        },
                    )
                    IconButton(onClick = onCloseSearch) {
                        Icon(
                            Icons.Rounded.Clear,
                            contentDescription = if (searchQuery.isBlank()) "Close search" else "Clear search",
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 7.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    destinations.forEach { destination ->
                        DockItem(
                            destination = destination,
                            selected = selectedRoute == destination.route,
                            onClick = { onSelect(destination.route) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    destination: DockDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(58.dp)
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) {
                destination.icon()
            }
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
