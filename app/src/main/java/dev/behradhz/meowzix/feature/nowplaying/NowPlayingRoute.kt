package dev.behradhz.meowzix.feature.nowplaying

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.QueueActionKind
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import dev.behradhz.meowzix.domain.settings.PlayerAccentSource
import dev.behradhz.meowzix.domain.settings.PlaybackPreferenceSettings
import dev.behradhz.meowzix.domain.settings.SleepTimerPresetMode
import dev.behradhz.meowzix.domain.settings.PlayerSeekBarStyle
import dev.behradhz.meowzix.feature.recommendation.RecommendationActionDialogs
import dev.behradhz.meowzix.feature.recommendation.RecommendationActionsViewModel
import dev.behradhz.meowzix.feature.telegram.TelegramForwardSheet
import dev.behradhz.meowzix.feature.library.LibraryToolsViewModel
import dev.behradhz.meowzix.feature.library.LocalTrackChatLinkActions
import dev.behradhz.meowzix.feature.library.TrackChatLinkActions
import dev.behradhz.meowzix.feature.queue.PlayerInlineQueue
import dev.behradhz.meowzix.feature.queue.QueueViewModel
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.SyntheticWaveformSeekBar
import dev.behradhz.meowzix.ui.components.CurlyMusicSlider
import dev.behradhz.meowzix.ui.components.rememberArtworkAccentColor
import dev.behradhz.meowzix.ui.components.sleepTimerStatusLabel
import dev.behradhz.meowzix.ui.components.TrackArtworkBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val PlayerPrimaryContent: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
private val PlayerSecondaryContent: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val PlayerGlass: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val PlayerLoadingShimmer: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

@Composable
fun NowPlayingRoute(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onGoToArtist: (String) -> Unit = {},
    onLyricsExpandedChange: (Boolean) -> Unit = {},
    onMoreActionsMenuExpandedChange: (Boolean) -> Unit = {},
    onMoreActionsButtonBoundsChanged: (Rect) -> Unit = {},
    onInlineQueueExpandedChange: (Boolean) -> Unit = {},
    lockScreenMode: Boolean = false,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    lyricsViewModel: NowPlayingLyricsViewModel = hiltViewModel(),
    libraryTools: LibraryToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
    val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val sleepTimerPreferences by viewModel.sleepTimerPreferences.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val recommendationActions: RecommendationActionsViewModel = hiltViewModel()
    // Create the Queue owner once, not during the first drag frame: acquiring
    // Hilt/database flows mid-gesture would produce an avoidable animation hitch.
    val inlineQueueViewModel: QueueViewModel = hiltViewModel()
    var lyricsExpanded by remember { mutableStateOf(false) }
    var inlineQueueExpanded by remember { mutableStateOf(false) }

    RecommendationActionDialogs(recommendationActions)
    BackHandler(enabled = lyricsExpanded || inlineQueueExpanded) {
        if (inlineQueueExpanded) inlineQueueExpanded = false else lyricsExpanded = false
    }
    LaunchedEffect(lyricsExpanded) { onLyricsExpandedChange(lyricsExpanded) }
    LaunchedEffect(inlineQueueExpanded) { onInlineQueueExpandedChange(inlineQueueExpanded) }
    LaunchedEffect(state.currentTrack?.id) { lyricsExpanded = false }
    // Android's Visualizer captures FFT from our own playback session, but requires RECORD_AUDIO.
    // Request it only when the full player is opened; mini-player never starts a permission prompt.
    val context = LocalContext.current
    val liveSpectrumPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.refreshVisualizer()
    }
    // Only capture FFT while the expanded waveform player is actually started.
    // Equalizer effects stay attached to the playback session independently.
    LifecycleStartEffect(viewModel, appearance.playerSeekBarStyle, lockScreenMode) {
        viewModel.setVisualizerCaptureEnabled(!lockScreenMode && appearance.playerSeekBarStyle == PlayerSeekBarStyle.WAVEFORM)
        onStopOrDispose { viewModel.setVisualizerCaptureEnabled(false) }
    }
    LaunchedEffect(appearance.playerSeekBarStyle, lockScreenMode) {
        // Never trigger a microphone permission dialog over the secure keyguard.
        if (lockScreenMode || appearance.playerSeekBarStyle != PlayerSeekBarStyle.WAVEFORM) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.refreshVisualizer()
        } else {
            liveSpectrumPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val closeOrCollapse = {
        when {
            inlineQueueExpanded -> inlineQueueExpanded = false
            lyricsExpanded -> lyricsExpanded = false
            else -> onBack()
        }
    }

    var deletedSourceNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.queueActionFeedback.collectLatest { feedback ->
            if (feedback.kind == QueueActionKind.SOURCE_DELETED) {
                deletedSourceNotice =
                    "\"${feedback.trackTitle}\" was deleted from Telegram. Skipping in 5 seconds."
                delay(5_000L)
                deletedSourceNotice = null
            }
        }
    }

    val globalColors = MaterialTheme.colorScheme
    val extractedAccent = rememberArtworkAccentColor(
        if (appearance.playerAccentSource == PlayerAccentSource.TRACK) state.currentTrack?.artworkRef else null,
    )
    val customAccent = if (appearance.playerAccentSource == PlayerAccentSource.TRACK) extractedAccent else null
    val effectiveColors = if (customAccent == null) globalColors else globalColors.copy(
        primary = customAccent,
        tertiary = customAccent,
        primaryContainer = customAccent.copy(alpha = 0.23f).compositeOver(globalColors.surface),
        secondaryContainer = customAccent.copy(alpha = 0.13f).compositeOver(globalColors.surface),
        surface = customAccent.copy(alpha = 0.055f).compositeOver(globalColors.surface),
        // Shared Queue rows use surfaceVariant/outlineVariant; tint these too
        // so they inherit the artwork-accent palette, not the app-wide neutral.
        surfaceVariant = customAccent.copy(alpha = 0.12f).compositeOver(globalColors.surfaceVariant),
        outlineVariant = customAccent.copy(alpha = 0.18f).compositeOver(globalColors.outlineVariant),
    )
    CompositionLocalProvider(
        LocalTrackChatLinkActions provides TrackChatLinkActions(
            lookup = libraryTools::telegramMessageLink,
            open = { link ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                runCatching {
                    context.startActivity(Intent(intent).setPackage("org.telegram.messenger"))
                }.recoverCatching {
                    context.startActivity(intent)
                }.onFailure {
                    Toast.makeText(context, "Unable to open Telegram", Toast.LENGTH_LONG).show()
                }
            },
        ),
    ) {
        MaterialTheme(colorScheme = effectiveColors) {
            Box(Modifier.fillMaxSize()) {
            NowPlayingScreen(
                state = state,
                queueState = queueState,
                inlineQueueViewModel = inlineQueueViewModel,
                lockScreenMode = lockScreenMode,
                liveBands = spectrum.bands,
                isFavorite = isFavorite,
                lyricsState = lyricsState,
                lyricsExpanded = lyricsExpanded,
                queueExpanded = inlineQueueExpanded,
                onQueueExpandedChange = { expanded ->
                    inlineQueueExpanded = expanded
                    if (expanded) lyricsExpanded = false
                },
                sleepTimer = sleepTimer,
                sleepTimerPreferences = sleepTimerPreferences,
                onSetSleepTimerEnabled = viewModel::setSleepTimerEnabled,
                onSetSleepTimerMinutes = viewModel::setSleepTimerMinutes,
                onSetSleepTimerEndOfTrack = viewModel::setSleepTimerEndOfTrack,
                onExtendSleepTimer = viewModel::extendSleepTimer,
                onShortenSleepTimer = viewModel::shortenSleepTimer,
                onChangePlaybackSpeed = viewModel::setPlaybackSpeed,
                reduceMotion = appearance.reduceMotion,
                seekBarStyle = appearance.playerSeekBarStyle,
                onBack = closeOrCollapse,
                onMoreActionsMenuExpandedChange = onMoreActionsMenuExpandedChange,
                onMoreActionsButtonBoundsChanged = onMoreActionsButtonBoundsChanged,
                onToggleLyrics = {
                    lyricsExpanded = !lyricsExpanded
                    if (lyricsExpanded) inlineQueueExpanded = false
                },
                onExpandLyrics = {
                    lyricsExpanded = true
                    inlineQueueExpanded = false
                },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onToggleFavorite = viewModel::toggleFavorite,
                onTogglePlaybackMode = viewModel::togglePlaybackMode,
                onCycleRepeatMode = viewModel::cycleRepeatMode,
                onOpenForward = viewModel::openForwardPicker,
                onOpenQueue = {
                    inlineQueueExpanded = !inlineQueueExpanded
                    if (inlineQueueExpanded) lyricsExpanded = false
                },
                onGoToArtist = onGoToArtist,
                onWhyThisSong = {
                    state.currentTrack?.id?.let { trackId -> recommendationActions.why(trackId) }
                },
                onContinueVibe = {
                    state.currentTrack?.id?.let { trackId -> recommendationActions.continueVibe(trackId) }
                },
                onLyricsImport = lyricsViewModel::importLrc,
                onLyricsPaste = lyricsViewModel::pastePlainLyrics,
                onLyricsDelay = lyricsViewModel::adjustDelay,
                onLyricsVersion = lyricsViewModel::selectVersion,
                onLyricsDeleteVersion = lyricsViewModel::deleteVersion,
                onLyricsRemoveAll = lyricsViewModel::removeAllLyrics,
                onLyricsRestoreAutomatic = lyricsViewModel::restoreAutomaticLyrics,
                onLyricSeek = lyricsViewModel::seekToLine,
            )
            deletedSourceNotice?.let { message ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 116.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            }
        }
    }

    val track = forwardState.track ?: state.currentTrack
    if (forwardState.isOpen && track != null) {
        TelegramForwardSheet(
            track = track,
            query = forwardState.query,
            chats = forwardState.chats,
            folders = forwardState.folders,
            foldersLoading = forwardState.foldersLoading,
            foldersError = forwardState.foldersError,
            onReloadFolders = viewModel::reloadForwardFolders,
            successfulForwardCount = forwardState.successfulForwardCount,
            isSearching = forwardState.isSearching,
            isSending = forwardState.isSending,
            errorMessage = forwardState.errorMessage,
            defaults = forwardState.defaults,
            onQueryChange = viewModel::searchForwardChats,
            onForward = viewModel::forwardCurrentTrack,
            onDismiss = viewModel::dismissForwardPicker,
            reduceMotion = appearance.reduceMotion,
        )
    }

    lyricsState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = lyricsViewModel::clearError,
            title = { Text("Lyrics") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = lyricsViewModel::clearError) { Text("OK") } },
        )
    }
}

@Composable
private fun NowPlayingScreen(
    state: PlaybackState,
    queueState: QueueState,
    inlineQueueViewModel: QueueViewModel,
    lockScreenMode: Boolean,
    liveBands: FloatArray,
    isFavorite: Boolean,
    lyricsState: NowPlayingLyricsState,
    lyricsExpanded: Boolean,
    queueExpanded: Boolean,
    onQueueExpandedChange: (Boolean) -> Unit,
    sleepTimer: SleepTimerState,
    sleepTimerPreferences: PlaybackPreferenceSettings,
    onSetSleepTimerEnabled: (Boolean) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onExtendSleepTimer: () -> Unit,
    onShortenSleepTimer: () -> Unit,
    onChangePlaybackSpeed: (Float) -> Unit,
    reduceMotion: Boolean,
    seekBarStyle: PlayerSeekBarStyle,
    onBack: () -> Unit,
    onMoreActionsMenuExpandedChange: (Boolean) -> Unit,
    onMoreActionsButtonBoundsChanged: (Rect) -> Unit,
    onToggleLyrics: () -> Unit,
    onExpandLyrics: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlaybackMode: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpenForward: () -> Unit,
    onOpenQueue: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onWhyThisSong: () -> Unit,
    onContinueVibe: () -> Unit,
    onLyricsImport: (android.net.Uri) -> Unit,
    onLyricsPaste: (String) -> Unit,
    onLyricsDelay: (Long) -> Unit,
    onLyricsVersion: (java.util.UUID) -> Unit,
    onLyricsDeleteVersion: (java.util.UUID) -> Unit,
    onLyricsRemoveAll: () -> Unit,
    onLyricsRestoreAutomatic: () -> Unit,
    onLyricSeek: (Int) -> Unit,
) {
    val track = state.currentTrack
    if (track == null) {
        EmptyNowPlaying(onBack)
        return
    }

    val hazeState = rememberHazeState()
    var pendingSeek by remember(track.id) { mutableStateOf<Float?>(null) }
    var settlingSeekTarget by remember(track.id) { mutableStateOf<Long?>(null) }
    // Keep the actual displayed artwork until the pager completes its transition. Reading
    // track.artworkRef here would flash the destination backdrop before a button animation.
    val backdropTransition = remember { mutableStateOf<ArtworkBackdropTransition?>(null) }
    val settledBackdropRef = remember { mutableStateOf(track.artworkRef) }
    val currentTrackId = track.id
    val currentTrackHasLyrics = lyricsState.trackId == currentTrackId && lyricsState.hasDisplayableLyrics
    var settledArtworkTrackId by remember { mutableStateOf<UUID?>(null) }
    var reservedLyricsLayout by remember { mutableStateOf(false) }
    // Retain the previous cover's layout throughout the swipe/crop animation. Only after the
    // pager finishes should the new cover ease into its lyrics/no-lyrics layout.
    LaunchedEffect(currentTrackId, settledArtworkTrackId, currentTrackHasLyrics) {
        if (settledArtworkTrackId == currentTrackId) {
            reservedLyricsLayout = currentTrackHasLyrics
        }
    }
    // A single animated progress drives both the persistent artwork/identity and the lyric
    // viewport. The expanded endpoint is a genuine compact header, not a near-zero hero.
    val lyricsMorph by animateFloatAsState(
        targetValue = if (lyricsExpanded) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.90f, stiffness = 520f),
        label = "lyrics-screen-morph",
    )
    // A vertical pull on the player continuously trades unused artwork space for
    // the same Queue list used by QueueRoute. Lyrics and queue are exclusive.
    var queueDragActive by remember { mutableStateOf(false) }
    var queueDragFraction by remember { mutableFloatStateOf(0f) }
    val queueMorph by animateFloatAsState(
        targetValue = if (queueDragActive) queueDragFraction else if (queueExpanded) 1f else 0f,
        animationSpec = if (reduceMotion || queueDragActive) snap()
                        else spring(dampingRatio = 0.90f, stiffness = 520f),
        label = "player-inline-queue-morph",
    )

    val duration = state.durationMs.coerceAtLeast(1L)
    val shownPosition = pendingSeek?.toLong() ?: settlingSeekTarget ?: state.positionMs.coerceIn(0L, duration)
    LaunchedEffect(state.positionMs, settlingSeekTarget) {
        val target = settlingSeekTarget ?: return@LaunchedEffect
        if (abs(state.positionMs - target) <= 900L) settlingSeekTarget = null
    }
    LaunchedEffect(settlingSeekTarget) {
        val target = settlingSeekTarget ?: return@LaunchedEffect
        delay(650L)
        if (settlingSeekTarget == target) settlingSeekTarget = null
    }
    val isLoading =
        state.status == PlaybackStatus.DOWNLOADING ||
            state.status == PlaybackStatus.BUFFERING ||
            state.status == PlaybackStatus.PREPARING

    Box(Modifier.fillMaxSize()) {
        // Read animation progress only in this child, not in the full player composition.
        AnimatedPlayerBackdrop(hazeState, settledBackdropRef, backdropTransition)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            PlayerHeader(
                currentTrackId = track.id,
                hazeState = hazeState,
                lockScreenMode = lockScreenMode,
                lyricsExpanded = lyricsExpanded,
                sleepTimer = sleepTimer,
                sleepTimerPreferences = sleepTimerPreferences,
                onSetSleepTimerEnabled = onSetSleepTimerEnabled,
                onSetSleepTimerMinutes = onSetSleepTimerMinutes,
                onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
                onExtendSleepTimer = onExtendSleepTimer,
                onShortenSleepTimer = onShortenSleepTimer,
                onChangePlaybackSpeed = onChangePlaybackSpeed,
                onBack = onBack,
                onMoreActionsMenuExpandedChange = onMoreActionsMenuExpandedChange,
                onMoreActionsButtonBoundsChanged = onMoreActionsButtonBoundsChanged,
                onOpenForward = onOpenForward,
                onOpenQueue = onOpenQueue,
                playbackSpeed = state.playbackSpeed,
                onWhyThisSong = onWhyThisSong,
                onContinueVibe = onContinueVibe,
                onToggleLyrics = onToggleLyrics,
                lyricsState = lyricsState,
                onLyricsImport = onLyricsImport,
                onLyricsPaste = onLyricsPaste,
                onLyricsDelay = onLyricsDelay,
                onLyricsVersion = onLyricsVersion,
                onLyricsDeleteVersion = onLyricsDeleteVersion,
                onLyricsRemoveAll = onLyricsRemoveAll,
                onLyricsRestoreAutomatic = onLyricsRestoreAutomatic,
            )

            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val density = LocalDensity.current
                var footerHeightPx by remember { mutableIntStateOf(with(density) { 160.dp.roundToPx() }) }
                val fullHeroHeight = (maxHeight - with(density) { footerHeightPx.toDp() })
                    .coerceAtLeast(0.dp)
                val fullHeroHeightPx = with(density) { fullHeroHeight.roundToPx() }
                val heroHeight = lyricsMorphHeroHeight(fullHeroHeight, queueMorph)
                val dragRangePx = with(density) { 260.dp.toPx() }
                // Apply one gesture definition to hero and controls, not to the
                // Queue LazyColumn. Vertical list scrolling is then never captured.
                val queueDragGesture = Modifier.pointerInput(queueExpanded, lyricsExpanded) {
                    if (!lyricsExpanded) detectVerticalDragGestures(
                        onDragStart = {
                            queueDragFraction = if (queueExpanded) 1f else 0f
                            queueDragActive = true
                        },
                        onVerticalDrag = { change, dragY ->
                            change.consume()
                            queueDragFraction = (queueDragFraction - dragY / dragRangePx)
                                .coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val expand = queueDragFraction >=
                                (if (queueExpanded) 0.55f else 0.38f)
                            queueDragActive = false
                            onQueueExpandedChange(expand)
                        },
                        onDragCancel = { queueDragActive = false },
                    )
                }

                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().height(heroHeight).then(queueDragGesture),
                    ) {
                // The artwork, title and artist each have ONE composable instance throughout
                // expansion and collapse. Their bounds interpolate into the same 76.dp header.
                MorphingPlayerHero(
                    state = state,
                    queueState = queueState,
                    progress = maxOf(lyricsMorph, queueMorph),
                    collapsedHeightPx = fullHeroHeightPx,
                    lyricsLayoutVisible = reservedLyricsLayout,
                    onBack = onBack,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onToggleFavorite = onToggleFavorite,
                    onGoToArtist = onGoToArtist,
                    favorite = isFavorite,
                    reduceMotion = reduceMotion,
                    onBackdropTransition = { backdropTransition.value = it },
                    onArtworkSettled = { settledId ->
                        settledArtworkTrackId = settledId
                        settledBackdropRef.value = queueState.items.firstOrNull { it.id == settledId }?.artworkRef
                            ?: if (settledId == track.id) track.artworkRef else settledBackdropRef.value
                    },
                    compactLyrics = {
                        if (settledArtworkTrackId == currentTrackId && currentTrackHasLyrics) {
                            CompactLyricsPreview(
                                state = lyricsState,
                                onExpand = onExpandLyrics,
                                reduceMotion = reduceMotion,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(heroHeight),
                )

                // Keep lyrics composed until the reverse morph has finished as well.
                if (lyricsExpanded || lyricsMorph > 0.005f) {
                    ExpandedLyricsPanel(
                        state = lyricsState,
                        onLineSeek = onLyricSeek,
                        onImportLrc = onLyricsImport,
                        onPaste = onLyricsPaste,
                        onAdjustDelay = onLyricsDelay,
                        onSelectVersion = onLyricsVersion,
                        reduceMotion = reduceMotion,
                        showToolbar = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = lyricsMorphHeroHeight(fullHeroHeight, lyricsMorph) + 8.dp)
                            .graphicsLayer {
                                alpha = ((lyricsMorph - 0.18f) / 0.62f).coerceIn(0f, 1f)
                            }
                            .semantics { if (lyricsMorph < 0.8f) invisibleToUser() },
                    )
                }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .onSizeChanged { footerHeightPx = it.height }
                            .then(queueDragGesture),
                    ) {
            // v0.5 kept the identity immediately above the waveform with a compact 12.dp gap.
            Spacer(Modifier.height(12.dp))

            val onDragSeek: (Float) -> Unit = { pendingSeek = it }
            val finishSeek: () -> Unit = {
                pendingSeek?.let { value ->
                    val target = value.toLong()
                    settlingSeekTarget = target
                    onSeek(target)
                }
                pendingSeek = null
            }
            // Playback timestamps share a line with the seek bar instead of consuming
            // another row beneath it. Preserve both seek-bar styles and their gestures.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    formatDuration(shownPosition),
                    modifier = Modifier.widthIn(min = 40.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerSecondaryContent,
                    maxLines = 1,
                )
                Box(Modifier.weight(1f)) {
                    if (seekBarStyle == PlayerSeekBarStyle.WAVEFORM) {
                        SyntheticWaveformSeekBar(
                            trackKey = track.id.toString(),
                            liveBands = liveBands,
                            value = shownPosition.toFloat(),
                            onValueChange = onDragSeek,
                            onValueChangeFinished = finishSeek,
                            valueRange = 0f..duration.toFloat(),
                            enabled = state.durationMs > 0L && !isLoading,
                            isPlaying = state.status == PlaybackStatus.PLAYING,
                            isLoading = isLoading,
                            activeBarColor = MaterialTheme.colorScheme.primary,
                            inactiveBarColor = PlayerPrimaryContent.copy(alpha = 0.24f),
                            pointerColor = PlayerPrimaryContent,
                            loadingShimmerColor = PlayerLoadingShimmer,
                            reduceMotion = reduceMotion,
                            accessibilityLabel = "Playback position",
                            accessibilityValue = "${formatDuration(shownPosition)} of ${formatDuration(duration)}",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        CurlyMusicSlider(
                            value = shownPosition.toFloat().coerceIn(0f, duration.toFloat()),
                            onValueChange = onDragSeek,
                            onValueChangeFinished = finishSeek,
                            valueRange = 0f..duration.toFloat(),
                            enabled = state.durationMs > 0L && !isLoading,
                            isPlaying = state.status == PlaybackStatus.PLAYING && !reduceMotion,
                            modifier = Modifier.fillMaxWidth().height(58.dp).semantics {
                                contentDescription = "Playback position"
                            },
                        )
                    }
                }
                Text(
                    "-${formatDuration((duration - shownPosition).coerceAtLeast(0L))}",
                    modifier = Modifier.widthIn(min = 40.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerSecondaryContent,
                    maxLines = 1,
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

                    if (queueMorph > 0.005f && !lyricsExpanded) {
                        Column(
                            Modifier.fillMaxWidth().weight(1f)
                                .graphicsLayer {
                                    alpha = ((queueMorph - 0.06f) / 0.35f).coerceIn(0f, 1f)
                                },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(start = 6.dp, end = 2.dp, top = 3.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        "Up next",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${queueState.items.drop((queueState.currentIndex + 1).coerceAtLeast(0)).size} upcoming",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onQueueExpandedChange(false) }) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Collapse queue",
                                    )
                                }
                            }
                            PlayerInlineQueue(
                                modifier = Modifier.fillMaxSize(),
                                onGoToArtist = onGoToArtist,
                                viewModel = inlineQueueViewModel,
                            )
                        }
                    }
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
private fun AnimatedPlayerBackdrop(
    hazeState: HazeState,
    settledArtwork: State<String?>,
    transitionState: State<ArtworkBackdropTransition?>,
) {
    val transition = transitionState.value
    Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
        TrackArtworkBackdrop(
            artworkRef = backdropSourceArtwork(settledArtwork.value, transition),
            modifier = Modifier.fillMaxSize(),
        )
        transition?.takeIf { it.progress > 0f }?.let { change ->
            TrackArtworkBackdrop(
                artworkRef = change.toArtworkRef,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = change.progress.coerceIn(0f, 1f)
                },
            )
        }
    }
}

/** Never source the outgoing backdrop directly from the already-updated playback track. */
internal fun backdropSourceArtwork(settledRef: String?, transition: ArtworkBackdropTransition?): String? =
    transition?.fromArtworkRef ?: settledRef

@Composable
private fun PlayerHeader(
    currentTrackId: UUID,
    hazeState: HazeState,
    lockScreenMode: Boolean,
    lyricsExpanded: Boolean,
    sleepTimer: SleepTimerState,
    sleepTimerPreferences: PlaybackPreferenceSettings,
    onSetSleepTimerEnabled: (Boolean) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onExtendSleepTimer: () -> Unit,
    onShortenSleepTimer: () -> Unit,
    onChangePlaybackSpeed: (Float) -> Unit,
    onBack: () -> Unit,
    onMoreActionsMenuExpandedChange: (Boolean) -> Unit,
    onMoreActionsButtonBoundsChanged: (Rect) -> Unit,
    onOpenForward: () -> Unit,
    onOpenQueue: () -> Unit,
    playbackSpeed: Float,
    onWhyThisSong: () -> Unit,
    onContinueVibe: () -> Unit,
    onToggleLyrics: () -> Unit,
    lyricsState: NowPlayingLyricsState,
    onLyricsImport: (android.net.Uri) -> Unit,
    onLyricsPaste: (String) -> Unit,
    onLyricsDelay: (Long) -> Unit,
    onLyricsVersion: (UUID) -> Unit,
    onLyricsDeleteVersion: (UUID) -> Unit,
    onLyricsRemoveAll: () -> Unit,
    onLyricsRestoreAutomatic: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var dragInProgress by remember { mutableStateOf(false) }
    var dragSelection by remember { mutableStateOf<Int?>(null) }
    var draggedLyricsRequest by remember { mutableIntStateOf(0) }
    var draggedSpeedRequest by remember { mutableIntStateOf(0) }
    var draggedTimerRequest by remember { mutableIntStateOf(0) }
    var triggerBounds by remember { mutableStateOf(Rect.Zero) }
    val actionBounds = remember { mutableStateMapOf<Int, Rect>() }
    val chatActions = LocalTrackChatLinkActions.current
    var viewInChatLink by remember(currentTrackId) { mutableStateOf<String?>(null) }
    LaunchedEffect(menuExpanded, currentTrackId) {
        viewInChatLink = null
        if (menuExpanded) {
            viewInChatLink = try {
                chatActions?.lookup?.invoke(currentTrackId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    // The player-wide vertical swipe handler is suspended while this menu is visible.
    LaunchedEffect(menuExpanded) { onMoreActionsMenuExpandedChange(menuExpanded) }
    var pasteDialog by remember(lyricsState.trackId) { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onLyricsImport)
    }

    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(25.dp),
        fallbackColor = PlayerGlass.copy(alpha = 0.78f),
        tint = PlayerPrimaryContent.copy(alpha = 0.05f),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (lyricsExpanded) "Collapse lyrics" else "Close now playing",
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
            if (!lockScreenMode) {
                IconButton(onClick = onOpenForward) {
                    Icon(painterResource(R.drawable.ic_forward_paper_plane), contentDescription = "Forward track on Telegram", tint = PlayerPrimaryContent)
                }
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Open queue", tint = PlayerPrimaryContent)
            }
            if (!lockScreenMode) Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val topLeft = coords.positionOnScreen()
                            if (topLeft != Offset.Unspecified) {
                                val bounds = Rect(
                                    topLeft.x,
                                    topLeft.y,
                                    topLeft.x + coords.size.width.toFloat(),
                                    topLeft.y + coords.size.height.toFloat(),
                                )
                                if (triggerBounds != bounds) {
                                    triggerBounds = bounds
                                    onMoreActionsButtonBoundsChanged(bounds)
                                }
                            }
                        }
                        .pointerInput(lyricsState.trackId) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragInProgress = true
                                    dragSelection = null
                                    actionBounds.clear()
                                },
                                onVerticalDrag = { change, dragY ->
                                    // Only a downward drag opens the popup; horizontal swipes and
                                    // normal taps are still handled by the surrounding controls.
                                    if (dragY > 0f) menuExpanded = true
                                    if (menuExpanded) {
                                        dragSelection = draggedPlayerAction(
                                            Offset(
                                                triggerBounds.left + change.position.x,
                                                triggerBounds.top + change.position.y,
                                            ),
                                            actionBounds,
                                        )
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    val selected = dragSelection
                                    dragInProgress = false
                                    dragSelection = null
                                    when (selected) {
                                        0 -> { draggedSpeedRequest++ }
                                        1 -> { draggedTimerRequest++ }
                                        2 -> { menuExpanded = false; onWhyThisSong() }
                                        3 -> { menuExpanded = false; onContinueVibe() }
                                        4 -> { draggedLyricsRequest++ }
                                        5 -> {
                                            menuExpanded = false
                                            viewInChatLink?.let { link -> chatActions?.open?.invoke(link) }
                                        }
                                        // A short drag just opens the menu; user can tap to select.
                                    }
                                },
                                onDragCancel = {
                                    dragInProgress = false
                                    dragSelection = null
                                },
                            )
                        },
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More track actions", tint = PlayerPrimaryContent)
                }
                PlayerActionsMenu(
                    expanded = menuExpanded,
                    trackId = currentTrackId,
                    lyricsState = lyricsState,
                    lyricsExpanded = lyricsExpanded,
                    sleepTimerLabel = if (sleepTimer.active) sleepTimerStatusLabel(sleepTimer)
                        else "Off · Last: " + dev.behradhz.meowzix.ui.components.sleepTimerPresetLabel(
                            sleepTimerPreferences.sleepTimerLastMode,
                            sleepTimerPreferences.sleepTimerLastMinutes,
                        ),
                    sleepTimerActive = sleepTimer.active,
                    sleepTimerCanEnable = sleepTimer.active ||
                        sleepTimerPreferences.sleepTimerLastMode != SleepTimerPresetMode.END_OF_TRACK ||
                        lyricsState.trackId != null,
                    onSleepTimerEnabledChange = onSetSleepTimerEnabled,
                    sleepTimerState = sleepTimer,
                    sleepTimerLastMinutes = sleepTimerPreferences.sleepTimerLastMinutes,
                    sleepTimerLastMode = sleepTimerPreferences.sleepTimerLastMode,
                    sleepTimerEndOfTrackEnabled = lyricsState.trackId != null,
                    onSetSleepTimerMinutes = onSetSleepTimerMinutes,
                    onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
                    onExtendSleepTimer = onExtendSleepTimer,
                    onShortenSleepTimer = onShortenSleepTimer,
                    onChangePlaybackSpeed = onChangePlaybackSpeed,
                    onDismiss = { menuExpanded = false },
                    playbackSpeed = playbackSpeed,
                    onWhyThisSong = onWhyThisSong,
                    onContinueVibe = onContinueVibe,
                    onViewInChat = viewInChatLink?.let { link ->
                        { chatActions?.open?.invoke(link) }
                    },
                    onToggleLyrics = onToggleLyrics,
                    onImportLrc = { importLauncher.launch(arrayOf("*/*")) },
                    onPasteLyrics = { pasteDialog = true },
                    onAdjustDelay = onLyricsDelay,
                    onSelectVersion = onLyricsVersion,
                    onDeleteVersion = onLyricsDeleteVersion,
                    onRemoveAllLyrics = onLyricsRemoveAll,
                    onRestoreAutomaticLyrics = onLyricsRestoreAutomatic,
                    dragInProgress = dragInProgress,
                    draggedAction = dragSelection,
                    draggedLyricsRequest = draggedLyricsRequest,
                    draggedSpeedRequest = draggedSpeedRequest,
                    draggedTimerRequest = draggedTimerRequest,
                    onActionBoundsChanged = { index, bounds -> actionBounds[index] = bounds },
                )
            }
        }
    }
    if (!lockScreenMode && pasteDialog) {
        PasteLyricsDialog(
            onDismiss = { pasteDialog = false },
            onSave = { pasteDialog = false; onLyricsPaste(it) },
        )
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
            Icon(
                imageVector = if (state.playbackMode == PlaybackMode.SMART_SHUFFLE) Icons.Rounded.AutoAwesome else Icons.Rounded.Shuffle,
                contentDescription = null,
            )
        }
        IconButton(onClick = onPrevious, enabled = state.canSkipPrevious, modifier = Modifier.size(52.dp)) {
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
            contentColor = MaterialTheme.colorScheme.surface,
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
        IconButton(onClick = onNext, enabled = state.canSkipNext, modifier = Modifier.size(52.dp)) {
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
        modifier = Modifier.size(44.dp).semantics { this.contentDescription = contentDescription },
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
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
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
