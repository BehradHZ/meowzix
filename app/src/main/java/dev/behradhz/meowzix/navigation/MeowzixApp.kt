package dev.behradhz.meowzix.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.behradhz.meowzix.feature.search.SearchViewModel
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
private const val TOP_LEVEL_ENTER_DURATION_MS = 120
private const val TOP_LEVEL_EXIT_DURATION_MS = 90

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
    val searchViewModel: SearchViewModel = hiltViewModel()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()
    val queueState by playerViewModel.queueState.collectAsStateWithLifecycle()
    val spectrum by playerViewModel.spectrum.collectAsStateWithLifecycle()
    val appearance by playerViewModel.appearanceSettings.collectAsStateWithLifecycle()
    val reduceMotion = appearance.reduceMotion
    val searchResults by searchViewModel.results.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: HOME_ROUTE
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var searchContextRoute by rememberSaveable { mutableStateOf<String?>(null) }

    val destinations = remember {
        listOf(
            DockDestination(HOME_ROUTE, "Home") { Icon(Icons.Rounded.Home, contentDescription = null) },
            DockDestination(LIBRARY_ROUTE, "Library") { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            DockDestination(SEARCH_ROUTE, "Search") { Icon(Icons.Rounded.Search, contentDescription = null) },
            DockDestination(QUEUE_ROUTE, "Queue") { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
            DockDestination(PROFILE_ROUTE, "Profile") {
                Image(
                    painter = painterResource(R.drawable.meowzix_logo),
                    contentDescription = null,
                    modifier = Modifier.size(25.dp),
                )
            },
        )
    }

    val secondaryProfileRoutes = remember { setOf(DOWNLOADS_ROUTE, HISTORY_ROUTE, TELEGRAM_AUTH_ROUTE) }
    val selectedDockRoute = if (currentRoute in secondaryProfileRoutes) PROFILE_ROUTE else currentRoute
    val searchActive = searchContextRoute != null
    val searchScope = when (searchContextRoute) {
        LIBRARY_ROUTE -> DockSearchScope.LIBRARY
        QUEUE_ROUTE -> DockSearchScope.QUEUE
        else -> DockSearchScope.GLOBAL
    }

    fun dismissSearchKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        searchFieldFocused = false
    }

    fun clearSearchState() {
        dismissSearchKeyboard()
        searchQuery = ""
        searchContextRoute = null
        searchViewModel.setQuery("")
    }

    fun navigateTopLevel(route: String) {
        clearSearchState()
        if (currentRoute == LIBRARY_ROUTE || route == LIBRARY_ROUTE) {
            libraryViewModel.resetNavigationState()
        }
        if (route == HOME_ROUTE) {
            navController.popBackStack(HOME_ROUTE, inclusive = false)
            return
        }
        navController.navigate(route) {
            popUpTo(HOME_ROUTE) {
                inclusive = false
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    fun goHome() = navigateTopLevel(HOME_ROUTE)

    LaunchedEffect(searchActive, searchScope, searchQuery) {
        if (searchActive && searchScope == DockSearchScope.GLOBAL) {
            searchViewModel.setQuery(searchQuery)
        } else {
            searchViewModel.setQuery("")
        }
    }

    LaunchedEffect(openNowPlayingRequest) {
        if (openNowPlayingRequest) {
            morphingPlayerState.expand()
            onNowPlayingRequestConsumed()
        }
    }

    BackHandler(
        enabled = currentRoute != HOME_ROUTE || searchActive || morphingPlayerState.targetExpanded,
    ) {
        when {
            morphingPlayerState.targetExpanded -> morphingPlayerState.collapse()
            searchActive -> clearSearchState()
            currentRoute in secondaryProfileRoutes -> navigateTopLevel(PROFILE_ROUTE)
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
            enterTransition = { fadeIn(animationSpec = tween(if (reduceMotion) 0 else TOP_LEVEL_ENTER_DURATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(if (reduceMotion) 0 else TOP_LEVEL_EXIT_DURATION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(if (reduceMotion) 0 else TOP_LEVEL_ENTER_DURATION_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(if (reduceMotion) 0 else TOP_LEVEL_EXIT_DURATION_MS)) },
        ) {
            composable(HOME_ROUTE) {
                HomeRoute(
                    hazeState = hazeState,
                    currentTrack = libraryState.tracks.firstOrNull { it.id == playbackState.currentTrack?.id },
                    onPlayTrack = { track, queue -> libraryViewModel.playTrack(track, queue) },
                    onPlayCollection = { tracks ->
                        libraryViewModel.playCollection(
                            tracks,
                            dev.behradhz.meowzix.domain.playback.PlaybackMode.ORDERED,
                        )
                    },
                    onOpenLibrary = { navigateTopLevel(LIBRARY_ROUTE) },
                )
            }
            composable(LIBRARY_ROUTE) {
                LibraryRoute(
                    onOpenNowPlaying = morphingPlayerState::expand,
                    searchQuery = if (searchActive && searchScope == DockSearchScope.LIBRARY) searchQuery else "",
                    viewModel = libraryViewModel,
                )
            }
            composable(QUEUE_ROUTE) {
                QueueRoute(
                    searchQuery = if (searchActive && searchScope == DockSearchScope.QUEUE) searchQuery else "",
                )
            }
            composable(PROFILE_ROUTE) {
                ProfileRoute(
                    onOpenOffline = { navController.navigate(DOWNLOADS_ROUTE) },
                    onOpenHistory = { navController.navigate(HISTORY_ROUTE) },
                    onOpenTelegram = { navController.navigate(TELEGRAM_AUTH_ROUTE) },
                )
            }
            composable(TELEGRAM_AUTH_ROUTE) {
                TelegramAuthRoute(onBack = { navigateTopLevel(PROFILE_ROUTE) })
            }
            composable(DOWNLOADS_ROUTE) { DownloadsRoute() }
            composable(HISTORY_ROUTE) { HistoryRoute() }
        }

        if (searchActive && searchScope == DockSearchScope.GLOBAL && searchQuery.isNotBlank()) {
            DockSearchOverlay(
                hazeState = hazeState,
                scope = searchScope,
                query = searchQuery,
                libraryResults = searchResults,
                queueState = queueState,
                currentTrackId = playbackState.currentTrack?.id,
                onPlayLibraryTrack = { track, results ->
                    clearSearchState()
                    libraryViewModel.playTrack(track, results)
                },
                onPlayQueueIndex = { index ->
                    clearSearchState()
                    playerViewModel.playQueueItemAt(index)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (searchFieldFocused) Modifier.imePadding()
                        else Modifier.navigationBarsPadding(),
                    )
                    .padding(start = 18.dp, end = 18.dp, bottom = 88.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (searchActive && searchFieldFocused) Modifier.imePadding()
                    else Modifier.navigationBarsPadding(),
                )
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    bottom = if (searchActive && searchFieldFocused) 4.dp else 10.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MorphingDock(
                hazeState = hazeState,
                destinations = destinations,
                selectedRoute = if (searchActive) SEARCH_ROUTE else selectedDockRoute,
                searchActive = searchActive,
                searchPlaceholder = searchScope.placeholder,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchFocusChanged = { searchFieldFocused = it },
                onSelect = { route ->
                    if (route == SEARCH_ROUTE) {
                        searchQuery = ""
                        searchContextRoute = when (currentRoute) {
                            LIBRARY_ROUTE -> LIBRARY_ROUTE
                            QUEUE_ROUTE -> QUEUE_ROUTE
                            else -> HOME_ROUTE
                        }
                    } else {
                        navigateTopLevel(route)
                    }
                },
                reduceMotion = reduceMotion,
                onCloseSearch = {
                    if (searchQuery.isNotBlank()) {
                        searchQuery = ""
                    } else {
                        clearSearchState()
                    }
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
    searchPlaceholder: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onCloseSearch: () -> Unit,
    reduceMotion: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun focusSearch() {
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            if (!reduceMotion) delay(110)
            focusSearch()
        }
    }

    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(
                if (reduceMotion) Modifier
                else Modifier.animateContentSize(animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)),
            ),
        shape = RoundedCornerShape(36.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = if (searchActive) 0.90f else 0.78f),
        tint = Color.White.copy(alpha = if (searchActive) 0.15f else 0.09f),
    ) {
        AnimatedContent(
            targetState = searchActive,
            transitionSpec = {
                (fadeIn(tween(if (reduceMotion) 0 else 210, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = if (reduceMotion) 1f else 0.96f, animationSpec = tween(if (reduceMotion) 0 else 210))) togetherWith
                    (fadeOut(tween(if (reduceMotion) 0 else 150)) +
                        scaleOut(targetScale = if (reduceMotion) 1f else 0.98f, animationSpec = tween(if (reduceMotion) 0 else 150)))
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
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { focusSearch() },
                        shape = RoundedCornerShape(23.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Search, contentDescription = "Focus search")
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
                                        text = searchPlaceholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
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
            Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) { destination.icon() }
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
