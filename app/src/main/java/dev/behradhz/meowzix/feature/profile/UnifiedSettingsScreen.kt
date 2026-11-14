package dev.behradhz.meowzix.feature.profile

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import dev.behradhz.meowzix.feature.library.PlaylistGlassBackdrop
import dev.behradhz.meowzix.ui.components.MeowzixStickyHeader
import dev.behradhz.meowzix.ui.components.MeowzixHeaderAction
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import dev.behradhz.meowzix.feature.library.PlaylistGlassPanel
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.backup.BackupPreview
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.domain.settings.PlayerAccentSource
import dev.behradhz.meowzix.domain.settings.PlayerSeekBarStyle
import dev.behradhz.meowzix.domain.settings.LauncherIconColorSource
import dev.behradhz.meowzix.BuildConfig
import dev.behradhz.meowzix.domain.settings.LiveSongCardStyle
import dev.behradhz.meowzix.domain.settings.LiveSongSideAction
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import dev.behradhz.meowzix.ui.components.SleepTimerMenuContent
import dev.behradhz.meowzix.ui.components.sleepTimerStatusLabel
import dev.behradhz.meowzix.navigation.dockAwareBottomPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UnifiedSettingsScreen(
    onBack: () -> Unit,
    onOpenBrand: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenTelegram: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onApplyLauncherIcon: (LauncherIconColorSource) -> Unit = {},
    viewModel: UnifiedSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedbackRows by viewModel.activeFeedback.collectAsStateWithLifecycle()
    val telegramAuth by viewModel.telegramAuthState.collectAsStateWithLifecycle()
    val telegramSources by viewModel.telegramSourceState.collectAsStateWithLifecycle()
    val downloadSummary by viewModel.downloadSummary.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val hasCurrentTrack by viewModel.hasCurrentTrack.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeHistoryInBackup by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<ByteArray, BackupPreview>?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var pendingIconSource by remember { mutableStateOf<LauncherIconColorSource?>(null) }
    var versionTaps by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = selectedCategory != null) { selectedCategory = null }
    val telegramConnected = telegramAuth.step is TelegramAuthStep.Ready

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = viewModel.exportBackup(includeHistoryInBackup)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("Unable to open backup destination")
                }
            }.onSuccess { Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Backup failed", Toast.LENGTH_LONG).show() }
        }
    }

    val liveCardPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setLiveSongCardEnabled(true)
        else Toast.makeText(context, "Allow notifications to show the live song card", Toast.LENGTH_LONG).show()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(32 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= dev.behradhz.meowzix.data.backup.BackupFormat.MAX_BYTES) { "Backup is too large" }
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    } ?: error("Unable to read backup")
                }
                bytes to viewModel.previewBackup(bytes)
            }.onSuccess { pendingRestore = it }
                .onFailure { Toast.makeText(context, it.message ?: "Invalid backup", Toast.LENGTH_LONG).show() }
        }
    }

    var pendingDanger by remember { mutableStateOf<SettingsDangerAction?>(null) }

    PlaylistGlassBackdrop(variation = 2) { hazeState ->
        Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSettingsCompactMode provides true) {
        AnimatedContent(
            targetState = selectedCategory,
            transitionSpec = {
                if (state.appearance.reduceMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    // Match the main Settings push/pop: open from right and exit
                    // left, then reverse the direction for Back.
                    if (targetState != null) {
                        slideInHorizontally(tween(300), initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(tween(300), targetOffsetX = { -it })
                    } else {
                        slideInHorizontally(tween(300), initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(tween(300), targetOffsetX = { it })
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "settings-category-transition",
        ) { page ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().hazeSource(hazeState),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 100.dp, bottom = dockAwareBottomPadding(196.dp)),
            verticalArrangement = Arrangement.spacedBy(25.dp),
        ) {
            if (page == null) {
                item(key = "categories") {
                    SettingsCategoryIndex(hazeState) { category -> selectedCategory = category }
                }
                item(key = "settings-version") {
                    Text(
                        text = "Version " + BuildConfig.VERSION_NAME +
                            if (state.appearance.spermerModeEnabled) " · Spermer" else "",
                        modifier = Modifier.fillMaxWidth()
                            .testTag("settings-version")
                            .clickable {
                                versionTaps++
                                val remaining = 7 - versionTaps
                                if (remaining <= 0) {
                                    val enabled = !state.appearance.spermerModeEnabled
                                    viewModel.setSpermerModeEnabled(enabled)
                                    Toast.makeText(
                                        context,
                                        if (enabled) "Spermer mode enabled" else "Spermer mode disabled",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    versionTaps = 0
                                } else if (remaining <= 3) {
                                    Toast.makeText(
                                        context,
                                        "$remaining taps to " +
                                            if (state.appearance.spermerModeEnabled) "disable" else "unlock",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (page == SettingsCategory.APPEARANCE) {
                item(key = "appearance") {
                    SettingsGlassSection(
                        hazeState, "Appearance", "Color and motion",
                        Icons.Rounded.Palette, showHeading = false,
                    ) {
                        SettingsChoiceRow(
                            "Theme", null, Icons.Rounded.DarkMode,
                            state.appearance.theme.name.lowercase().replaceFirstChar(Char::uppercase),
                            ThemePreference.entries.map { it.name },
                        ) { viewModel.setTheme(ThemePreference.valueOf(it)) }
                        SettingsRowDivider()
                        SettingsColorPicker(
                            state.appearance, viewModel::setColorTheme, viewModel::setCustomColorSeed,
                        )
                        SettingsRowDivider()
                        LauncherIconPreviews(state.appearance) { source ->
                            pendingIconSource = source
                        }
                        SettingsRowDivider()
                        SettingsToggleRow(
                            "Reduced motion",
                            "Shorter, more restrained lyric and UI animations",
                            Icons.Rounded.Animation,
                            state.appearance.reduceMotion,
                            viewModel::setReduceMotion,
                        )
                    }
                }
                item(key = "appearance-full-player") {
                    SettingsGlassSection(
                        hazeState,
                        "Full player",
                        "Appearance of the expanded now-playing screen",
                        Icons.Rounded.PlayCircle,
                    ) {
                        SettingsPlayerPreview(state.appearance)
                        SettingsRowDivider()
                        SettingsChoiceRow(
                            "Accent",
                            "Choose the color used for the full-screen player",
                            Icons.Rounded.Palette,
                            if (state.appearance.playerAccentSource == PlayerAccentSource.TRACK)
                                "Track artwork" else "Global accent",
                            listOf("Global accent", "Track artwork"),
                            centerOptions = true,
                        ) {
                            viewModel.setPlayerAccentSource(
                                if (it == "Track artwork") PlayerAccentSource.TRACK
                                else PlayerAccentSource.GLOBAL,
                            )
                        }
                        SettingsRowDivider()
                        SettingsSliderRow(
                            "Lyrics text size",
                            "Changes the lyrics displayed in the full-screen player",
                            Icons.Rounded.TextFields,
                            "${state.appearance.lyricsTextScalePercent}%",
                            state.appearance.lyricsTextScalePercent.toFloat(),
                            80f..160f, 7,
                            { viewModel.setLyricsScale(it.toInt()) },
                            showSubtitle = true,
                            subtitleMaxLines = 2,
                        )
                        if (state.appearance.spermerModeEnabled) {
                            SettingsRowDivider()
                            val previewPlayback by viewModel.seekBarPreviewPlayback.collectAsStateWithLifecycle()
                            SettingsSeekBarStylePicker(
                                selected = state.appearance.playerSeekBarStyle,
                                onSelect = viewModel::setPlayerSeekBarStyle,
                                playback = previewPlayback,
                                spectrumFlow = viewModel.seekBarPreviewSpectrum,
                                reduceMotion = state.appearance.reduceMotion,
                                onPreviewCaptureChange = viewModel::setSettingsPreviewCaptureEnabled,
                            )
                        }
                    }
                }
            }
            if (page == SettingsCategory.PLAYBACK) {
                item(key = "playback") {
                    val previewPlayback by viewModel.seekBarPreviewPlayback.collectAsStateWithLifecycle()
                    SettingsGlassSection(hazeState, "Playback", "", Icons.Rounded.PlayCircle, showHeading = false) {
                        SettingsPlaybackModeSelector(
                            selected = state.playback.defaultMode,
                            onSelect = viewModel::setDefaultMode,
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            "Artist-balanced Shuffle",
                            "Spread playback across artists. Turn off for uniform song-level random.",
                            Icons.Rounded.Shuffle,
                            state.playback.artistBalancedShuffle,
                            viewModel::setArtistBalancedShuffle,
                            showSubtitle = true,
                            subtitleMaxLines = 3,
                        )
                        if (state.playback.artistBalancedShuffle) {
                            SettingsSliderRow(
                                "Artist balance",
                                "Lower values reduce artists' library-size advantage. Applies to new queues.",
                                Icons.Rounded.Shuffle,
                                String.format(java.util.Locale.US, "%.2f", state.playback.artistShuffleAlpha),
                                state.playback.artistShuffleAlpha,
                                0.7f..1f, 5, { viewModel.setArtistShuffleAlpha(it) },
                                showSubtitle = true,
                                subtitleMaxLines = 3,
                            )
                        }
                        SettingsRowDivider()
                        SettingsRepeatCycleRow(
                            selected = state.playback.defaultRepeat,
                            onSelect = viewModel::setDefaultRepeat,
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            "Resume playback state",
                            "Restore your last queue and playback position.",
                            Icons.Rounded.Restore,
                            state.playback.resumeOnLaunch,
                            viewModel::setResume,
                            showSubtitle = true,
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            "Resume after other apps",
                            "Resume after another app finishes, unless you paused.",
                            Icons.Rounded.PlayCircle,
                            state.playback.resumeAfterExternalAudio,
                            viewModel::setResumeAfterExternalAudio,
                            showSubtitle = true,
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            "Pause when microphone starts",
                            "Pause for microphone use and resume when it ends.",
                            Icons.Rounded.Mic,
                            state.playback.pauseOnMicrophoneRecording,
                            viewModel::setPauseOnMicrophoneRecording,
                            showSubtitle = true,
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            "Live song card",
                            "Show the current song on the lock screen and supported AODs.",
                            Icons.Rounded.MusicNote,
                            state.playback.liveSongCardEnabled,
                            onChecked = { enabled ->
                                when {
                                    !enabled -> viewModel.setLiveSongCardEnabled(false)
                                    Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                                        liveCardPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else -> viewModel.setLiveSongCardEnabled(true)
                                }
                            },
                            showSubtitle = true,
                        )
                        AnimatedVisibility(
                            visible = state.playback.liveSongCardEnabled,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(190)),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(140)),
                        ) {
                            Column {
                            SettingsRowDivider()
                            SettingsLiveUpdateLayoutPicker(
                                selected = state.playback.liveSongCardStyle,
                                onSelect = viewModel::setLiveSongCardStyle,
                            )
                            SettingsLiveUpdatePreview(
                                playback = previewPlayback,
                                style = state.playback.liveSongCardStyle,
                                leftAction = state.playback.liveSongLeftAction,
                                rightAction = state.playback.liveSongRightAction,
                            )
                            AnimatedVisibility(
                                visible = state.playback.liveSongCardStyle == LiveSongCardStyle.CONTROLS,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(190)),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(140)),
                            ) {
                                Column {
                                SettingsRowDivider()
                                SettingsChoiceRow(
                                    "Left Live Update button",
                                    "The middle button is always Pause",
                                    livePreviewActionIcon(state.playback.liveSongLeftAction),
                                    state.playback.liveSongLeftAction.name,
                                    LiveSongSideAction.entries.map { it.name },
                                    optionIcon = { livePreviewActionIcon(LiveSongSideAction.valueOf(it)) },
                                ) { viewModel.setLiveSongLeftAction(LiveSongSideAction.valueOf(it)) }
                                SettingsRowDivider()
                                SettingsChoiceRow(
                                    "Right Live Update button",
                                    "Previous, Next, Favorite, Shuffle, or Repeat",
                                    livePreviewActionIcon(state.playback.liveSongRightAction),
                                    state.playback.liveSongRightAction.name,
                                    LiveSongSideAction.entries.map { it.name },
                                    optionIcon = { livePreviewActionIcon(LiveSongSideAction.valueOf(it)) },
                                ) { viewModel.setLiveSongRightAction(LiveSongSideAction.valueOf(it)) }
                                }
                            }
                            }
                        }
                        SettingsRowDivider()
                        SettingsSleepTimerHeader(
                            state = sleepTimer,
                            lastMode = state.playback.sleepTimerLastMode,
                            lastMinutes = state.playback.sleepTimerLastMinutes,
                            endOfTrackEnabled = hasCurrentTrack,
                            onToggle = viewModel::setSleepTimerEnabled,
                        )
                        Column {
                            SleepTimerMenuContent(
                                    state = sleepTimer,
                                    endOfTrackEnabled = hasCurrentTrack,
                                    lastMinutes = state.playback.sleepTimerLastMinutes,
                                    lastMode = state.playback.sleepTimerLastMode,
                                    onSetMinutes = viewModel::setSleepTimerMinutes,
                                    onEndOfTrack = viewModel::setSleepTimerEndOfTrack,
                                    onExtend = { viewModel.extendSleepTimer(15) },
                                    onShorten = { viewModel.shortenSleepTimer(15) },
                                    onSetEnabled = viewModel::setSleepTimerEnabled,
                                    showStatusHeader = false,
                            )
                            SettingsRowDivider()
                            SettingsToggleRow(
                                    "Fade out",
                                    "Fade music during the final 5 seconds of the timer.",
                                    Icons.Rounded.Bedtime,
                                    state.playback.sleepTimerFadeEnabled,
                                    viewModel::setSleepTimerFadeEnabled,
                                    showSubtitle = true,
                            )
                        }
                    }
                }
            }
            if (page == SettingsCategory.SOUND) {
                item(key = "sound") {
                    SettingsGlassSection(hazeState, "Sound", "", Icons.Rounded.GraphicEq, showHeading = false) {
                            SettingsInfoRow("Equalizer", "", Icons.Rounded.Equalizer)
                            SettingsEqualizerControls()
                        SettingsRowDivider()
                            SettingsToggleRow("Loudness normalization",
                                "ReplayGain first; readable local audio may use conservative RMS dBFS, not LUFS.",
                                Icons.AutoMirrored.Rounded.VolumeUp, state.playback.loudnessNormalizationEnabled, viewModel::setLoudnessNormalization)
                            SettingsRowDivider()
                            SettingsSliderRow("Crossfade", "Overlap between supported local/readable tracks", Icons.Rounded.GraphicEq,
                                if (state.playback.crossfadeDurationSeconds == 0) "Off" else "${state.playback.crossfadeDurationSeconds} s",
                                state.playback.crossfadeDurationSeconds.toFloat(), 0f..12f, 11,
                                { viewModel.setCrossfadeSeconds(it.toInt().coerceIn(0, 12)) })
                    }
                }
            }
            if (page == SettingsCategory.DISCOVERY) {
                item(key = "recommendations") {
                    SettingsGlassSection(hazeState, "Discovery", "", Icons.Rounded.AutoAwesome, showHeading = false) {
                        SettingsToggleRow(
                            "Smart recommendations",
                            "Personalize music suggestions from your listening habits. Your data stays on this device.",
                            Icons.Rounded.AutoAwesome,
                            state.recommendations.smartRecommendationsEnabled,
                            viewModel::setSmart,
                            showSubtitle = true,
                            subtitleMaxLines = 3,
                        )
                        SettingsRowDivider()
                        SettingsSliderRow(
                            "Exploration",
                            if (state.recommendations.smartRecommendationsEnabled)
                                "Give rarely played songs more chances in Smart recommendations. Higher values encourage discovery."
                            else
                                "Give rarely played songs more chances in Smart recommendations. Enable Smart recommendations to adjust.",
                            Icons.Rounded.Explore,
                            "${state.recommendations.explorationPercent}%",
                            state.recommendations.explorationPercent.toFloat(),
                            0f..50f,
                            9,
                            { viewModel.setExploration(it.toInt()) },
                            enabled = state.recommendations.smartRecommendationsEnabled,
                            showSubtitle = true,
                            subtitleMaxLines = 3,
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            "Local audio analysis",
                            "Analyze readable audio files on this device to improve recommendations. Your music is not uploaded.",
                            Icons.Rounded.AutoAwesome,
                            state.recommendations.audioAnalysisEnabled,
                            viewModel::setAudioAnalysis,
                            showSubtitle = true,
                            subtitleMaxLines = 3,
                        )
                        SettingsRowDivider()
                        SettingsToggleRow(
                            "Diagnostics",
                            "Show local model details and evaluation metrics. Use them to understand how recommendations perform.",
                            Icons.Rounded.BugReport,
                            state.recommendations.diagnosticsVisible,
                            viewModel::setDiagnostics,
                            showSubtitle = true,
                            subtitleMaxLines = 3,
                        )
                        feedbackRows.forEach { row ->
                            SettingsRowDivider()
                            SettingsActionRow(
                                row.title,
                                "Remove your '${feedbackActionLabel(row.action)}' feedback for this song.",
                                Icons.AutoMirrored.Rounded.Undo,
                                "Undo",
                                { viewModel.undoRecommendationFeedback(row.trackId, row.action) },
                                showSubtitle = true,
                                subtitleMaxLines = 3,
                            )
                        }
                    }
                }
            }
            if (page == SettingsCategory.CONNECTIONS) {
                item(key = "telegram") {
                        SettingsGlassSection(hazeState, "Telegram", "Connection and selected music sources", Icons.Rounded.Cloud) {
                            SettingsActionRow("Music sources",
                                if (telegramConnected) "Manage selected chats" else "Connect Telegram to choose music chats",
                                Icons.Rounded.FolderOpen, "Manage", onOpenTelegram)
                            SettingsRowDivider()
                            SettingsActionRow("Sync selected sources",
                                if (!telegramConnected) "Connect Telegram to sync" else if (telegramSources.isSyncing) "Sync in progress" else "Refresh selected sources",
                                Icons.Rounded.Sync, if (telegramSources.isSyncing) "Syncing" else "Sync",
                                viewModel::syncTelegram, enabled = telegramConnected && !telegramSources.isSyncing)
                            SettingsRowDivider()
                            SettingsToggleRow("Wi-Fi only cloud downloads", "Applies to Telegram-backed downloads",
                                Icons.Rounded.Wifi, state.network.wifiOnlyDownloads, viewModel::setWifiOnly)
                        }
                    }
            }
            if (page == SettingsCategory.CONNECTIONS) {
                item(key = "storage") {
                        SettingsGlassSection(hazeState, "Downloads & storage", "Keep listening, even offline", Icons.Rounded.Download) {
                            SettingsActionRow("Offline files & downloads",
                                "${downloadSummary.activeCount} active · ${downloadSummary.pinnedCount} saved offline",
                                Icons.Rounded.DownloadForOffline, "Manage", onOpenDownloads)
                            SettingsRowDivider()
                            SettingsToggleRow("Prefetch", "Prepare likely next tracks when policy allows",
                                Icons.Rounded.Downloading, state.network.prefetchEnabled, viewModel::setPrefetch)
                            SettingsRowDivider()
                            SettingsToggleRow("Prefetch on metered networks",
                                "Available when prefetch is on and Wi-Fi-only downloads are off",
                                Icons.Rounded.Wifi, state.network.prefetchOnMetered, viewModel::setPrefetchOnMetered,
                                enabled = state.network.prefetchEnabled && !state.network.wifiOnlyDownloads)
                        }
                    }
            }
            if (page == SettingsCategory.DATA) {
                item(key = "privacy") {
                        SettingsGlassSection(hazeState, "Privacy & history", "Your listening, under your control", Icons.Rounded.Shield) {
                            SettingsActionRow(
                                "Browse history", "",
                                Icons.Rounded.History, "Open", onOpenHistory,
                            )
                            SettingsRowDivider()
                            SettingsToggleRow("Save listening history", null,
                                Icons.Rounded.History, state.network.listeningHistoryEnabled, viewModel::setHistory)
                        }
                    }
            }
            if (page == SettingsCategory.DATA) {
                item(key = "backup") {
                        SettingsGlassSection(hazeState, "Backup & restore", "Take your preferences with you", Icons.Rounded.Backup) {
                            SettingsToggleRow("Include listening history", "Off by default; trained model weights are never exported",
                                Icons.Rounded.History, includeHistoryInBackup, { includeHistoryInBackup = it })
                            SettingsRowDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(onClick = { exportLauncher.launch("meowzix-backup.mzx") }, modifier = Modifier.weight(1f)) { Text("Export") }
                                OutlinedButton(
                                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/*", "text/*")) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Import") }
                            }
                        }
                    }
            }
            if (page == SettingsCategory.DATA) {
                item(key = "manage-data") {
                        SettingsGlassSection(hazeState, "Data controls", "Changes that deserve a second look", Icons.Rounded.DeleteOutline) {
                            SettingsDangerRow("Clear listening history", "Delete raw listening events",
                                Icons.Rounded.History) { pendingDanger = SettingsDangerAction.CLEAR_HISTORY }
                            SettingsRowDivider()
                            SettingsDangerRow("Reset personalization", "Reset learned state without deleting music",
                                Icons.Rounded.RestartAlt) { pendingDanger = SettingsDangerAction.RESET_PERSONALIZATION }
                            if (feedbackRows.isNotEmpty()) {
                                SettingsRowDivider()
                                SettingsDangerRow("Clear recommendation feedback", "Remove all active feedback",
                                    Icons.Rounded.ThumbDown) { pendingDanger = SettingsDangerAction.CLEAR_FEEDBACK }
                            }
                            if (telegramConnected) {
                                SettingsRowDivider()
                                SettingsDangerRow("Disconnect Telegram", "Sign out of the connected account",
                                    Icons.AutoMirrored.Rounded.Logout) { pendingDanger = SettingsDangerAction.DISCONNECT_TELEGRAM }
                            }
                        }
                    }
            }
            if (page == SettingsCategory.INFORMATION) {
                item(key = "information") {
                    SettingsInformationContent(
                        hazeState = hazeState,
                        telegramStatus = if (telegramConnected)
                            "Telegram connected · " + telegramSources.selectedChatIds.size + " selected sources"
                        else "Telegram disconnected · offline music remains available",
                        downloadsStatus = downloadSummary.activeCount.toString() +
                            " active downloads · " + downloadSummary.pinnedCount + " saved offline",
                        managedStorage = storageUsage?.let { usage ->
                            "Managed storage: saved audio " + formatBytes(usage.pinnedOfflineBytes) +
                                ", artwork " + formatBytes(usage.otherManagedBytes)
                        } ?: "Managed storage is being calculated",
                        onOpenBrand = onOpenBrand,
                    )
                }
            }
        }
        }
        }
        MeowzixStickyHeader(
            title = selectedCategory?.title ?: "Settings",
            subtitle = "",
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            leadingActions = {
                MeowzixHeaderAction(
                    "Back",
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = {
                        if (selectedCategory == null) onBack() else selectedCategory = null
                    },
                )
            },
        )
        }
    }

    pendingIconSource?.let { chosen ->
        AlertDialog(
            onDismissRequest = { pendingIconSource = null },
            title = { Text("Change app icon?") },
            text = {
                Text("Apply the " +
                    (if (chosen == LauncherIconColorSource.WALLPAPER) "Wallpaper" else "App theme") +
                    " icon without restarting. Your launcher may take a moment to refresh.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingIconSource = null
                    onApplyLauncherIcon(chosen)
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { pendingIconSource = null }) { Text("Cancel") }
            },
        )
    }

    pendingDanger?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDanger = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDanger = null
                    when (action) {
                        SettingsDangerAction.CLEAR_HISTORY -> viewModel.clearHistory()
                        SettingsDangerAction.RESET_PERSONALIZATION -> viewModel.resetPersonalization()
                        SettingsDangerAction.DISCONNECT_TELEGRAM -> viewModel.disconnectTelegram()
                        SettingsDangerAction.CLEAR_FEEDBACK -> viewModel.clearRecommendationFeedback()
                    }
                }) { Text(action.confirm, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDanger = null }) { Text("Cancel") } },
        )
    }

    pendingRestore?.let { (bytes, preview) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${preview.recordCounts.values.sum()} records")
                    Text("${preview.unresolvedTrackReferences} track references cannot currently be matched")
                    if (preview.conflicts > 0) Text("${preview.conflicts} existing records have detectable conflicts; restore uses merge/non-destructive semantics.")
                    Text(if (preview.includeHistory) "Listening history is included" else "Listening history is not included")
                    Text("Restore merges state; it does not replace your library or Telegram login.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        runCatching { viewModel.restoreBackup(bytes) }
                            .onSuccess { Toast.makeText(context, "Restored ${it.restoredRecords} records; ${it.unresolvedReferences} unresolved", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, it.message ?: "Restore failed", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

private fun feedbackActionLabel(action: RecommendationFeedbackAction): String = when (action) {
    RecommendationFeedbackAction.MORE_LIKE_THIS -> "More like this"
    RecommendationFeedbackAction.SUGGEST_LESS -> "Suggest less"
    RecommendationFeedbackAction.SNOOZE -> "Snoozed"
}

private fun formatBytes(bytes: Long): String =
    if (bytes >= 1024L * MIB) "%.1f GiB".format(bytes / (1024f * MIB)) else "${bytes / MIB} MiB"

private const val MIB = 1024L * 1024L
