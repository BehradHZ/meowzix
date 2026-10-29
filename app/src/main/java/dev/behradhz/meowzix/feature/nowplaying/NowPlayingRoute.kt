package dev.behradhz.meowzix.feature.nowplaying

import android.Manifest
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
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import dev.behradhz.meowzix.domain.settings.PlayerAccentSource
import dev.behradhz.meowzix.domain.settings.PlayerSeekBarStyle
import dev.behradhz.meowzix.feature.recommendation.RecommendationActionDialogs
import dev.behradhz.meowzix.feature.recommendation.RecommendationActionsViewModel
import dev.behradhz.meowzix.feature.telegram.TelegramForwardSheet
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.behradhz.meowzix.ui.components.SleepTimerControls
import dev.behradhz.meowzix.ui.components.SyntheticWaveformSeekBar
import dev.behradhz.meowzix.ui.components.rememberArtworkAccentColor
import dev.behradhz.meowzix.ui.components.sleepTimerStatusLabel
import dev.behradhz.meowzix.ui.components.TrackArtworkBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.delay

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
    viewModel: NowPlayingViewModel = hiltViewModel(),
    lyricsViewModel: NowPlayingLyricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
    val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val recommendationActions: RecommendationActionsViewModel = hiltViewModel()
    var lyricsExpanded by remember { mutableStateOf(false) }
    var sleepTimerOpen by remember { mutableStateOf(false) }

    RecommendationActionDialogs(recommendationActions)
    BackHandler(enabled = lyricsExpanded) { lyricsExpanded = false }
    LaunchedEffect(lyricsExpanded) { onLyricsExpandedChange(lyricsExpanded) }
    LaunchedEffect(state.currentTrack?.id) { lyricsExpanded = false }
    // Android's Visualizer captures FFT from our own playback session, but requires RECORD_AUDIO.
    // Request it only when the full player is opened; mini-player never starts a permission prompt.
    val context = LocalContext.current
    val liveSpectrumPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.refreshVisualizer()
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.refreshVisualizer()
        } else {
            liveSpectrumPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val closeOrCollapse = {
        if (lyricsExpanded) lyricsExpanded = false else onBack()
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
    )
    MaterialTheme(colorScheme = effectiveColors) {
        NowPlayingScreen(
        state = state,
        queueState = queueState,
        liveBands = spectrum.bands,
        isFavorite = isFavorite,
        lyricsState = lyricsState,
        lyricsExpanded = lyricsExpanded,
        sleepTimer = sleepTimer,
        reduceMotion = appearance.reduceMotion,
        seekBarStyle = appearance.playerSeekBarStyle,
        onBack = closeOrCollapse,
        onToggleLyrics = { lyricsExpanded = !lyricsExpanded },
        onExpandLyrics = { lyricsExpanded = true },
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onToggleFavorite = viewModel::toggleFavorite,
        onTogglePlaybackMode = viewModel::togglePlaybackMode,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onOpenForward = viewModel::openForwardPicker,
        onOpenQueue = onOpenQueue,
        onGoToArtist = onGoToArtist,
        onOpenSleepTimer = { sleepTimerOpen = true },
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

    if (sleepTimerOpen) {
        AlertDialog(
            onDismissRequest = { sleepTimerOpen = false },
            title = { Text("Sleep timer") },
            text = {
                SleepTimerControls(
                    state = sleepTimer,
                    endOfTrackEnabled = state.currentTrack != null,
                    onSetMinutes = viewModel::setSleepTimerMinutes,
                    onEndOfTrack = viewModel::setSleepTimerEndOfTrack,
                    onExtend = viewModel::extendSleepTimer,
                    onCancel = viewModel::cancelSleepTimer,
                )
            },
            confirmButton = { TextButton(onClick = { sleepTimerOpen = false }) { Text("Done") } },
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
    liveBands: FloatArray,
    isFavorite: Boolean,
    lyricsState: NowPlayingLyricsState,
    lyricsExpanded: Boolean,
    sleepTimer: SleepTimerState,
    reduceMotion: Boolean,
    seekBarStyle: PlayerSeekBarStyle,
    onBack: () -> Unit,
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
    onOpenSleepTimer: () -> Unit,
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
                hazeState = hazeState,
                lyricsExpanded = lyricsExpanded,
                sleepTimer = sleepTimer,
                onBack = onBack,
                onOpenForward = onOpenForward,
                onOpenQueue = onOpenQueue,
                onOpenSleepTimer = onOpenSleepTimer,
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

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                val density = LocalDensity.current
                val fullHeroHeightPx = with(density) { maxHeight.roundToPx() }
                val compactHeroHeight = lyricsMorphHeroHeight(maxHeight, lyricsMorph)

                // The artwork, title and artist each have ONE composable instance throughout
                // expansion and collapse. Their bounds interpolate into the same 76.dp header.
                MorphingPlayerHero(
                    state = state,
                    queueState = queueState,
                    progress = lyricsMorph,
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
                    modifier = Modifier.fillMaxWidth().height(compactHeroHeight),
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
                            .padding(top = compactHeroHeight + 8.dp)
                            .graphicsLayer {
                                alpha = ((lyricsMorph - 0.18f) / 0.62f).coerceIn(0f, 1f)
                            }
                            .semantics { if (lyricsMorph < 0.8f) invisibleToUser() },
                    )
                }

            }

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
                Slider(
                    value = shownPosition.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = onDragSeek,
                    onValueChangeFinished = finishSeek,
                    valueRange = 0f..duration.toFloat(),
                    enabled = state.durationMs > 0L && !isLoading,
                    modifier = Modifier.fillMaxWidth().height(58.dp).semantics {
                        contentDescription = "Playback position"
                    },
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelMedium, color = PlayerSecondaryContent)
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
    hazeState: HazeState,
    lyricsExpanded: Boolean,
    sleepTimer: SleepTimerState,
    onBack: () -> Unit,
    onOpenForward: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
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
            IconButton(onClick = onOpenForward) {
                Icon(painterResource(R.drawable.ic_forward_paper_plane), contentDescription = "Forward track on Telegram", tint = PlayerPrimaryContent)
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Open queue", tint = PlayerPrimaryContent)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More track actions", tint = PlayerPrimaryContent)
                }
                PlayerActionsMenu(
                    expanded = menuExpanded,
                    trackId = lyricsState.trackId,
                    lyricsState = lyricsState,
                    lyricsExpanded = lyricsExpanded,
                    sleepTimerLabel = sleepTimerStatusLabel(sleepTimer),
                    onDismiss = { menuExpanded = false },
                    onOpenSleepTimer = onOpenSleepTimer,
                    onWhyThisSong = onWhyThisSong,
                    onContinueVibe = onContinueVibe,
                    onToggleLyrics = onToggleLyrics,
                    onImportLrc = { importLauncher.launch(arrayOf("*/*")) },
                    onPasteLyrics = { pasteDialog = true },
                    onAdjustDelay = onLyricsDelay,
                    onSelectVersion = onLyricsVersion,
                    onDeleteVersion = onLyricsDeleteVersion,
                    onRemoveAllLyrics = onLyricsRemoveAll,
                    onRestoreAutomaticLyrics = onLyricsRestoreAutomatic,
                )
            }
        }
    }
    if (pasteDialog) {
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
