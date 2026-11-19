package dev.behradhz.meowzix.feature.nowplaying

import android.Manifest
import android.content.res.Configuration
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

/** Either vertical direction in the empty player gutters can dismiss inline Queue. */
internal fun queueGutterDragFraction(dragY: Float, dragRangePx: Float): Float =
    (1f - abs(dragY) / dragRangePx.coerceAtLeast(1f)).coerceIn(0.05f, 1f)

internal fun shouldCollapseQueueFromGutter(dragY: Float, thresholdPx: Float): Boolean =
    abs(dragY) >= thresholdPx

/** A deliberate downward pull or quick downward flick collapses the open inline queue. */
internal fun shouldCollapseInlineQueue(
    fraction: Float,
    downwardVelocityPxPerSecond: Float,
    dragRangePx: Float,
): Boolean = fraction < 0.72f || downwardVelocityPxPerSecond > dragRangePx * 2.4f

internal fun queueDragFractionAfterDelta(
    fraction: Float, dragY: Float, dragRangePx: Float,
): Float = (fraction - dragY / dragRangePx.coerceAtLeast(1f)).coerceIn(0f, 1f)

internal fun shouldExpandInlineQueue(fraction: Float, alreadyExpanded: Boolean): Boolean =
    fraction >= (if (alreadyExpanded) 0.55f else 0.38f)

/** A boundary swipe is recognized only after Lyrics has consumed all available scroll. */
internal enum class LyricsEdgeSwipeAction { NONE, OPEN_QUEUE, COLLAPSE_LYRICS }

internal class LyricsEdgeSwipeTracker(private val thresholdPx: Float) {
    private var unconsumedDragY = 0f
    private var triggered = false

    fun onUnconsumedDrag(deltaY: Float): LyricsEdgeSwipeAction {
        if (triggered || deltaY == 0f) return LyricsEdgeSwipeAction.NONE
        if (unconsumedDragY != 0f && (unconsumedDragY > 0f) != (deltaY > 0f)) {
            unconsumedDragY = 0f
        }
        unconsumedDragY += deltaY
        val action = when {
            unconsumedDragY <= -thresholdPx -> LyricsEdgeSwipeAction.OPEN_QUEUE
            unconsumedDragY >= thresholdPx -> LyricsEdgeSwipeAction.COLLAPSE_LYRICS
            else -> LyricsEdgeSwipeAction.NONE
        }
        if (action != LyricsEdgeSwipeAction.NONE) triggered = true
        return action
    }

    fun reset() {
        unconsumedDragY = 0f
        triggered = false
    }
}

/**
 * Artwork is bounded by usable landscape height and the width left after reserving
 * 300dp for seek, transport controls and the queue/lyrics panel.
 */
internal fun landscapeArtworkSize(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    minOf((height - 58.dp).coerceAtLeast(0.dp), width * 0.42f,
        (width - 320.dp).coerceAtLeast(0.dp), 430.dp)

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
    canRemoveOffline: Boolean = false,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    lyricsViewModel: NowPlayingLyricsViewModel = hiltViewModel(),
    libraryTools: LibraryToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
    val trackRemovalError by viewModel.trackRemovalError.collectAsStateWithLifecycle()
    val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val sleepTimerPreferences by viewModel.sleepTimerPreferences.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val recommendationActions: RecommendationActionsViewModel = hiltViewModel()
    // Create the Queue owner once, not during the first drag frame: acquiring
    // Hilt/database flows mid-gesture would produce an avoidable animation hitch.
    val inlineQueueViewModel: QueueViewModel = hiltViewModel()
    val panels = remember { NowPlayingPanelState() }
    val lyricsExpanded = panels.lyricsExpanded
    val inlineQueueExpanded = panels.queueExpanded

    RecommendationActionDialogs(recommendationActions)
    BackHandler(enabled = lyricsExpanded || inlineQueueExpanded) {
        panels.collapseForeground()
    }
    LaunchedEffect(lyricsExpanded) { onLyricsExpandedChange(lyricsExpanded) }
    LaunchedEffect(inlineQueueExpanded) { onInlineQueueExpandedChange(inlineQueueExpanded) }
    LaunchedEffect(state.currentTrack?.id) { panels.onTrackChanged() }
    // The first-launch flow requests RECORD_AUDIO; opening the player must not
    // trigger another unsolicited permission dialog after a denial.
    val context = LocalContext.current
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
        }
    }

    val closeOrCollapse = {
        if (!panels.collapseForeground()) onBack()
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
            originLookup = libraryTools::telegramChatOrigin,
            refreshOrigin = libraryTools::refreshTelegramChatOrigin,
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
                onQueueExpandedChange = panels::setQueueExpanded,
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
                onToggleLyrics = panels::toggleLyrics,
                onExpandLyrics = panels::expandLyrics,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onToggleFavorite = viewModel::toggleFavorite,
                onTogglePlaybackMode = viewModel::togglePlaybackMode,
                onCycleRepeatMode = viewModel::cycleRepeatMode,
                onOpenForward = viewModel::openForwardPicker,
                onOpenQueue = panels::toggleQueue,
                onGoToArtist = onGoToArtist,
                onWhyThisSong = {
                    state.currentTrack?.id?.let { trackId -> recommendationActions.why(trackId) }
                },
                onContinueVibe = {
                    state.currentTrack?.id?.let { trackId -> recommendationActions.continueVibe(trackId) }
                },
                canRemoveOffline = canRemoveOffline,
                onRemoveFromLocations = { trackId, offline, library ->
                    viewModel.removeTrackFromLocations(trackId, offline, library)
                },
                onLyricsImport = lyricsViewModel::importLrc,
                onLyricsPaste = lyricsViewModel::pastePlainLyrics,
                onLyricsDelay = lyricsViewModel::adjustDelay,
                onLyricsVersion = lyricsViewModel::selectVersion,
                onLyricsDeleteVersion = lyricsViewModel::deleteVersion,
                onLyricsShowChange = lyricsViewModel::setShowInPlayer,
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

    trackRemovalError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissTrackRemovalError,
            title = { Text("Remove song") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissTrackRemovalError) { Text("OK") }
            },
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
    canRemoveOffline: Boolean,
    onRemoveFromLocations: (UUID, Boolean, Boolean) -> Unit,
    onLyricsImport: (android.net.Uri) -> Unit,
    onLyricsPaste: (String) -> Unit,
    onLyricsDelay: (Long) -> Unit,
    onLyricsVersion: (java.util.UUID) -> Unit,
    onLyricsDeleteVersion: (java.util.UUID) -> Unit,
    onLyricsShowChange: (Boolean) -> Unit,
    onLyricsRestoreAutomatic: () -> Unit,
    onLyricSeek: (Int) -> Unit,
) {
    val track = state.currentTrack
    if (track == null) {
        EmptyNowPlaying(onBack)
        return
    }


    // Portrait's vertical hero reserves too much height in landscape. Keep one
    // artwork pager on the left and put all interactive content in a right pane.
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        val landscapeHaze = rememberHazeState()
        val settledArt = remember { mutableStateOf(track.artworkRef) }
        val landscapeBackdrop = remember { mutableStateOf<ArtworkBackdropTransition?>(null) }
        var landscapePendingSeek by remember(track.id) { mutableStateOf<Float?>(null) }
        LaunchedEffect(track.id) {
            if (landscapeBackdrop.value == null) settledArt.value = track.artworkRef
        }
        val duration = state.durationMs.coerceAtLeast(1L)
        val position = landscapePendingSeek?.toLong() ?: state.positionMs.coerceIn(0L, duration)
        val loading = state.status == PlaybackStatus.DOWNLOADING ||
            state.status == PlaybackStatus.BUFFERING ||
            state.status == PlaybackStatus.PREPARING

        Box(Modifier.fillMaxSize()) {
            AnimatedPlayerBackdrop(landscapeHaze, settledArt, landscapeBackdrop)
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                val artSize = landscapeArtworkSize(maxWidth, maxHeight)
                Column(Modifier.fillMaxSize()) {
                    PlayerHeader(
                        currentTrackId = track.id,
                        currentTrackTitle = track.title,
                        hazeState = landscapeHaze,
                        lockScreenMode = lockScreenMode,
                        lyricsExpanded = lyricsExpanded,
                        queueExpanded = queueExpanded,
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
                        canRemoveOffline = canRemoveOffline,
                        onRemoveFromLocations = if (lockScreenMode) null else { offline, library ->
                            onRemoveFromLocations(track.id, offline, library)
                        },
                        onOpenLyrics = onExpandLyrics,
                        lyricsState = lyricsState,
                        onLyricsImport = onLyricsImport,
                        onLyricsPaste = onLyricsPaste,
                        onLyricsDelay = onLyricsDelay,
                        onLyricsVersion = onLyricsVersion,
                        onLyricsDeleteVersion = onLyricsDeleteVersion,
                        onLyricsShowChange = onLyricsShowChange,
                        onLyricsRestoreAutomatic = onLyricsRestoreAutomatic,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(artSize).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NowPlayingArtworkPager(
                                state = state,
                                queueState = queueState,
                                fallbackArtworkRef = track.artworkRef,
                                fallbackArtworkDescription = track.title + " cover art",
                                onBack = onBack,
                                onSwipeUp = { onQueueExpandedChange(true) },
                                onPrevious = onPrevious,
                                onNext = onNext,
                                onBackdropTransition = { landscapeBackdrop.value = it },
                                onTransitionSettled = { settledId ->
                                    settledArt.value = queueState.items
                                        .firstOrNull { it.id == settledId }?.artworkRef
                                        ?: if (settledId == track.id) track.artworkRef else settledArt.value
                                },
                                cornerRadius = 22.dp,
                                modifier = Modifier.size(artSize),
                            )
                        }
                        GlassSurface(
                            hazeState = landscapeHaze,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(24.dp),
                            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f),
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                when {
                                    queueExpanded -> {
                                        LandscapePlayerPanelHeading(
                                            title = "Up next",
                                            subtitle = queueState.items.drop(
                                                (queueState.currentIndex + 1).coerceAtLeast(0)
                                            ).size.toString() + " upcoming",
                                            onDone = { onQueueExpandedChange(false) },
                                        )
                                        PlayerInlineQueue(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            onGoToArtist = onGoToArtist,
                                            viewModel = inlineQueueViewModel,
                                        )
                                        LandscapePlayerMiniTransport(
                                            state, onPrevious, onTogglePlayPause, onNext,
                                        )
                                    }
                                    lyricsExpanded -> {
                                        LandscapePlayerPanelHeading(
                                            title = "Lyrics",
                                            subtitle = track.title,
                                            onDone = onToggleLyrics,
                                        )
                                        ExpandedLyricsPanel(
                                            state = lyricsState,
                                            onLineSeek = onLyricSeek,
                                            onImportLrc = onLyricsImport,
                                            onPaste = onLyricsPaste,
                                            onAdjustDelay = onLyricsDelay,
                                            onSelectVersion = onLyricsVersion,
                                            reduceMotion = reduceMotion,
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                        )
                                        LandscapePlayerMiniTransport(
                                            state, onPrevious, onTogglePlayPause, onNext,
                                        )
                                    }
                                    else -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    track.title,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                val artist = track.artist?.takeIf(String::isNotBlank)
                                                Text(
                                                    artist ?: "Unknown artist",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.then(
                                                        if (artist != null) Modifier.clickable {
                                                            onGoToArtist(artist)
                                                        } else Modifier,
                                                    ),
                                                )
                                            }
                                            IconButton(onClick = onToggleFavorite) {
                                                Icon(
                                                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                    contentDescription = if (isFavorite) "Remove from favorites"
                                                        else "Add to favorites",
                                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            TextButton(onClick = onExpandLyrics) { Text("Lyrics") }
                                        }
                                        Spacer(Modifier.weight(1f))
                                        state.errorMessage?.let { message ->
                                            Text(
                                                message,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                formatDuration(position),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Box(Modifier.weight(1f)) {
                                                val finishSeek: () -> Unit = {
                                                    landscapePendingSeek?.let { onSeek(it.toLong()) }
                                                    landscapePendingSeek = null
                                                }
                                                if (seekBarStyle == PlayerSeekBarStyle.WAVEFORM) {
                                                    SyntheticWaveformSeekBar(
                                                        trackKey = track.id.toString(),
                                                        liveBands = liveBands,
                                                        value = position.toFloat(),
                                                        onValueChange = { landscapePendingSeek = it },
                                                        onValueChangeFinished = finishSeek,
                                                        valueRange = 0f..duration.toFloat(),
                                                        enabled = state.durationMs > 0L && !loading,
                                                        isPlaying = state.status == PlaybackStatus.PLAYING,
                                                        isLoading = loading,
                                                        activeBarColor = MaterialTheme.colorScheme.primary,
                                                        inactiveBarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                                                        pointerColor = MaterialTheme.colorScheme.onSurface,
                                                        loadingShimmerColor = MaterialTheme.colorScheme.tertiary,
                                                        reduceMotion = reduceMotion,
                                                        accessibilityLabel = "Playback position",
                                                        accessibilityValue = formatDuration(position) + " of " + formatDuration(duration),
                                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                                    )
                                                } else {
                                                    CurlyMusicSlider(
                                                        value = position.toFloat().coerceIn(0f, duration.toFloat()),
                                                        onValueChange = { landscapePendingSeek = it },
                                                        onValueChangeFinished = finishSeek,
                                                        valueRange = 0f..duration.toFloat(),
                                                        enabled = state.durationMs > 0L && !loading,
                                                        isPlaying = state.status == PlaybackStatus.PLAYING && !reduceMotion,
                                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                                            .semantics { contentDescription = "Playback position" },
                                                    )
                                                }
                                            }
                                            Text(
                                                "-" + formatDuration((duration - position).coerceAtLeast(0L)),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        PlaybackControls(
                                            state = state,
                                            onPrevious = onPrevious,
                                            onNext = onNext,
                                            onTogglePlayPause = onTogglePlayPause,
                                            onTogglePlaybackMode = onTogglePlaybackMode,
                                            onCycleRepeatMode = onCycleRepeatMode,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
    val currentTrackHasLyrics = lyricsState.trackId == currentTrackId &&
        lyricsState.hasDisplayableLyrics && lyricsState.trackLyrics?.showInPlayer != false
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
    var playerActionsMenuOpen by remember { mutableStateOf(false) }
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

        // One gesture recognizer is shared by header, artwork and playback controls.
        // Do not attach it to the inline Queue's LazyColumn: the queue must scroll
        // and reorder independently of the player's expand/collapse gesture.
        val density = LocalDensity.current
        val dragRangePx = with(density) { 260.dp.toPx() }
        val lyricsSwipeThresholdPx = with(density) { 72.dp.toPx() }
        var lyricsOuterDragY by remember { mutableFloatStateOf(0f) }
        val applyQueueDrag: (Float) -> Unit = { dragY ->
            if (!queueDragActive) {
                queueDragFraction = if (queueExpanded) 1f else 0f
                queueDragActive = true
            }
            queueDragFraction = queueDragFractionAfterDelta(queueDragFraction, dragY, dragRangePx)
        }
        val settleQueueDrag: () -> Unit = {
            if (queueDragActive) {
                val expand = shouldExpandInlineQueue(queueDragFraction, queueExpanded)
                queueDragActive = false
                onQueueExpandedChange(expand)
            }
        }
        val artworkVerticalDragCallbacks = ArtworkVerticalDragCallbacks(
            onDelta = applyQueueDrag,
            onEnd = { action ->
                when {
                    action == ArtworkVerticalSwipeAction.CLOSE_PLAYER && !queueExpanded -> {
                        queueDragActive = false
                        onBack()
                    }
                    action == ArtworkVerticalSwipeAction.OPEN_QUEUE && !queueExpanded -> {
                        queueDragActive = false
                        onQueueExpandedChange(true)
                    }
                    else -> settleQueueDrag()
                }
            },
            onCancel = { queueDragActive = false },
        )
        // Only the portrait player's empty outer gutters own this gesture.
        // A swipe in either direction closes the open Queue; the actual list
        // remains free to scroll and reorder independently.
        var queueGutterDragY by remember { mutableFloatStateOf(0f) }
        val queueGutterGesture = Modifier.pointerInput(queueExpanded, playerActionsMenuOpen) {
            if (queueExpanded && !playerActionsMenuOpen) detectVerticalDragGestures(
                onDragStart = {
                    queueGutterDragY = 0f
                    queueDragFraction = 1f
                    queueDragActive = false
                },
                onVerticalDrag = { change, dragY ->
                    change.consume()
                    queueGutterDragY += dragY
                    queueDragActive = true
                    queueDragFraction = queueGutterDragFraction(queueGutterDragY, dragRangePx)
                },
                onDragEnd = {
                    val shouldClose = shouldCollapseQueueFromGutter(queueGutterDragY, lyricsSwipeThresholdPx)
                    queueDragActive = false
                    onQueueExpandedChange(!shouldClose)
                    queueGutterDragY = 0f
                },
                onDragCancel = {
                    queueDragActive = false
                    queueGutterDragY = 0f
                },
            )
        }
        val queueDragGesture = Modifier.pointerInput(queueExpanded, lyricsExpanded, playerActionsMenuOpen) {
            if (!playerActionsMenuOpen) detectVerticalDragGestures(
                onDragStart = {
                    queueDragFraction = if (queueExpanded) 1f else 0f
                    queueDragActive = false
                    lyricsOuterDragY = 0f
                },
                onVerticalDrag = { change, dragY ->
                    change.consume()
                    lyricsOuterDragY += dragY
                    // When Lyrics is open, a downward pull outside its text closes
                    // Lyrics; an upward pull opens the Queue. Lyric text scrolling
                    // itself never uses this recognizer.
                    if (!lyricsExpanded || queueExpanded || lyricsOuterDragY < 0f || queueDragActive) {
                        applyQueueDrag(dragY)
                    }
                },
                onDragEnd = {
                    when {
                        lyricsExpanded && !queueExpanded && lyricsOuterDragY >= lyricsSwipeThresholdPx -> {
                            queueDragActive = false
                            onToggleLyrics()
                        }
                        lyricsExpanded && !queueExpanded && lyricsOuterDragY <= -lyricsSwipeThresholdPx -> {
                            queueDragActive = false
                            onQueueExpandedChange(true)
                        }
                        else -> settleQueueDrag()
                    }
                    lyricsOuterDragY = 0f
                },
                onDragCancel = {
                    queueDragActive = false
                    lyricsOuterDragY = 0f
                },
            )
        }

        // Let the LazyColumn scroll and reorder normally. Only its unconsumed
        // downward pull at the top is handed to the player's queue morph.
        val queuePullNestedScrollConnection = remember(
            queueExpanded, lyricsExpanded, playerActionsMenuOpen, dragRangePx,
        ) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || !queueDragActive) return Offset.Zero
                    val previous = queueDragFraction
                    queueDragFraction = (previous - available.y / dragRangePx).coerceIn(0.05f, 1f)
                    if (queueDragFraction >= 1f && available.y < 0f) queueDragActive = false
                    return Offset(0f, (previous - queueDragFraction) * dragRangePx)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput || !queueExpanded ||
                        lyricsExpanded || playerActionsMenuOpen || available.y <= 0f
                    ) return Offset.Zero

                    if (!queueDragActive) {
                        queueDragFraction = 1f
                        queueDragActive = true
                    }
                    val previous = queueDragFraction
                    // Keep the list composed until the gesture finishes and settles.
                    queueDragFraction = (previous - available.y / dragRangePx).coerceIn(0.05f, 1f)
                    return Offset(0f, (previous - queueDragFraction) * dragRangePx)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!queueDragActive) return Velocity.Zero
                    val collapse = shouldCollapseInlineQueue(
                        queueDragFraction, available.y, dragRangePx,
                    )
                    queueDragActive = false
                    onQueueExpandedChange(!collapse)
                    // The fling belongs to the sheet settle, not to the queue's list.
                    return Velocity(0f, available.y)
                }
            }
        }

        // The Lyrics LazyColumn scrolls normally. Only movement left unconsumed
        // at its top or bottom is used to navigate between player panels.
        val lyricsEdgeTracker = remember(lyricsSwipeThresholdPx) {
            LyricsEdgeSwipeTracker(lyricsSwipeThresholdPx)
        }
        val lyricsEdgeNestedScroll = remember(
            lyricsExpanded, queueExpanded, playerActionsMenuOpen, lyricsEdgeTracker,
        ) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput ||
                        !lyricsExpanded || queueExpanded || playerActionsMenuOpen
                    ) {
                        lyricsEdgeTracker.reset()
                        return Offset.Zero
                    }
                    if (available.y == 0f) {
                        if (consumed.y != 0f) lyricsEdgeTracker.reset()
                        return Offset.Zero
                    }
                    when (lyricsEdgeTracker.onUnconsumedDrag(available.y)) {
                        LyricsEdgeSwipeAction.OPEN_QUEUE -> onQueueExpandedChange(true)
                        LyricsEdgeSwipeAction.COLLAPSE_LYRICS -> onToggleLyrics()
                        LyricsEdgeSwipeAction.NONE -> Unit
                    }
                    // Do not consume lyric text scrolling or its overscroll effect.
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    lyricsEdgeTracker.reset()
                    return Velocity.Zero
                }
            }
        }

    Box(Modifier.fillMaxSize()) {
        // Gesture handling on the backdrop reaches only the empty side gutters,
        // never the Queue list or its reorder handles above this surface.
        Box(Modifier.fillMaxSize()
            .then(when {
                playerActionsMenuOpen -> Modifier
                queueExpanded -> queueGutterGesture
                else -> queueDragGesture
            })
            .testTag("player-queue-side-gutters"),
        ) {
            AnimatedPlayerBackdrop(hazeState, settledBackdropRef, backdropTransition)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Box(Modifier.fillMaxWidth().then(queueDragGesture)) {
                PlayerHeader(
                currentTrackId = track.id,
                        currentTrackTitle = track.title,
                hazeState = hazeState,
                lockScreenMode = lockScreenMode,
                lyricsExpanded = lyricsExpanded,
                queueExpanded = queueExpanded,
                sleepTimer = sleepTimer,
                sleepTimerPreferences = sleepTimerPreferences,
                onSetSleepTimerEnabled = onSetSleepTimerEnabled,
                onSetSleepTimerMinutes = onSetSleepTimerMinutes,
                onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
                onExtendSleepTimer = onExtendSleepTimer,
                onShortenSleepTimer = onShortenSleepTimer,
                onChangePlaybackSpeed = onChangePlaybackSpeed,
                onBack = onBack,
                onMoreActionsMenuExpandedChange = { expanded ->
                    playerActionsMenuOpen = expanded
                    onMoreActionsMenuExpandedChange(expanded)
                },
                onMoreActionsButtonBoundsChanged = onMoreActionsButtonBoundsChanged,
                onOpenForward = onOpenForward,
                onOpenQueue = onOpenQueue,
                playbackSpeed = state.playbackSpeed,
                onWhyThisSong = onWhyThisSong,
                onContinueVibe = onContinueVibe,
                canRemoveOffline = canRemoveOffline,
                onRemoveFromLocations = if (lockScreenMode) null else { offline, library ->
                    onRemoveFromLocations(track.id, offline, library)
                },
                onOpenLyrics = onExpandLyrics,
                lyricsState = lyricsState,
                onLyricsImport = onLyricsImport,
                onLyricsPaste = onLyricsPaste,
                onLyricsDelay = onLyricsDelay,
                onLyricsVersion = onLyricsVersion,
                onLyricsDeleteVersion = onLyricsDeleteVersion,
                onLyricsShowChange = onLyricsShowChange,
                onLyricsRestoreAutomatic = onLyricsRestoreAutomatic,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Measure the transport before the hero. A size callback can miss the
            // first frame, leaving the full-height cover to obscure the controls.
            SubcomposeLayout(modifier = Modifier.fillMaxWidth().weight(1f)) { constraints ->
                val width = constraints.maxWidth
                val footer = subcompose("transport") {
                    Column(Modifier.fillMaxWidth().then(queueDragGesture)) {
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
                }.single().measure(
                    Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight),
                )

                val heights = portraitPlayerSpace(
                    availableHeight = constraints.maxHeight,
                    footerHeight = footer.height,
                    queueProgress = queueMorph,
                    compactHeroHeight = 76.dp.roundToPx(),
                )
                val fullHeroHeightPx = heights.fullHeroHeight
                val fullHeroHeight = fullHeroHeightPx.toDp()
                val heroHeight = heights.heroHeight.toDp()
                val hero = subcompose("hero") {
                    Box(
                        Modifier.fillMaxWidth().height(heroHeight)
                            .then(if (lyricsExpanded) Modifier else queueDragGesture),
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
                            onSwipeUp = { onQueueExpandedChange(true) },
                            verticalDragCallbacks = artworkVerticalDragCallbacks,
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
                                    .nestedScroll(lyricsEdgeNestedScroll)
                                    .graphicsLayer {
                                        alpha = ((lyricsMorph - 0.18f) / 0.62f).coerceIn(0f, 1f)
                                    }
                                    .semantics { if (lyricsMorph < 0.8f) invisibleToUser() },
                            )
                        }
                        if (lyricsExpanded && !queueExpanded) {
                            // Narrow transparent gutters handle panel navigation.
                            // The center Lyrics LazyColumn keeps vertical drag ownership.
                            val gutterTop = lyricsMorphHeroHeight(fullHeroHeight, lyricsMorph) + 8.dp
                            Box(
                                Modifier.align(Alignment.CenterStart)
                                    .padding(top = gutterTop).width(28.dp).fillMaxHeight()
                                    .then(queueDragGesture).testTag("lyrics-swipe-left"),
                            )
                            Box(
                                Modifier.align(Alignment.CenterEnd)
                                    .padding(top = gutterTop).width(28.dp).fillMaxHeight()
                                    .then(queueDragGesture).testTag("lyrics-swipe-right"),
                            )
                        }
                    }
                }.single().measure(Constraints.fixed(width, heights.heroHeight))

                // Retain Queue throughout its reverse morph, including when Lyrics
                // is being restored. This also previews Queue during an upward lyric-gutter drag.
                val queue = if (queueMorph > 0.005f) {
                    subcompose("queue") {
                        Column(
                            Modifier.fillMaxWidth().graphicsLayer {
                                alpha = ((queueMorph - 0.06f) / 0.35f).coerceIn(0f, 1f)
                            },
                        ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .then(queueDragGesture)
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
                                    modifier = Modifier.fillMaxSize()
                                        .nestedScroll(queuePullNestedScrollConnection),
                                    onGoToArtist = onGoToArtist,
                                    viewModel = inlineQueueViewModel,
                                )
                            }
                    }.single().measure(Constraints.fixed(width, heights.queueHeight))
                } else null

                layout(width, constraints.maxHeight) {
                    hero.placeRelative(0, 0)
                    footer.placeRelative(0, heights.heroHeight)
                    queue?.placeRelative(0, heights.heroHeight + footer.height)
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
private fun LandscapePlayerPanelHeading(
    title: String,
    subtitle: String,
    onDone: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onDone) { Text("Done") }
    }
}

/** Keeps play/pause and navigation reachable while queue or lyrics own the right pane. */
@Composable
private fun LandscapePlayerMiniTransport(
    state: PlaybackState,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            state.currentTrack?.title.orEmpty(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onPrevious, enabled = state.canSkipPrevious) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
        }
        Surface(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                )
            }
        }
        IconButton(onClick = onNext, enabled = state.canSkipNext) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
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
    currentTrackTitle: String,
    hazeState: HazeState,
    lockScreenMode: Boolean,
    lyricsExpanded: Boolean,
    queueExpanded: Boolean,
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
    canRemoveOffline: Boolean,
    onRemoveFromLocations: ((Boolean, Boolean) -> Unit)?,
    onOpenLyrics: () -> Unit,
    lyricsState: NowPlayingLyricsState,
    onLyricsImport: (android.net.Uri) -> Unit,
    onLyricsPaste: (String) -> Unit,
    onLyricsDelay: (Long) -> Unit,
    onLyricsVersion: (UUID) -> Unit,
    onLyricsDeleteVersion: (UUID) -> Unit,
    onLyricsShowChange: (Boolean) -> Unit,
    onLyricsRestoreAutomatic: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var dragInProgress by remember { mutableStateOf(false) }
    var dragSelection by remember { mutableStateOf<Int?>(null) }
    var draggedLyricsRequest by remember { mutableIntStateOf(0) }
    var draggedSpeedRequest by remember { mutableIntStateOf(0) }
    var draggedTimerRequest by remember { mutableIntStateOf(0) }
    var draggedRemoveRequest by remember { mutableIntStateOf(0) }
    var triggerBounds by remember { mutableStateOf(Rect.Zero) }
    val actionBounds = remember { mutableStateMapOf<Int, Rect>() }
    val chatActions = LocalTrackChatLinkActions.current
    val menuActionScope = rememberCoroutineScope()
    val menuContext = LocalContext.current
    var viewInChatOrigin by remember(currentTrackId) {
        mutableStateOf<dev.behradhz.meowzix.domain.telegram.TelegramTrackOrigin?>(null)
    }
    var chatOriginLoaded by remember(currentTrackId) { mutableStateOf(false) }
    LaunchedEffect(menuExpanded, currentTrackId) {
        viewInChatOrigin = null
        chatOriginLoaded = false
        if (menuExpanded) {
            viewInChatOrigin = try {
                chatActions?.originLookup?.invoke(currentTrackId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (viewInChatOrigin != null && viewInChatOrigin?.profilePhotoRef == null) {
                val refreshed = try {
                    chatActions?.refreshOrigin?.invoke(currentTrackId)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (refreshed != null) viewInChatOrigin = refreshed
            }
            chatOriginLoaded = true
        }
    }
    fun openChatMessage() {
        menuActionScope.launch {
            val link = try {
                chatActions?.lookup?.invoke(currentTrackId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (link != null) chatActions?.open?.invoke(link)
            else Toast.makeText(menuContext, "Telegram message unavailable", Toast.LENGTH_SHORT).show()
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
                    Icon(painterResource(R.drawable.ic_telegram_plane), contentDescription = "Forward track on Telegram", tint = PlayerPrimaryContent)
                }
            }
            IconButton(onClick = onOpenQueue) {
                Icon(
                    if (queueExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.QueueMusic,
                    contentDescription = if (queueExpanded) "Collapse queue" else "Open queue",
                    tint = PlayerPrimaryContent,
                )
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
                                        6 -> { draggedRemoveRequest++ }
                                        5 -> {
                                            menuExpanded = false
                                            if (chatActions != null && viewInChatOrigin != null) openChatMessage()
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
                    trackTitle = currentTrackTitle,
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
                    onViewInChat = if (chatActions != null && viewInChatOrigin != null) ::openChatMessage else null,
                    viewInChatOrigin = viewInChatOrigin,
                    isLocalSource = chatOriginLoaded && viewInChatOrigin == null,
                    canRemoveOffline = canRemoveOffline,
                    onRemoveFromLocations = onRemoveFromLocations,
                    onOpenLyrics = onOpenLyrics,
                    onImportLrc = { importLauncher.launch(arrayOf("*/*")) },
                    onPasteLyrics = { pasteDialog = true },
                    onAdjustDelay = onLyricsDelay,
                    onSelectVersion = onLyricsVersion,
                    onDeleteVersion = onLyricsDeleteVersion,
                    onShowLyricsChange = onLyricsShowChange,
                    onRestoreAutomaticLyrics = onLyricsRestoreAutomatic,
                    dragInProgress = dragInProgress,
                    draggedAction = dragSelection,
                    draggedLyricsRequest = draggedLyricsRequest,
                    draggedSpeedRequest = draggedSpeedRequest,
                    draggedTimerRequest = draggedTimerRequest,
                    draggedRemoveRequest = draggedRemoveRequest,
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
