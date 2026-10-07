package dev.behradhz.meowzix.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.backup.BackupOptions
import dev.behradhz.meowzix.domain.backup.BackupPreview
import dev.behradhz.meowzix.domain.backup.BackupRestoreResult
import dev.behradhz.meowzix.domain.backup.LocalBackupRepository
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.ManagedStorageRepository
import dev.behradhz.meowzix.domain.downloads.ManagedStorageUsage
import dev.behradhz.meowzix.domain.history.ListeningHistoryRepository
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackRepository
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings
import dev.behradhz.meowzix.domain.settings.PlaybackPreferenceSettings
import dev.behradhz.meowzix.domain.settings.RecommendationPreferenceSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.StoragePolicySettings
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UnifiedSettingsState(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val playback: PlaybackPreferenceSettings = PlaybackPreferenceSettings(),
    val recommendations: RecommendationPreferenceSettings = RecommendationPreferenceSettings(),
    val network: NetworkPlaybackSettings = NetworkPlaybackSettings(),
    val storage: StoragePolicySettings = StoragePolicySettings(),
)

data class FeedbackSettingRow(
    val trackId: UUID,
    val title: String,
    val action: RecommendationFeedbackAction,
)

data class DownloadSettingsSummary(
    val activeCount: Int = 0,
    val pinnedCount: Int = 0,
)

@HiltViewModel
class UnifiedSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val history: ListeningHistoryRepository,
    private val feedback: RecommendationFeedbackRepository,
    private val backup: LocalBackupRepository,
    private val telegram: TelegramRepository,
    private val downloads: DownloadRepository,
    private val managedStorage: ManagedStorageRepository,
    library: MusicLibraryRepository,
) : ViewModel() {
    val state = combine(
        settings.appearanceSettings,
        settings.playbackPreferenceSettings,
        settings.recommendationPreferenceSettings,
        settings.networkPlaybackSettings,
        settings.storagePolicySettings,
    ) { appearance, playback, recommendations, network, storage ->
        UnifiedSettingsState(appearance, playback, recommendations, network, storage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnifiedSettingsState())

    val telegramAuthState = telegram.authState
    val telegramSourceState = telegram.musicSourceState

    val activeFeedback = combine(feedback.feedback, library.observeTracks()) { rows, tracks ->
        val titles = tracks.associate { it.id to it.title }
        val now = Instant.now()
        rows.filter { it.isActive(now) }.map { row ->
            FeedbackSettingRow(row.trackId, titles[row.trackId] ?: "Unknown track", row.action)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadSummary = downloads.observeDownloads().map { rows ->
        DownloadSettingsSummary(
            activeCount = rows.count {
                it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.PAUSED
            },
            pinnedCount = rows.count { it.pinned && it.status == DownloadStatus.COMPLETED },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadSettingsSummary())

    private val _storageUsage = MutableStateFlow<ManagedStorageUsage?>(null)
    val storageUsage = _storageUsage.asStateFlow()

    init {
        refreshStorageUsage()
    }

    fun setTheme(value: ThemePreference) = launch { settings.setThemePreference(value) }
    fun setLyricsScale(value: Int) = launch { settings.setLyricsTextScalePercent(value) }
    fun setReduceMotion(value: Boolean) = launch { settings.setReduceMotion(value) }
    fun setDefaultMode(value: PlaybackMode) = launch { settings.setDefaultPlaybackMode(value) }
    fun setDefaultRepeat(value: RepeatMode) = launch { settings.setDefaultRepeatMode(value) }
    fun setResume(value: Boolean) = launch { settings.setResumeOnLaunch(value) }
    fun setSmart(value: Boolean) = launch { settings.setSmartRecommendationsEnabled(value) }
    fun setExploration(value: Int) = launch { settings.setExplorationPercent(value) }
    fun setAudioAnalysis(value: Boolean) = launch { settings.setAudioAnalysisEnabled(value) }
    fun setDiagnostics(value: Boolean) = launch { settings.setDiagnosticsVisible(value) }
    fun setHistory(value: Boolean) = launch { settings.setListeningHistoryEnabled(value) }
    fun setWifiOnly(value: Boolean) = launch { settings.setWifiOnlyDownloads(value) }
    fun setPrefetch(value: Boolean) = launch { settings.setPrefetchEnabled(value) }
    fun setPrefetchOnMetered(value: Boolean) = launch { settings.setPrefetchOnMetered(value) }
    fun setCacheBudget(bytes: Long) = launch {
        settings.setTemporaryCacheBudgetBytes(bytes)
        _storageUsage.value = managedStorage.reconcileAndEnforceBudget()
    }
    fun clearHistory() = launch { history.clear() }
    fun resetPersonalization() = launch { history.resetPersonalization(); feedback.clearAll() }
    fun clearRecommendationFeedback() = launch { feedback.clearAll() }
    fun undoRecommendationFeedback(trackId: UUID, action: RecommendationFeedbackAction) =
        launch { feedback.undo(trackId, action) }

    fun syncTelegram() = telegram.syncSelectedSources()
    fun disconnectTelegram() = telegram.logout()

    fun clearTemporaryCache() = launch { _storageUsage.value = managedStorage.clearTemporaryCache() }
    fun refreshStorageUsage() = launch { _storageUsage.value = managedStorage.usage() }

    suspend fun exportBackup(includeHistory: Boolean): ByteArray = backup.export(BackupOptions(includeHistory))
    suspend fun previewBackup(bytes: ByteArray): BackupPreview = backup.preview(bytes)
    suspend fun restoreBackup(bytes: ByteArray): BackupRestoreResult = backup.restore(bytes)

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
