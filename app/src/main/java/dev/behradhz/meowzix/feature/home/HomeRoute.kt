package dev.behradhz.meowzix.feature.home

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import dev.behradhz.meowzix.navigation.dockAwareBottomPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import dev.behradhz.meowzix.ui.components.GlassLazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.core.permissions.AudioPermission
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.MeowzixStickyHeader
import dev.behradhz.meowzix.ui.components.SecondaryAmbientBackdrop
import dev.chrisbanes.haze.hazeSource
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun HomeRoute(
    hazeState: HazeState,
    currentTrack: Track?,
    onPlayCollection: (List<Track>) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlaylist: (java.util.UUID) -> Unit,
    scrollToTopRequest: Int = 0,
    foregroundSessionId: Long = 1L,
    reduceMotion: Boolean = false,
    isActive: Boolean = true,
    isTelegramConnected: Boolean = false,
    isTelegramStatusInitializing: Boolean = false,
    onConnectTelegram: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Always derive access from the OS permission rather than the previous
    // first-launch choice. A denied permission must not dismiss Home onboarding.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioPermission = remember { AudioPermission.requiredPermission() }
    fun hasAudioAccess() = ContextCompat.checkSelfPermission(context, audioPermission) ==
        PackageManager.PERMISSION_GRANTED
    var localAudioPermissionGranted by remember(context, audioPermission) {
        mutableStateOf(hasAudioAccess())
    }
    val permissionCheckRequests by AudioPermission.permissionCheckRequests.collectAsStateWithLifecycle()
    LaunchedEffect(permissionCheckRequests, isActive) {
        if (isActive) localAudioPermissionGranted = hasAudioAccess()
    }
    // Also catch changes in Android Settings when the Activity returns to foreground.
    DisposableEffect(lifecycleOwner, context, audioPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                localAudioPermissionGranted = hasAudioAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val showInitialSourceChoice = shouldShowInitialSourceChoice(
        telegramConnected = isTelegramConnected,
        localAudioPermissionGranted = localAudioPermissionGranted,
    )
    val telegramReminderDismissed by viewModel.telegramConnectReminderDismissed
        .collectAsStateWithLifecycle(initialValue = true)
    var dismissedInSession by rememberSaveable { mutableStateOf(false) }
    val showTelegramConnectReminder = shouldShowTelegramConnectReminder(
        isReady = state.isReady,
        isConnected = isTelegramConnected,
        isInitializing = isTelegramStatusInitializing,
        isDismissed = telegramReminderDismissed || dismissedInSession,
        isFirstLaunchChoiceVisible = showInitialSourceChoice,
    )
    val refreshContentAlpha by animateFloatAsState(
        targetValue = if (state.isRefreshing) 0.88f else 1f,
        animationSpec = tween(150),
        label = "home-refresh-content-fade",
    )
    val listState = rememberLazyListState()
    // Keep the Home header's blur input separate from NavHost/dock and card haze sources.
    val headerHazeState = rememberHazeState()
    // The frosted header + reminder are fixed siblings above the scrollable feed.
    // Measure their combined height so the feed starts below them at scroll position 0.
    var pinnedChromeHeightPx by remember(showTelegramConnectReminder) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val feedTopPadding = if (showTelegramConnectReminder) {
        if (pinnedChromeHeightPx > 0) {
            val chromeHeightBelowStatusBar = with(density) {
                (pinnedChromeHeightPx - WindowInsets.statusBars.getTop(this))
                    .coerceAtLeast(0).toDp()
            }
            maxOf(100.dp, chromeHeightBelowStatusBar + 14.dp)
        } else 198.dp // Initial layout only; onSizeChanged supplies the measured value.
    } else 100.dp
    HomeScrollToTopEffect(listState, scrollToTopRequest)

    if (isActive) LifecycleStartEffect(viewModel, foregroundSessionId) {
        viewModel.onHomeVisible(foregroundSessionId)
        onStopOrDispose { viewModel.onHomeHidden() }
    }

    Box(Modifier.fillMaxSize()) {
        // Capture the ambient backdrop and the scrolling feed as one real blur source.
        // The header is a sibling, never a descendant of this captured layer.
        Box(Modifier.fillMaxSize().hazeSource(headerHazeState)) {
            SecondaryAmbientBackdrop(variation = 0)
    
            if (showInitialSourceChoice) {
                FirstLaunchSourcePicker(
                    hazeState = hazeState,
                    onConnectTelegram = onConnectTelegram,
                    onOpenLibrary = onOpenLibrary,
                )
            } else {
            GlassLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .graphicsLayer { alpha = refreshContentAlpha }
                    .hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = feedTopPadding,
                    bottom = dockAwareBottomPadding(198.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                currentTrack?.let { track ->
                    item("continue") {
                        ContinueGlassHero(
                            hazeState = hazeState,
                            track = track,
                            onClick = { viewModel.playFromHome(track.id) },
                        )
                    }
                }
    
                if (state.recommended.isNotEmpty()) {
                    item("recommended") {
                        val recommendationIds = state.recommended.map(Track::id)
                        Crossfade(
                            targetState = recommendationIds,
                            modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                            animationSpec = tween(durationMillis = if (reduceMotion) 0 else 420),
                            label = "homeRecommendations",
                        ) { visibleIds ->
                            // Artwork/title updates recompose in place; only a new selection crossfades.
                            val previousTracks = remember(visibleIds) { state.recommended }
                            FeaturedRecommendations(
                                hazeState = hazeState,
                                tracks = if (visibleIds == recommendationIds) state.recommended else previousTracks,
                                onPlay = { track -> viewModel.playFromHome(track.id) },
                            )
                        }
                    }
                }
    
                if (state.recent.isNotEmpty()) {
                    item("recent") {
                        Box(if (reduceMotion) Modifier else Modifier.animateItem()) {
                            RecentGlassList(
                                hazeState = hazeState,
                                tracks = state.recent,
                                reduceMotion = reduceMotion,
                                onPlay = { track -> viewModel.playFromHome(track.id) },
                            )
                        }
                    }
                }
    
                if (state.artists.isNotEmpty()) {
                    item("artists") {
                        Crossfade(
                            targetState = state.artists,
                            modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                            animationSpec = tween(durationMillis = if (reduceMotion) 0 else 420),
                            label = "homeArtists",
                        ) { entries ->
                            ArtistOrbit(
                                hazeState = hazeState,
                                artists = entries,
                                onPlayArtist = { artist ->
                                    viewModel.playArtist(artist.normalizedName, onPlayCollection)
                                },
                            )
                        }
                    }
                }
    
                if (state.playlists.isNotEmpty()) {
                    item("playlists") {
                        Crossfade(
                            targetState = state.playlists,
                            modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                            animationSpec = tween(durationMillis = if (reduceMotion) 0 else 420),
                            label = "homePlaylists",
                        ) { entries ->
                            PlaylistGlassRail(
                                hazeState = hazeState,
                                playlists = entries,
                                onOpenPlaylist = onOpenPlaylist,
                            )
                        }
                    }
                }
    
                if (state.recentlyAdded.isNotEmpty()) {
                    item("recently-added") {
                        Box(if (reduceMotion) Modifier else Modifier.animateItem()) {
                            RecentlyAddedGlassPanel(
                                hazeState = hazeState,
                                tracks = state.recentlyAdded,
                                reduceMotion = reduceMotion,
                                onPlay = { track -> viewModel.playFromHome(track.id) },
                                onOpenLibrary = onOpenLibrary,
                            )
                        }
                    }
                } else if (state.isReady) {
                    item("empty") {
                        EmptyLibraryGlassCard(hazeState, onOpenLibrary)
                    }
                }
            }
            }
        }

        // Draw after the feed: both the header and the banner remain in place
        // while music cards move beneath their frosted glass surfaces.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(1f)
                .onSizeChanged { pinnedChromeHeightPx = it.height },
        ) {
            MeowzixStickyHeader(
                title = "Home",
                subtitle = if (showInitialSourceChoice) "Choose where your music comes from." else "Your listening, shaped for this moment.",
                hazeState = headerHazeState,
                tintAlpha = 0.28f,
            )
            if (showTelegramConnectReminder) {
                HomeTelegramConnectBanner(
                    hazeState = headerHazeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 4.dp),
                    onConnect = onConnectTelegram,
                    onDismiss = {
                        dismissedInSession = true
                        viewModel.dismissTelegramConnectReminder()
                    },
                )
            }
        }
    }
}


internal fun shouldShowInitialSourceChoice(
    telegramConnected: Boolean,
    localAudioPermissionGranted: Boolean,
): Boolean = !telegramConnected && !localAudioPermissionGranted

/** Keep the Telegram reminder anchored immediately below the Home header. */
internal fun shouldShowTelegramConnectReminder(
    isReady: Boolean,
    isConnected: Boolean,
    isInitializing: Boolean,
    isDismissed: Boolean,
    isFirstLaunchChoiceVisible: Boolean,
): Boolean = isReady && !isConnected && !isInitializing && !isDismissed && !isFirstLaunchChoiceVisible

@Composable
private fun HomeTelegramConnectBanner(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val telegramAccent = Color(0xFF2AABEE)
    GlassSurface(
        hazeState = hazeState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = "Connect Telegram", onClick = onConnect)
            .testTag("home-telegram-reminder"),
        shape = shape,
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.83f),
        tint = telegramAccent.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = telegramAccent.copy(alpha = 0.14f),
                contentColor = telegramAccent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_telegram_plane),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connect Telegram",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Bring music from your chats into Meowzix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(44.dp).testTag("home-telegram-reminder-dismiss"),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss Telegram reminder")
            }
        }
    }
}

@Composable
private fun FirstLaunchSourcePicker(
    hazeState: HazeState,
    onConnectTelegram: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    // The dock is 72dp tall and anchored 10dp above the navigation inset.
    // Reserve only the dock + a small visual gap, not the scroll-feed's 198dp.
    val bottomDockClearance = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding() + 94.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 100.dp, bottom = bottomDockClearance)
            .testTag("first-launch-source-choice"),
    ) {
        val compactHeight = maxHeight < 520.dp
        val veryCompactHeight = maxHeight < 380.dp
        val iconSize = when {
            veryCompactHeight -> 40.dp
            compactHeight -> 48.dp
            else -> 72.dp
        }
        val telegramAccent = Color(0xFF2AABEE)
        val libraryAccent = Color(0xFFC47C5C)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FirstLaunchSourceCard(
                hazeState = hazeState,
                title = "Telegram",
                description = "Access music from Saved Messages and your selected chats.",
                buttonLabel = "Connect Telegram",
                testTagPrefix = "first-launch-telegram",
                modifier = Modifier.weight(1f).fillMaxWidth(),
                accent = telegramAccent,
                iconSize = iconSize,
                compact = compactHeight,
                veryCompact = veryCompactHeight,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_telegram_plane),
                        contentDescription = null,
                        tint = telegramAccent,
                        modifier = Modifier.size(if (compactHeight) 28.dp else 34.dp),
                    )
                },
                onClick = onConnectTelegram,
            )
            FirstLaunchSourceCard(
                hazeState = hazeState,
                title = "Library",
                description = "Play audio files stored on your device. No Telegram connection needed.",
                buttonLabel = "Open Library",
                testTagPrefix = "first-launch-library",
                modifier = Modifier.weight(1f).fillMaxWidth(),
                accent = libraryAccent,
                iconSize = iconSize,
                compact = compactHeight,
                veryCompact = veryCompactHeight,
                icon = {
                    Icon(
                        Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = libraryAccent,
                        modifier = Modifier.size(if (compactHeight) 28.dp else 34.dp),
                    )
                },
                onClick = onOpenLibrary,
            )
        }
    }
}

@Composable
private fun FirstLaunchSourceCard(
    hazeState: HazeState,
    title: String,
    description: String,
    buttonLabel: String,
    testTagPrefix: String,
    modifier: Modifier,
    accent: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    veryCompact: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    GlassSurface(
        hazeState = hazeState,
        modifier = modifier
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
            .testTag("$testTagPrefix-card"),
        shape = shape,
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
        tint = accent.copy(alpha = 0.10f),
    ) {
        Box(
            Modifier.matchParentSize().background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(if (veryCompact) 9.dp else if (compact) 12.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.size(iconSize),
                    shape = RoundedCornerShape(20.dp),
                    color = accent.copy(alpha = 0.12f),
                    contentColor = accent,
                ) {
                    Box(contentAlignment = Alignment.Center) { icon() }
                }
                Text(
                    text = title,
                    style = if (veryCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp),
                )
                Text(
                    text = description,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = if (veryCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 8.dp),
                )
            }
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(if (veryCompact) 40.dp else if (compact) 44.dp else 50.dp)
                    .testTag("$testTagPrefix-button"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.9f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            ) {
                Text(buttonLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Tab re-selection is a distinct event from route navigation. */
@Composable
internal fun HomeScrollToTopEffect(listState: LazyListState, request: Int) {
    LaunchedEffect(listState, request) {
        if (request > 0) listState.animateScrollToItem(0)
    }
}

/**
 * Keeps the quiet abstract atmosphere from 0.4, with a little more depth behind glass.
 * These shapes are deliberately non-interactive and low-contrast so the music remains primary.
 */
@Composable
private fun HomeAmbientBackdrop() {
    // Rasterize the existing four blurred shapes into one isolated hardware layer.
    // The original colors, radii, shapes, and positions are intentionally unchanged.
    // Compose can reuse the layer while the Home feed scrolls; theme/size changes invalidate it.
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        Box(
            Modifier
                .offset(x = (-92).dp, y = 84.dp)
                .size(300.dp)
                .blur(92.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 108.dp, y = 210.dp)
                .size(286.dp)
                .blur(96.dp)
                .background(
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-128).dp, y = 210.dp)
                .width(360.dp)
                .height(118.dp)
                .rotate(-18f)
                .blur(74.dp)
                .background(
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.07f),
                    RoundedCornerShape(90.dp),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 88.dp, y = 72.dp)
                .size(250.dp)
                .blur(90.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun ContinueGlassHero(
    hazeState: HazeState,
    track: Track,
    onClick: () -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(34.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track.artworkRef, track.title, 92.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "CONTINUE LISTENING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = track.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                }
            }
        }
    }
}

@Composable
private fun FeaturedRecommendations(
    hazeState: HazeState,
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
) {
    Column {
        HomeSectionHeading(
            title = "Made for you",
            subtitle = "Picked from your listening",
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
        )
        LazyRow(
            contentPadding = PaddingValues(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = { it.id.toString() }) { track ->
                GlassSurface(
                    hazeState = hazeState,
                    modifier = Modifier
                        .width(172.dp)
                        .clickable { onPlay(track) },
                    shape = RoundedCornerShape(28.dp),
                    fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ) {
                    Column(Modifier.padding(11.dp)) {
                        TrackArtwork(track.artworkRef, track.title, 150.dp)
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(
                            text = track.artist ?: "Unknown artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentGlassList(
    hazeState: HazeState,
    tracks: List<Track>,
    reduceMotion: Boolean,
    onPlay: (Track) -> Unit,
) {
    Column {
        HomeSectionHeading(
            title = "Recently played",
            subtitle = "Jump back in",
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
        )
        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(30.dp),
            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        ) {
            LookaheadScope {
            Column(Modifier.padding(vertical = 5.dp)) {
                tracks.take(5).forEach { track ->
                    key(track.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (reduceMotion) Modifier else Modifier.animateBounds(this@LookaheadScope))
                            .clickable { onPlay(track) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork(track.artworkRef, track.title, 54.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist ?: "Unknown artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.53f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                        )
                    }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ArtistOrbit(
    hazeState: HazeState,
    artists: List<SuggestedArtist>,
    onPlayArtist: (SuggestedArtist) -> Unit,
) {
    Column {
        HomeSectionHeading(
            title = "Artists for you",
            subtitle = "Based on what you play",
        )
        LazyRow(
            contentPadding = PaddingValues(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(artists, key = { it.name }) { artist ->
                GlassSurface(
                    hazeState = hazeState,
                    modifier = Modifier
                        .width(136.dp)
                        .clickable { onPlayArtist(artist) },
                    shape = RoundedCornerShape(68.dp),
                    fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TrackArtwork(artist.artworkRef, artist.name, 78.dp)
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 9.dp),
                        )
                        Text(
                            "${artist.trackCount} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGlassRail(
    hazeState: HazeState,
    playlists: List<PlaylistSummary>,
    onOpenPlaylist: (java.util.UUID) -> Unit,
) {
    Column {
        HomeSectionHeading(title = "Playlists", subtitle = "Your collections")
        LazyRow(
            contentPadding = PaddingValues(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(playlists, key = { it.id.toString() }) { playlist ->
                GlassSurface(
                    hazeState = hazeState,
                    modifier = Modifier
                        .width(196.dp)
                        .height(126.dp)
                        .clickable { onOpenPlaylist(playlist.id) },
                    shape = RoundedCornerShape(28.dp),
                    fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                            }
                        }
                        Column {
                            Text(
                                playlist.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedGlassPanel(
    hazeState: HazeState,
    tracks: List<Track>,
    reduceMotion: Boolean,
    onPlay: (Track) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column {
        HomeSectionHeading(title = "Recently added", subtitle = "Fresh in your library")
        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(30.dp),
            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        ) {
            LookaheadScope {
            Column(Modifier.padding(vertical = 6.dp)) {
                tracks.take(4).forEach { track ->
                    key(track.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (reduceMotion) Modifier else Modifier.animateBounds(this@LookaheadScope))
                            .clickable { onPlay(track) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork(track.artworkRef, track.title, 48.dp)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist ?: "Unknown artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenLibrary)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Open library",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun EmptyLibraryGlassCard(
    hazeState: HazeState,
    onOpenLibrary: () -> Unit,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLibrary),
        shape = RoundedCornerShape(30.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Build your library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Scan local music or connect Telegram from Profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(
    title: String,
    subtitle: String,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.width(9.dp))
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
            )
        }
    }
}
