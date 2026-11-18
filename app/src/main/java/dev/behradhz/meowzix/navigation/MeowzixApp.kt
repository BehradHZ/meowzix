package dev.behradhz.meowzix.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.feature.downloads.DownloadsRoute
import dev.behradhz.meowzix.feature.history.HistoryRoute
import dev.behradhz.meowzix.feature.home.HomeRoute
import dev.behradhz.meowzix.feature.home.HomeSessionIds
import dev.behradhz.meowzix.feature.library.LibraryRoute
import dev.behradhz.meowzix.feature.library.PlaylistsRoute
import dev.behradhz.meowzix.data.repository.AlbumSummary
import dev.behradhz.meowzix.feature.library.LibraryViewModel
import dev.behradhz.meowzix.feature.library.LibraryTracksViewModel
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.feature.profile.SettingsRoute
import dev.behradhz.meowzix.feature.queue.QueueRoute
import dev.behradhz.meowzix.feature.search.SearchViewModel
import dev.behradhz.meowzix.feature.telegramauth.TelegramAuthRoute
import dev.behradhz.meowzix.feature.telegramauth.TelegramAuthViewModel
import dev.behradhz.meowzix.feature.telegramauth.TelegramProxyPanel
import dev.behradhz.meowzix.domain.telegram.telegramProxyBadge
import dev.behradhz.meowzix.ui.theme.LocalMeowzixLogoBackground
import dev.behradhz.meowzix.ui.components.FloatingChromeGlassSurface
import dev.behradhz.meowzix.ui.components.floatingChromeFrame
import dev.behradhz.meowzix.ui.components.LocalMeowzixHeaderControls
import dev.behradhz.meowzix.ui.components.MeowzixHeaderControls
import dev.behradhz.meowzix.ui.components.SecondaryAmbientBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private const val HOME_ROUTE = "home"
private const val LIBRARY_ROUTE = "library"
private const val PLAYLISTS_ROUTE = "playlists"
private const val SEARCH_ROUTE = "search"
private const val QUEUE_ROUTE = "queue"
private const val SETTINGS_ROUTE = "settings"
private const val TELEGRAM_AUTH_ROUTE = "telegram-auth"
private const val DOWNLOADS_ROUTE = "downloads"
private const val HISTORY_ROUTE = "history"
private const val TOP_LEVEL_ENTER_DURATION_MS = 120
private const val TOP_LEVEL_EXIT_DURATION_MS = 90
private val RETAINED_TAB_ROUTES = setOf(HOME_ROUTE, LIBRARY_ROUTE, PLAYLISTS_ROUTE, QUEUE_ROUTE)
private val SETTINGS_CHILD_ROUTES = setOf(DOWNLOADS_ROUTE, HISTORY_ROUTE, TELEGRAM_AUTH_ROUTE)

internal enum class DockTabTapAction { CLOSE_SETTINGS, SWITCH_FROM_SETTINGS, RESELECT, SWITCH }

/** Resolve a dock tap before mutating the back stack; Settings never belongs to a tab's saved state. */
internal fun dockTabTapAction(currentRoute: String, activeTab: String, tappedTab: String): DockTabTapAction =
    when {
        currentRoute == SETTINGS_ROUTE || currentRoute in SETTINGS_CHILD_ROUTES ->
            if (tappedTab == activeTab) DockTabTapAction.CLOSE_SETTINGS else DockTabTapAction.SWITCH_FROM_SETTINGS
        currentRoute == tappedTab -> DockTabTapAction.RESELECT
        else -> DockTabTapAction.SWITCH
    }

internal data class DockDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun MeowzixApp(
    openNowPlayingRequest: Boolean = false,
    onNowPlayingRequestConsumed: () -> Unit = {},
    onApplyLauncherIcon: (dev.behradhz.meowzix.domain.settings.LauncherIconColorSource) -> Unit = {},
    playerViewModel: NowPlayingViewModel = hiltViewModel(),
) {
    // A new session starts when the application returns to the foreground.
    var foregroundSessionId by remember { mutableLongStateOf(0L) }
    LifecycleStartEffect(Unit) {
        foregroundSessionId = HomeSessionIds.next()
        onStopOrDispose { }
    }

    val navController = rememberNavController()
    var visitedTabRoutes by remember { mutableStateOf(setOf(HOME_ROUTE)) }
    var lastContentTab by rememberSaveable { mutableStateOf(HOME_ROUTE) }
    val hazeState = rememberHazeState()
    val morphingPlayerState = rememberMorphingPlayerState()
    val floatingTouchBounds = remember { FloatingControlsTouchBounds() }
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val libraryTracksViewModel: LibraryTracksViewModel = hiltViewModel()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val telegramProxyViewModel: TelegramAuthViewModel = hiltViewModel()
    val proxyState by telegramProxyViewModel.proxyState.collectAsStateWithLifecycle()
    val telegramAuthState by telegramProxyViewModel.state.collectAsStateWithLifecycle()
    var openProxyRequest by remember { mutableIntStateOf(0) }
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val librarySelectionActive by libraryTracksViewModel.selectionActive.collectAsStateWithLifecycle()
    val libraryDisplaySettings by libraryTracksViewModel.displaySettings.collectAsStateWithLifecycle()
    val queueSelectionController = remember { ContextualSelectionController() }
    val playlistSelectionController = remember { ContextualSelectionController() }
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()
    val playerBlocksBackgroundBack =
        playbackState.currentTrack != null && morphingPlayerState.blocksBackgroundBack
    val appearance by playerViewModel.appearanceSettings.collectAsStateWithLifecycle()
    val reduceMotion = appearance.reduceMotion
    val searchResults by searchViewModel.results.collectAsStateWithLifecycle()
    val searchArtists by searchViewModel.artists.collectAsStateWithLifecycle()
    val searchAlbums by searchViewModel.albums.collectAsStateWithLifecycle()
    val searchPlaylists by searchViewModel.playlists.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: HOME_ROUTE
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingArtist by remember { mutableStateOf<String?>(null) }
    var pendingAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var pendingPlaylistId by remember { mutableStateOf<java.util.UUID?>(null) }
    var searchContextRoute by rememberSaveable { mutableStateOf<String?>(null) }

    val destinations = remember {
        listOf(
            DockDestination(HOME_ROUTE, "Home") { Icon(Icons.Rounded.Home, contentDescription = null) },
            DockDestination(LIBRARY_ROUTE, "Library") { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            DockDestination(PLAYLISTS_ROUTE, "Playlists") { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
            DockDestination(QUEUE_ROUTE, "Queue") { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
        )
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute in RETAINED_TAB_ROUTES) {
            lastContentTab = currentRoute
            visitedTabRoutes = visitedTabRoutes + currentRoute
        }
    }
    // The same global Settings destination can be opened from any tab.
    val selectedDockRoute =
        if (currentRoute == SETTINGS_ROUTE || currentRoute in SETTINGS_CHILD_ROUTES) lastContentTab else currentRoute
    val searchActive = searchContextRoute != null
    val librarySelectionVisible = currentRoute == LIBRARY_ROUTE && librarySelectionActive
    val contextualSelectionVisible = librarySelectionVisible ||
        (currentRoute == QUEUE_ROUTE && queueSelectionController.active) ||
        (currentRoute == PLAYLISTS_ROUTE && playlistSelectionController.active)
    val searchScope = when (searchContextRoute) {
        LIBRARY_ROUTE -> DockSearchScope.LIBRARY
        PLAYLISTS_ROUTE -> DockSearchScope.PLAYLIST
        QUEUE_ROUTE -> DockSearchScope.QUEUE
        else -> DockSearchScope.GLOBAL
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var dockCompact by rememberSaveable { mutableStateOf(false) }
    val queueReorderGestureState = remember { QueueReorderGestureState() }
    var reachedScrollEnd by remember(currentRoute) { mutableStateOf(false) }
    // Incremented even when Home is already selected: reselecting the tab
    // should scroll its retained LazyColumn back to the header.
    var homeScrollToTopRequest by remember { mutableIntStateOf(0) }
    var libraryScrollToTopRequest by remember { mutableIntStateOf(0) }
    var queueScrollToTopRequest by remember { mutableIntStateOf(0) }
    var playlistReselectRequest by remember { mutableIntStateOf(0) }
    var scrollDelta by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 28.dp.toPx() }
    val dockScrollConnection = remember(currentRoute, searchActive, contextualSelectionVisible, threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || searchActive || contextualSelectionVisible ||
                    (currentRoute == QUEUE_ROUTE && queueReorderGestureState.active)
                ) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero
                if (delta > 0f) reachedScrollEnd = false
                if (delta < 0f && reachedScrollEnd) return Offset.Zero
                if (delta * scrollDelta < 0f) scrollDelta = 0f
                scrollDelta += delta
                if (scrollDelta <= -threshold) {
                    dockCompact = true
                    scrollDelta = 0f
                } else if (scrollDelta >= threshold) {
                    dockCompact = false
                    scrollDelta = 0f
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // At the true bottom of a scrollable page, the remaining upward
                // drag is unconsumed. Expand the dock so the reserved footer is filled.
                if (contextualSelectionVisible || (currentRoute == QUEUE_ROUTE && queueReorderGestureState.active)) return Offset.Zero
                if (shouldExpandDockAtEnd(
                        isUserInput = source == NestedScrollSource.UserInput,
                        searchActive = searchActive,
                        compact = dockCompact,
                        unconsumedDy = available.y,
                    )
                ) {
                    reachedScrollEnd = true
                    dockCompact = false
                    scrollDelta = 0f
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(currentRoute, searchActive, contextualSelectionVisible) {
        dockCompact = false
        reachedScrollEnd = false
        scrollDelta = 0f
        if (currentRoute != QUEUE_ROUTE) queueReorderGestureState.active = false
    }

    fun dismissSearchKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    fun clearSearchState() {
        dismissSearchKeyboard()
        searchQuery = ""
        searchContextRoute = null
        searchViewModel.setQuery("")
    }

    fun navigateTopLevel(route: String) {
        val action = dockTabTapAction(currentRoute, lastContentTab, route)
        if (route in RETAINED_TAB_ROUTES) visitedTabRoutes = visitedTabRoutes + route

        if (action == DockTabTapAction.CLOSE_SETTINGS) {
            clearSearchState()
            // One Back action dismisses Settings and all of its nested screens.
            navController.popBackStack(SETTINGS_ROUTE, inclusive = true)
            return
        }
        if (action == DockTabTapAction.SWITCH_FROM_SETTINGS) {
            // Remove Settings before saveState/restoreState can associate it with the origin tab.
            navController.popBackStack(SETTINGS_ROUTE, inclusive = true)
        }

        if (action == DockTabTapAction.RESELECT) {
            clearSearchState()
            dockCompact = false
            scrollDelta = 0f
            when (route) {
                HOME_ROUTE -> homeScrollToTopRequest++
                LIBRARY_ROUTE -> libraryScrollToTopRequest++
                PLAYLISTS_ROUTE -> playlistReselectRequest++
                QUEUE_ROUTE -> if (!queueReorderGestureState.active) queueScrollToTopRequest++
            }
            return // Don't create a new navigation entry on reselect.
        }
        clearSearchState()
        if (route == HOME_ROUTE) {
            dockCompact = false
            scrollDelta = 0f
            homeScrollToTopRequest++
        }
        lastContentTab = route
        navController.navigate(route) {
            popUpTo(HOME_ROUTE) {
                inclusive = false
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goHome() = navigateTopLevel(HOME_ROUTE)

    fun toggleSettings() {
        when {
            currentRoute == SETTINGS_ROUTE -> navController.popBackStack()
            currentRoute in SETTINGS_CHILD_ROUTES -> navController.popBackStack(SETTINGS_ROUTE, false)
            else -> {
                clearSearchState()
                navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true }
            }
        }
    }

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
        enabled = currentRoute != HOME_ROUTE || searchActive || playerBlocksBackgroundBack,
    ) {
        when {
            playerBlocksBackgroundBack -> morphingPlayerState.collapse()
            searchActive -> clearSearchState()
            currentRoute in SETTINGS_CHILD_ROUTES -> navController.popBackStack()
            currentRoute == SETTINGS_ROUTE -> navController.popBackStack()
            currentRoute != HOME_ROUTE -> goHome()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentRoute != HOME_ROUTE) SecondaryAmbientBackdrop(variation = 0)
        CompositionLocalProvider(
            LocalMeowzixHeaderControls provides MeowzixHeaderControls(
                onSettings = if (currentRoute == SETTINGS_ROUTE || currentRoute in SETTINGS_CHILD_ROUTES) null
                    else { { toggleSettings() } },
                onProxy = { openProxyRequest++ },
                proxyBadge = telegramProxyBadge(
                    proxyState.proxies.any { it.enabled },
                    telegramAuthState.connectionStatus,
                ),
            ),
            LocalDockLayout provides DockLayout(
                compact = dockCompact && !searchActive && !contextualSelectionVisible,
                reduceMotion = reduceMotion,
            ),
            LocalFloatingControlsTouchBounds provides floatingTouchBounds,
            LocalQueueReorderGestureState provides queueReorderGestureState,
        ) {
        // The scroll connection must be an ancestor of retained tabs as well as NavHost.
        // Keeping it on NavHost alone misses all top-level tab scroll gestures.
        Box(Modifier.fillMaxSize().nestedScroll(dockScrollConnection)) {
        // Keep visited destinations in composition; inactive tabs are not placed, painted, or hit-tested.
            if (HOME_ROUTE in visitedTabRoutes) RetainedTopLevelTab(active = currentRoute == HOME_ROUTE, hazeState = hazeState) {
                HomeRoute(
                    isActive = currentRoute == HOME_ROUTE,
                    hazeState = hazeState,
                    scrollToTopRequest = homeScrollToTopRequest,
                    foregroundSessionId = foregroundSessionId,
                    reduceMotion = reduceMotion,
                    currentTrack = libraryState.tracks.firstOrNull { it.id == playbackState.currentTrack?.id },
                    onPlayCollection = { tracks ->
                        libraryViewModel.playCollection(
                            tracks,
                            dev.behradhz.meowzix.domain.playback.PlaybackMode.ORDERED,
                        )
                    },
                    onOpenLibrary = { navigateTopLevel(LIBRARY_ROUTE) },
                    onOpenPlaylist = { id ->
                        pendingPlaylistId = id
                        navigateTopLevel(PLAYLISTS_ROUTE)
                    },
                )
            }
            if (LIBRARY_ROUTE in visitedTabRoutes) RetainedTopLevelTab(active = currentRoute == LIBRARY_ROUTE, hazeState = hazeState) {
                LibraryRoute(
                    isActive = currentRoute == LIBRARY_ROUTE && !playerBlocksBackgroundBack,
                    onOpenNowPlaying = {
                        if (searchActive) clearSearchState()
                        morphingPlayerState.expand()
                    },
                    searchQuery = if (searchActive && searchScope == DockSearchScope.LIBRARY) searchQuery else "",
                    viewModel = libraryViewModel,
                    tracksViewModel = libraryTracksViewModel,
                    requestedArtist = pendingArtist,
                    scrollToTopRequest = libraryScrollToTopRequest,
                    onArtistRequestConsumed = { pendingArtist = null },
                    requestedAlbum = pendingAlbum,
                    onAlbumRequestConsumed = { pendingAlbum = null },
                )
            }
            if (PLAYLISTS_ROUTE in visitedTabRoutes) RetainedTopLevelTab(active = currentRoute == PLAYLISTS_ROUTE, hazeState = hazeState) {
                PlaylistsRoute(
                    isActive = currentRoute == PLAYLISTS_ROUTE && !playerBlocksBackgroundBack,
                    selectionController = playlistSelectionController,
                    selectionMoreActions = libraryDisplaySettings.selection.normalized().more,
                    viewModel = libraryViewModel,
                    searchQuery = if (searchActive && searchScope == DockSearchScope.PLAYLIST) searchQuery else "",
                    onOpenNowPlaying = { morphingPlayerState.expand() },
                    onGoToArtist = { artist ->
                        pendingArtist = artist
                        navigateTopLevel(LIBRARY_ROUTE)
                    },
                    requestedPlaylistId = pendingPlaylistId,
                    reselectRequest = playlistReselectRequest,
                    onPlaylistRequestConsumed = { pendingPlaylistId = null },
                    reduceMotion = reduceMotion,
                )
            }
            if (QUEUE_ROUTE in visitedTabRoutes) RetainedTopLevelTab(active = currentRoute == QUEUE_ROUTE, hazeState = hazeState) {
                QueueRoute(
                    isActive = currentRoute == QUEUE_ROUTE && !playerBlocksBackgroundBack,
                    selectionController = queueSelectionController,
                    selectionMoreActions = libraryDisplaySettings.selection.normalized().more,
                    scrollToTopRequest = queueScrollToTopRequest,
                    searchQuery = if (searchActive && searchScope == DockSearchScope.QUEUE) searchQuery else "",
                    onGoToArtist = { artist ->
                        pendingArtist = artist
                        navigateTopLevel(LIBRARY_ROUTE)
                    },
                )
            }

        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            enterTransition = {
                if (!reduceMotion && targetState.destination.route == SETTINGS_ROUTE)
                    fadeIn(tween(260)) + slideInHorizontally(tween(300), initialOffsetX = { it / 6 })
                else fadeIn(tween(if (reduceMotion) 0 else TOP_LEVEL_ENTER_DURATION_MS))
            },
            exitTransition = {
                if (!reduceMotion && initialState.destination.route == SETTINGS_ROUTE)
                    fadeOut(tween(210)) + slideOutHorizontally(tween(250), targetOffsetX = { it / 6 })
                else fadeOut(tween(if (reduceMotion) 0 else TOP_LEVEL_EXIT_DURATION_MS))
            },
            popEnterTransition = {
                if (!reduceMotion && targetState.destination.route == SETTINGS_ROUTE)
                    fadeIn(tween(260)) + slideInHorizontally(tween(300), initialOffsetX = { it / 6 })
                else fadeIn(tween(if (reduceMotion) 0 else TOP_LEVEL_ENTER_DURATION_MS))
            },
            popExitTransition = {
                if (!reduceMotion && initialState.destination.route == SETTINGS_ROUTE)
                    fadeOut(tween(210)) + slideOutHorizontally(tween(250), targetOffsetX = { it / 6 })
                else fadeOut(tween(if (reduceMotion) 0 else TOP_LEVEL_EXIT_DURATION_MS))
            },
        ) {
            composable(HOME_ROUTE) { }
            composable(LIBRARY_ROUTE) { }
            composable(PLAYLISTS_ROUTE) { }
            composable(QUEUE_ROUTE) { }
            composable(SETTINGS_ROUTE) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenOffline = { navController.navigate(DOWNLOADS_ROUTE) },
                    onOpenHistory = { navController.navigate(HISTORY_ROUTE) },
                    onOpenTelegram = { navController.navigate(TELEGRAM_AUTH_ROUTE) },
                    onApplyLauncherIcon = onApplyLauncherIcon,
                )
            }
            composable(TELEGRAM_AUTH_ROUTE) {
                TelegramAuthRoute(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(DOWNLOADS_ROUTE) { DownloadsRoute(onBack = { navController.popBackStack() }) }
            composable(HISTORY_ROUTE) { HistoryRoute(onBack = { navController.popBackStack() }) }
        }
        }
        }

        // Search results and the dock share one IME/navigation-bar anchor, independent of focus.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searchActive && searchScope == DockSearchScope.GLOBAL && searchQuery.isNotBlank()) {
                // The root UI does not need to collect Queue updates when search is closed.
                val queueState by playerViewModel.queueState.collectAsStateWithLifecycle()
                DockSearchOverlay(
                    hazeState = hazeState,
                    scope = searchScope,
                    query = searchQuery,
                    libraryResults = searchResults,
                    artistResults = searchArtists,
                    albumResults = searchAlbums,
                    playlistResults = searchPlaylists,
                    allPlaylists = libraryState.playlists,
                    libraryAvailability = libraryState.availability,
                    downloads = libraryState.downloads,
                    queueState = queueState,
                    currentTrackId = playbackState.currentTrack?.id,
                    onPlayLibraryTrack = { track ->
                        clearSearchState()
                        libraryViewModel.playLibrarySearchTrack(track)
                    },
                    onPlayNext = libraryViewModel::playNext,
                    onAddToQueue = libraryViewModel::addToQueue,
                    onPinOffline = libraryViewModel::pinOffline,
                    onRemoveOfflineCopy = libraryViewModel::removeOfflineCopy,
                    onRemoveTrack = libraryViewModel::removeTrack,
                    onFavorite = libraryViewModel::setFavorite,
                    onAddToPlaylist = libraryViewModel::addToPlaylist,
                    onGoToArtist = { track ->
                        track.artist?.takeIf(String::isNotBlank)?.let { artist ->
                            pendingArtist = artist
                            navigateTopLevel(LIBRARY_ROUTE)
                        }
                    },
                    onEnsureArtwork = libraryViewModel::ensureArtwork,
                    // Browsing, downloading or opening a menu never acknowledges playback.
                    onTrackInteracted = { _ -> },
                    onPlayNextArtist = { artist -> libraryTracksViewModel.queueArtist(artist, next = true) },
                    onQueueArtist = { artist -> libraryTracksViewModel.queueArtist(artist, next = false) },
                    onPlayNextAlbum = { album -> libraryTracksViewModel.queueAlbum(album, next = true) },
                    onQueueAlbum = { album -> libraryTracksViewModel.queueAlbum(album, next = false) },
                    onOpenArtist = { artist ->
                        pendingArtist = artist.name
                        navigateTopLevel(LIBRARY_ROUTE)
                    },
                    onOpenAlbum = { album ->
                        pendingAlbum = album
                        navigateTopLevel(LIBRARY_ROUTE)
                    },
                    onOpenPlaylist = { playlist ->
                        pendingPlaylistId = playlist.id
                        navigateTopLevel(PLAYLISTS_ROUTE)
                    },
                    onPlayQueueIndex = { index ->
                        clearSearchState()
                        playerViewModel.playQueueItemAt(index)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
            Box(
                Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                    floatingTouchBounds.dockTopY = coordinates.boundsInWindow().top
                },
            ) {
            MorphingDock(
                hazeState = hazeState,
                destinations = destinations,
                selectedRoute = if (searchActive) SEARCH_ROUTE else selectedDockRoute,
                selectionActions = if (contextualSelectionVisible)
                    libraryDisplaySettings.selection.normalized().dock else emptyList(),
                onSelectionAction = { action ->
                    when (currentRoute) {
                        LIBRARY_ROUTE -> libraryTracksViewModel.requestSelectionAction(action)
                        QUEUE_ROUTE -> queueSelectionController.onAction(action)
                        PLAYLISTS_ROUTE -> playlistSelectionController.onAction(action)
                    }
                },
                searchActive = searchActive,
                dockCompact = dockCompact && !contextualSelectionVisible,
                searchPlaceholder = searchScope.placeholder,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onExpandCompact = {
                    if (!queueReorderGestureState.active) {
                    // This is a geometry action, not navigation; the mini player
                    // observes dockCompact and rises with the same eased timeline.
                    dockCompact = false
                    scrollDelta = 0f
                    }
                },
                onSelect = { route ->
                    if (!queueReorderGestureState.active) {
                    if (route == SEARCH_ROUTE) {
                        searchQuery = ""
                        searchContextRoute = when (currentRoute) {
                            LIBRARY_ROUTE -> LIBRARY_ROUTE
                            PLAYLISTS_ROUTE -> PLAYLISTS_ROUTE
                            QUEUE_ROUTE -> QUEUE_ROUTE
                            else -> HOME_ROUTE
                        }
                    } else {
                        navigateTopLevel(route)
                    }
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
        }

        // The collapsed player must not cover the active keyboard or global search results.
        val searchOccludesPlayer = searchActive && (
            imeVisible || (searchScope == DockSearchScope.GLOBAL && searchQuery.isNotBlank())
        )
        if (playbackState.currentTrack != null && (!searchOccludesPlayer || morphingPlayerState.targetExpanded)) {
            MorphingPlayerOverlay(
                hazeState = hazeState,
                state = playbackState,
                viewModel = playerViewModel,
                morphState = morphingPlayerState,
                dockCompact = dockCompact && !searchActive && !contextualSelectionVisible,
                onMiniPlayerTopInWindow = { floatingTouchBounds.miniPlayerTopY = it },
                onOpenQueue = { navigateTopLevel(QUEUE_ROUTE) },
                onGoToArtist = { artist ->
                    pendingArtist = artist
                    navigateTopLevel(LIBRARY_ROUTE)
                },
            )
        }

        TelegramProxyPanel(
            state = proxyState,
            connectionStatus = telegramAuthState.connectionStatus,
            onAdd = telegramProxyViewModel::addProxy,
            onImport = telegramProxyViewModel::importProxyLink,
            onRefresh = telegramProxyViewModel::refreshProxies,
            onEnable = telegramProxyViewModel::enableProxy,
            onDisable = telegramProxyViewModel::disableProxy,
            onRemove = telegramProxyViewModel::removeProxy,
            onPing = telegramProxyViewModel::pingProxy,
            onClearError = telegramProxyViewModel::clearProxyError,
            showTrigger = false,
            openRequest = openProxyRequest,
        )
    }
}

/** Retains LazyListState, artwork caches and subpage state without off-screen drawing or touch handling. */
@Composable
private fun RetainedTopLevelTab(
    active: Boolean,
    hazeState: HazeState,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().layout { measurable, constraints ->
            val child = measurable.measure(constraints)
            layout(child.width, child.height) {
                if (active) child.place(0, 0)
            }
        }.hazeSource(hazeState),
    ) { content() }
}

@Composable
internal fun MorphingDock(
    hazeState: HazeState,
    destinations: List<DockDestination>,
    selectedRoute: String,
    searchActive: Boolean,
    dockCompact: Boolean,
    searchPlaceholder: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onExpandCompact: () -> Unit,
    onCloseSearch: () -> Unit,
    reduceMotion: Boolean = false,
    selectionActions: List<LibrarySelectionAction> = emptyList(),
    onSelectionAction: (LibrarySelectionAction) -> Unit = {},
) {
    // Retain the very same glass surfaces while swapping dock content. Search must
    // keep its reversible morph even when Library selection is active.
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Wait for the bubble to occupy the dock before the IME starts resizing the footer.
    // LaunchedEffect cancels the pending focus if search is closed mid-transition.
    LaunchedEffect(searchActive, reduceMotion) {
        if (searchActive) {
            if (!reduceMotion) delay(DockSearchOpenDurationMs.toLong() - 35L)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    // Keep both glass bubbles in one layout. Switching the entire composable on
    // searchActive caused the previous instant cut, and could not animate back.
    val searchMorph by animateFloatAsState(
        targetValue = if (searchActive) 1f else 0f,
        animationSpec = dockSearchMorphSpec(searchActive, reduceMotion),
        label = "search-dock-morph",
    )
    val compact = dockCompact && !searchActive && selectionActions.size != 4
    val morph by animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = dockMorphSpec(compact, reduceMotion),
        label = "dock-morph",
    )
    val expandedAlpha = ((0.48f - morph) / 0.38f).coerceIn(0f, 1f)
    val compactAlpha = ((morph - 0.18f) / 0.55f).coerceIn(0f, 1f)
    val navigationAlpha = dockSearchNavigationAlpha(searchMorph)
    val searchInputAlpha = dockSearchInputAlpha(searchMorph)
    val activeDestination = destinations.firstOrNull { it.route == selectedRoute }
        ?: destinations.first()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        val compactSize = CompactDockButtonDp.dp
        val leftWidth = lerp(maxWidth - 72.dp, compactSize, morph)
        val leftHeight = lerp(72.dp, compactSize, morph)
        val searchSize = lerp(62.dp, compactSize, morph)
        val dockShape = RoundedCornerShape(lerp(36.dp, 26.dp, morph))

        // The Search circle retains its original end-edge anchor as it grows
        // across the dock. The very same glass surface is used in both states.
        val searchWidth = lerp(searchSize, maxWidth, searchMorph)
        val searchHeight = lerp(searchSize, 72.dp, searchMorph)
        val searchShape = RoundedCornerShape(lerp(searchSize / 2, 36.dp, searchMorph))
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingChromeGlassSurface(
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(leftWidth)
                    .height(leftHeight)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = navigationAlpha
                    }
                    .floatingChromeFrame(dockShape),
                shape = dockShape,
            ) {
                // Crossfade the four destinations into contextual actions, without unmounting
                // the dock or its Search bubble. Rapid enter/exit remains reversible.
                if (expandedAlpha > 0.01f && navigationAlpha > 0.01f) {
                    Box(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                            alpha = expandedAlpha
                            translationX = -morph * 10.dp.toPx()
                        },
                    ) {
                        Crossfade(
                            // Preserve the outgoing action snapshot while it fades away.
                            targetState = selectionActions.takeIf { it.size == 4 },
                            animationSpec = tween(if (reduceMotion) 0 else 260),
                            label = "library-selection-dock",
                        ) { activeSelectionActions ->
                            if (activeSelectionActions != null) {
                                SelectionDockActions(
                                    actions = activeSelectionActions,
                                    onAction = onSelectionAction,
                                    enabled = selectionActions.size == 4 && !searchActive,
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    destinations.forEach { destination ->
                                        DockItem(
                                            destination = destination,
                                            selected = selectedRoute == destination.route,
                                            onClick = { onSelect(destination.route) },
                                            modifier = Modifier.weight(1f),
                                            enabled = selectionActions.size != 4 &&
                                                !searchActive && searchMorph < 0.06f && morph < 0.40f,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (compactAlpha > 0.01f && navigationAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                alpha = compactAlpha
                                val scale = 0.88f + 0.12f * morph
                                scaleX = scale
                                scaleY = scale
                            }
                            .semantics { contentDescription = "Expand navigation dock" }
                            .clickable(enabled = compact && morph > 0.55f && searchMorph < 0.06f) {
                                onExpandCompact()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(23.dp), contentAlignment = Alignment.Center) {
                            activeDestination.icon()
                        }
                    }
                }
            }

            FloatingChromeGlassSurface(
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(searchWidth)
                    .height(searchHeight)
                    .testTag("dock-search-bubble")
                    .floatingChromeFrame(searchShape)
                    .clickable(enabled = !searchActive && searchMorph < 0.06f) {
                        onSelect(SEARCH_ROUTE)
                    },
                shape = searchShape,
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Keep a single Search icon anchored to the original circle.
                    // Only the surrounding glass and the input grow; the icon
                    // never jumps from a centered button to the opposite edge.
                    if (searchMorph > 0.32f) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                    alpha = searchInputAlpha
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onCloseSearch,
                                enabled = searchActive && searchInputAlpha > 0.85f,
                            ) {
                                Icon(
                                    Icons.Rounded.Clear,
                                    contentDescription = if (searchQuery.isBlank()) "Close search" else "Clear search",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                enabled = searchActive,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                searchPlaceholder,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                            Spacer(Modifier.width(searchSize))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(searchSize)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = if (searchActive) null else "Search all music",
                            tint = if (searchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(lerp(25.dp, 23.dp, searchMorph)),
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
    showLabel: Boolean = true,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
        },
        shape = shape,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) { destination.icon() }
            if (showLabel) Text(
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
