package dev.behradhz.meowzix.feature.profile

import android.widget.Toast
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
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import dev.behradhz.meowzix.feature.library.PlaylistGlassBackdrop
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.backup.BackupPreview
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import dev.behradhz.meowzix.ui.components.SleepTimerControls
import dev.behradhz.meowzix.ui.components.sleepTimerStatusLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UnifiedSettingsScreen(
    onBack: () -> Unit,
    onOpenBrand: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenTelegram: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
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
    var showSleepTimer by remember { mutableStateOf(false) }
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

    PlaylistGlassBackdrop { hazeState ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 196.dp),
            verticalArrangement = Arrangement.spacedBy(25.dp),
        ) {
            item(key = "navigation") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    PlaylistGlassPanel(
                        hazeState,
                        modifier = Modifier.size(52.dp).clickable(role = Role.Button, onClick = onBack),
                        radius = 19.dp,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "MEOWZIX",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp, letterSpacing = 1.5.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 30.sp, lineHeight = 36.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            item(key = "hero") {
                SettingsGlassHero(
                    hazeState,
                    telegramConnected,
                    state.recommendations.smartRecommendationsEnabled,
                    downloadSummary,
                    onOpenBrand = onOpenBrand,
                )
            }
            item(key = "appearance") {
                        SettingsGlassSection(hazeState, "Appearance", "Color, typography and motion", Icons.Rounded.Palette) {
                            SettingsChoiceRow("Theme", null, Icons.Rounded.DarkMode,
                                state.appearance.theme.name.lowercase().replaceFirstChar(Char::uppercase),
                                ThemePreference.entries.map { it.name }) { viewModel.setTheme(ThemePreference.valueOf(it)) }
                            SettingsRowDivider()
                            SettingsColorPicker(state.appearance, viewModel::setColorTheme, viewModel::setCustomColorSeed)
                            SettingsRowDivider()
                            SettingsSliderRow("Lyrics text size", null, Icons.Rounded.TextFields,
                                "${state.appearance.lyricsTextScalePercent}%", state.appearance.lyricsTextScalePercent.toFloat(),
                                80f..160f, 7, { viewModel.setLyricsScale(it.toInt()) })
                            SettingsRowDivider()
                            SettingsToggleRow("Reduced motion", "Shorter, more restrained lyric and UI animations",
                                Icons.Rounded.Animation, state.appearance.reduceMotion, viewModel::setReduceMotion)
                        }
                    }
                    item(key = "playback") {
                        SettingsGlassSection(hazeState, "Playback & sound", "Fine-tune every listening session", Icons.Rounded.PlayCircle) {
                            SettingsChoiceRow("Default playback mode", null, Icons.Rounded.QueueMusic,
                                state.playback.defaultMode.name, PlaybackMode.entries.map { it.name }) { viewModel.setDefaultMode(PlaybackMode.valueOf(it)) }
                            SettingsRowDivider()
                            SettingsChoiceRow("Default repeat", null, Icons.Rounded.Repeat,
                                state.playback.defaultRepeat.name, RepeatMode.entries.map { it.name }) { viewModel.setDefaultRepeat(RepeatMode.valueOf(it)) }
                            SettingsRowDivider()
                            SettingsToggleRow("Resume playback state", "Restore the previous queue and position",
                                Icons.Rounded.Restore, state.playback.resumeOnLaunch, viewModel::setResume)
                            SettingsRowDivider()
                            SettingsActionRow("Equalizer", "Device-aware audio controls", Icons.Rounded.Equalizer, "Open", onOpenEqualizer)
                            SettingsRowDivider()
                            SettingsActionRow("Sleep timer", sleepTimerStatusLabel(sleepTimer), Icons.Rounded.Bedtime, "Configure", { showSleepTimer = true })
                            SettingsRowDivider()
                            SettingsToggleRow("Loudness normalization",
                                "ReplayGain first; readable local audio may use conservative RMS dBFS, not LUFS.",
                                Icons.Rounded.VolumeUp, state.playback.loudnessNormalizationEnabled, viewModel::setLoudnessNormalization)
                            SettingsRowDivider()
                            SettingsSliderRow("Crossfade", "Overlap between supported local/readable tracks", Icons.Rounded.GraphicEq,
                                if (state.playback.crossfadeDurationSeconds == 0) "Off" else "${state.playback.crossfadeDurationSeconds} s",
                                state.playback.crossfadeDurationSeconds.toFloat(), 0f..12f, 11,
                                { viewModel.setCrossfadeSeconds(it.toInt().coerceIn(0, 12)) })
                            SettingsRowDivider()
                            SettingsInfoRow("Gapless & crossfade limits",
                                "Gapless is automatic where supported. Progressive Telegram is not guaranteed gapless. EQ, sleep timer, repeat-one, short tracks, queue changes or preload failure may prevent crossfade.",
                                Icons.Rounded.Tune)
                        }
                    }
                    item(key = "recommendations") {
                        SettingsGlassSection(hazeState, "Discovery", "Private recommendations, made for you", Icons.Rounded.AutoAwesome) {
                            SettingsToggleRow("Smart recommendations", "Enable learned recommendation surfaces",
                                Icons.Rounded.AutoAwesome, state.recommendations.smartRecommendationsEnabled, viewModel::setSmart)
                            SettingsRowDivider()
                            SettingsSliderRow("Exploration",
                                if (state.recommendations.smartRecommendationsEnabled) "Balance familiar tracks and discoveries" else "Enable Smart recommendations to adjust",
                                Icons.Rounded.Explore, "${state.recommendations.explorationPercent}%",
                                state.recommendations.explorationPercent.toFloat(), 0f..50f, 9,
                                { viewModel.setExploration(it.toInt()) }, enabled = state.recommendations.smartRecommendationsEnabled)
                            SettingsRowDivider()
                            SettingsToggleRow("Local audio analysis", "Analyze readable local audio only",
                                Icons.Rounded.AutoAwesome, state.recommendations.audioAnalysisEnabled, viewModel::setAudioAnalysis)
                            SettingsRowDivider()
                            if (feedbackRows.isEmpty()) {
                                SettingsInfoRow("Active feedback", "No active recommendation feedback", Icons.Rounded.ThumbUp)
                            } else {
                                SettingsInfoRow("Active feedback", "${feedbackRows.size} current preferences", Icons.Rounded.ThumbUp)
                                feedbackRows.forEach { row ->
                                    SettingsRowDivider()
                                    SettingsActionRow(
                                        row.title, feedbackActionLabel(row.action), Icons.Rounded.Undo, "Undo",
                                        { viewModel.undoRecommendationFeedback(row.trackId, row.action) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "telegram") {
                        SettingsGlassSection(hazeState, "Telegram", "Connection and selected music sources", Icons.Rounded.Cloud) {
                            SettingsInfoRow("Connection",
                                if (telegramConnected) "Connected · ${telegramSources.selectedChatIds.size} selected music source(s)" else "Disconnected · local and offline music remain available",
                                Icons.Rounded.CloudDone)
                            SettingsRowDivider()
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
                    item(key = "storage") {
                        SettingsGlassSection(hazeState, "Downloads & storage", "Keep listening, even offline", Icons.Rounded.Download) {
                            SettingsInfoRow("Managed storage",
                                storageUsage?.let { usage ->
                                    "Temporary ${formatBytes(usage.temporaryPlaybackBytes)} · Pinned ${formatBytes(usage.pinnedOfflineBytes)} · Other ${formatBytes(usage.otherManagedBytes)}"
                                } ?: "Calculating storage usage…",
                                Icons.Rounded.Storage)
                            SettingsRowDivider()
                            SettingsActionRow("Offline files & downloads",
                                "${downloadSummary.activeCount} active · ${downloadSummary.pinnedCount} pinned/offline",
                                Icons.Rounded.DownloadForOffline, "Manage", onOpenDownloads)
                            SettingsRowDivider()
                            SettingsToggleRow("Prefetch", "Prepare likely next tracks when policy allows",
                                Icons.Rounded.Downloading, state.network.prefetchEnabled, viewModel::setPrefetch)
                            SettingsRowDivider()
                            SettingsToggleRow("Prefetch on metered networks",
                                "Available when prefetch is on and Wi-Fi-only downloads are off",
                                Icons.Rounded.Wifi, state.network.prefetchOnMetered, viewModel::setPrefetchOnMetered,
                                enabled = state.network.prefetchEnabled && !state.network.wifiOnlyDownloads)
                            SettingsRowDivider()
                            SettingsSliderRow("Temporary cache budget", "Maximum app-managed temporary playback storage", Icons.Rounded.Storage,
                                formatBytes(state.storage.temporaryCacheBudgetBytes),
                                state.storage.temporaryCacheBudgetBytes / MIB.toFloat(), 64f..2048f, 30,
                                { viewModel.setCacheBudget(it.toLong() * MIB) })
                        }
                    }
                    item(key = "privacy") {
                        SettingsGlassSection(hazeState, "Privacy & history", "Your listening, under your control", Icons.Rounded.Shield) {
                            SettingsActionRow(
                                "Listening history", "Browse recently played music",
                                Icons.Rounded.History, "Open", onOpenHistory,
                            )
                            SettingsRowDivider()
                            SettingsToggleRow("Listening history", "Turning this off stops new learning-event capture",
                                Icons.Rounded.History, state.network.listeningHistoryEnabled, viewModel::setHistory)
                            SettingsRowDivider()
                            SettingsInfoRow("Personalization controls",
                                "Use Manage data below to clear raw history or reset learned state separately.",
                                Icons.Rounded.PrivacyTip)
                        }
                    }
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
                    item(key = "advanced") {
                        SettingsGlassSection(hazeState, "Advanced", "Diagnostics and local models", Icons.Rounded.Tune) {
                            SettingsToggleRow("Diagnostics", "Show local model and evaluation details",
                                Icons.Rounded.BugReport, state.recommendations.diagnosticsVisible, viewModel::setDiagnostics)
                        }
                    }
                    item(key = "manage-data") {
                        SettingsGlassSection(hazeState, "Data controls", "Changes that deserve a second look", Icons.Rounded.DeleteOutline) {
                            SettingsDangerRow("Clear temporary cache", "Pinned offline and MediaStore files are preserved",
                                Icons.Rounded.DeleteSweep) { pendingDanger = SettingsDangerAction.CLEAR_CACHE }
                            SettingsRowDivider()
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
                                    Icons.Rounded.Logout) { pendingDanger = SettingsDangerAction.DISCONNECT_TELEGRAM }
                            }
                        }
                    }
        }
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
                        SettingsDangerAction.CLEAR_CACHE -> viewModel.clearTemporaryCache()
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

    if (showSleepTimer) {
        AlertDialog(
            onDismissRequest = { showSleepTimer = false },
            title = { Text("Sleep timer") },
            text = {
                SleepTimerControls(
                    state = sleepTimer,
                    endOfTrackEnabled = hasCurrentTrack,
                    onSetMinutes = viewModel::setSleepTimerMinutes,
                    onEndOfTrack = viewModel::setSleepTimerEndOfTrack,
                    onExtend = { viewModel.extendSleepTimer(15) },
                    onCancel = viewModel::cancelSleepTimer,
                )
            },
            confirmButton = { TextButton(onClick = { showSleepTimer = false }) { Text("Done") } },
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
