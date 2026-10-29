package dev.behradhz.meowzix.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
import dev.behradhz.meowzix.feature.library.PlaylistsRoute
import dev.behradhz.meowzix.data.repository.AlbumSummary
import dev.behradhz.meowzix.feature.library.LibraryViewModel
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.feature.profile.SettingsRoute
import dev.behradhz.meowzix.feature.queue.QueueRoute
import dev.behradhz.meowzix.feature.search.SearchViewModel
import dev.behradhz.meowzix.feature.telegramauth.TelegramAuthRoute
import dev.behradhz.meowzix.ui.theme.LocalMeowzixLogoBackground
import dev.behradhz.meowzix.ui.components.GlassSurface
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

internal data class DockDestination(
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

    val secondarySettingsRoutes = remember { setOf(DOWNLOADS_ROUTE, HISTORY_ROUTE, TELEGRAM_AUTH_ROUTE) }
    val selectedDockRoute = if (currentRoute in secondarySettingsRoutes || currentRoute == SETTINGS_ROUTE) HOME_ROUTE else currentRoute
    val searchActive = searchContextRoute != null
    val searchScope = when (searchContextRoute) {
        LIBRARY_ROUTE -> DockSearchScope.LIBRARY
        QUEUE_ROUTE -> DockSearchScope.QUEUE
        else -> DockSearchScope.GLOBAL
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var dockCompact by rememberSaveable { mutableStateOf(false) }
    // Incremented even when Home is already selected: reselecting the tab
    // should scroll its retained LazyColumn back to the header.
    var homeScrollToTopRequest by remember { mutableIntStateOf(0) }
    var scrollDelta by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 28.dp.toPx() }
    val dockScrollConnection = remember(currentRoute, searchActive, threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || searchActive) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero
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
        }
    }
    LaunchedEffect(currentRoute, searchActive) {
        dockCompact = false
        scrollDelta = 0f
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
        // Refresh only on an intentional second tap of the already-selected Library tab.
        if (route == LIBRARY_ROUTE && currentRoute == LIBRARY_ROUTE && !searchActive) {
            libraryViewModel.refresh()
            return
        }
        clearSearchState()
        if (currentRoute == LIBRARY_ROUTE || route == LIBRARY_ROUTE) {
            libraryViewModel.resetNavigationState()
        }
        if (route == HOME_ROUTE) {
            dockCompact = false
            scrollDelta = 0f
            homeScrollToTopRequest++
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
            currentRoute in secondarySettingsRoutes -> navController.popBackStack()
            currentRoute != HOME_ROUTE -> goHome()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentRoute != HOME_ROUTE) {
            SecondaryAmbientBackdrop(
                variation = when (currentRoute) {
                    LIBRARY_ROUTE -> 0
                    PLAYLISTS_ROUTE -> 1
                    QUEUE_ROUTE -> 3
                    SETTINGS_ROUTE -> 2
                    else -> 4
                },
            )
        }
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(dockScrollConnection)
                .hazeSource(hazeState),
            enterTransition = { fadeIn(animationSpec = tween(if (reduceMotion) 0 else TOP_LEVEL_ENTER_DURATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(if (reduceMotion) 0 else TOP_LEVEL_EXIT_DURATION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(if (reduceMotion) 0 else TOP_LEVEL_ENTER_DURATION_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(if (reduceMotion) 0 else TOP_LEVEL_EXIT_DURATION_MS)) },
        ) {
            composable(HOME_ROUTE) {
                HomeRoute(
                    hazeState = hazeState,
                    scrollToTopRequest = homeScrollToTopRequest,
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
                    onOpenSettings = { navigateTopLevel(SETTINGS_ROUTE) },
                )
            }
            composable(LIBRARY_ROUTE) {
                LibraryRoute(
                    onOpenNowPlaying = {
                        if (searchActive) clearSearchState()
                        morphingPlayerState.expand()
                    },
                    searchQuery = if (searchActive && searchScope == DockSearchScope.LIBRARY) searchQuery else "",
                    viewModel = libraryViewModel,
                    requestedArtist = pendingArtist,
                    onArtistRequestConsumed = { pendingArtist = null },
                    requestedAlbum = pendingAlbum,
                    onAlbumRequestConsumed = { pendingAlbum = null },
                )
            }
            composable(PLAYLISTS_ROUTE) {
                PlaylistsRoute(
                    viewModel = libraryViewModel,
                    onOpenNowPlaying = { morphingPlayerState.expand() },
                    onGoToArtist = { artist ->
                        pendingArtist = artist
                        navigateTopLevel(LIBRARY_ROUTE)
                    },
                    requestedPlaylistId = pendingPlaylistId,
                    onPlaylistRequestConsumed = { pendingPlaylistId = null },
                    reduceMotion = reduceMotion,
                )
            }
            composable(QUEUE_ROUTE) {
                QueueRoute(
                    searchQuery = if (searchActive && searchScope == DockSearchScope.QUEUE) searchQuery else "",
                    onGoToArtist = { artist ->
                        pendingArtist = artist
                        navigateTopLevel(LIBRARY_ROUTE)
                    },
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenOffline = { navController.navigate(DOWNLOADS_ROUTE) },
                    onOpenHistory = { navController.navigate(HISTORY_ROUTE) },
                    onOpenTelegram = { navController.navigate(TELEGRAM_AUTH_ROUTE) },
                )
            }
            composable(TELEGRAM_AUTH_ROUTE) {
                TelegramAuthRoute(onBack = { navController.popBackStack() })
            }
            composable(DOWNLOADS_ROUTE) { DownloadsRoute() }
            composable(HISTORY_ROUTE) { HistoryRoute() }
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
                DockSearchOverlay(
                    hazeState = hazeState,
                    scope = searchScope,
                    query = searchQuery,
                    libraryResults = searchResults,
                    artistResults = searchArtists,
                    albumResults = searchAlbums,
                    playlistResults = searchPlaylists,
                    queueState = queueState,
                    currentTrackId = playbackState.currentTrack?.id,
                    onPlayLibraryTrack = { track ->
                        clearSearchState()
                        libraryViewModel.playLibrarySearchTrack(track)
                    },
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
            MorphingDock(
                hazeState = hazeState,
                destinations = destinations,
                selectedRoute = if (searchActive) SEARCH_ROUTE else selectedDockRoute,
                searchActive = searchActive,
                dockCompact = dockCompact,
                searchPlaceholder = searchScope.placeholder,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onExpandCompact = {
                    // This is a geometry action, not navigation; the mini player
                    // observes dockCompact and rises with the same eased timeline.
                    dockCompact = false
                    scrollDelta = 0f
                },
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

        // The collapsed player must not cover the active keyboard or global search results.
        val searchOccludesPlayer = searchActive && (
            imeVisible || (searchScope == DockSearchScope.GLOBAL && searchQuery.isNotBlank())
        )
        if (playbackState.currentTrack != null && (!searchOccludesPlayer || morphingPlayerState.targetExpanded)) {
            MorphingPlayerOverlay(
                hazeState = hazeState,
                state = playbackState,
                spectrum = spectrum,
                viewModel = playerViewModel,
                morphState = morphingPlayerState,
                dockCompact = dockCompact && !searchActive,
                onOpenQueue = { navigateTopLevel(QUEUE_ROUTE) },
                onGoToArtist = { artist ->
                    pendingArtist = artist
                    navigateTopLevel(LIBRARY_ROUTE)
                },
            )
        }
    }
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
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchActive) {
        if (searchActive) {
            if (!reduceMotion) delay(110)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    if (searchActive) {
        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(36.dp),
            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
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
                                Text(searchPlaceholder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            }
                            inner()
                        }
                    },
                )
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Rounded.Clear, contentDescription = if (searchQuery.isBlank()) "Close search" else "Clear search")
                }
            }
        }
        return
    }

    // One eased, reversible timeline controls width, height, icon crossfade and
    // the motion-blur pulse. Avoid switching layouts on the boolean itself.
    val morph by animateFloatAsState(
        targetValue = if (dockCompact) 1f else 0f,
        animationSpec = dockMorphSpec(dockCompact, reduceMotion),
        label = "dock-morph",
    )
    val motionBlur = dockMotionBlurDp(morph, reduceMotion).dp
    val expandedAlpha = ((0.48f - morph) / 0.38f).coerceIn(0f, 1f)
    val compactAlpha = ((morph - 0.18f) / 0.55f).coerceIn(0f, 1f)
    val activeDestination = destinations.firstOrNull { it.route == selectedRoute }
        ?: destinations.first()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        val compactSize = CompactDockButtonDp.dp
        val leftWidth = lerp(maxWidth - 72.dp, compactSize, morph)
        val leftHeight = lerp(72.dp, compactSize, morph)
        val searchSize = lerp(62.dp, compactSize, morph)
        // Both edges have fixed anchors. Shrinking the left dock must never
        // pull Search toward the center or underneath the compact player.
        Box(modifier = Modifier.fillMaxSize()) {
            GlassSurface(
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(leftWidth)
                    .height(leftHeight)
                    .blur(motionBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                shape = RoundedCornerShape(lerp(36.dp, 26.dp, morph)),
                fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f),
            ) {
                // Keep the four destinations composed long enough to fade out,
                // then let the selected destination take over the circular dock.
                if (expandedAlpha > 0.01f) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 5.dp, vertical = 7.dp)
                            .graphicsLayer {
                                alpha = expandedAlpha
                                translationX = -morph * 10.dp.toPx()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        destinations.forEach { destination ->
                            DockItem(
                                destination = destination,
                                selected = selectedRoute == destination.route,
                                onClick = { onSelect(destination.route) },
                                modifier = Modifier.weight(1f),
                                enabled = morph < 0.40f,
                            )
                        }
                    }
                }
                if (compactAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = compactAlpha
                                val scale = 0.88f + 0.12f * morph
                                scaleX = scale
                                scaleY = scale
                            }
                            .semantics { contentDescription = "Expand navigation dock" }
                            .clickable(enabled = dockCompact && morph > 0.55f) {
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
            GlassSurface(
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(searchSize)
                    .blur(motionBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .clickable { onSelect(SEARCH_ROUTE) },
                shape = RoundedCornerShape(searchSize / 2),
                fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search all music",
                        modifier = Modifier.size(lerp(25.dp, 22.dp, morph)),
                    )
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
    Surface(
        modifier = modifier
            .height(58.dp)
            .clickable(enabled = enabled, onClick = onClick),
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
