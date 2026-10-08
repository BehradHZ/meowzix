package dev.behradhz.meowzix.domain.settings

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import kotlinx.coroutines.flow.Flow

data class NetworkPlaybackSettings(
    val offlineMode: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val prefetchEnabled: Boolean = true,
    val prefetchOnMetered: Boolean = false,
    val listeningHistoryEnabled: Boolean = true,
)

data class StoragePolicySettings(
    val temporaryCacheBudgetBytes: Long = DEFAULT_TEMPORARY_CACHE_BUDGET_BYTES,
)

const val DEFAULT_TEMPORARY_CACHE_BUDGET_BYTES: Long = 512L * 1024L * 1024L

data class TelegramForwardSettings(
    val includeSourceAttribution: Boolean = true,
    val keepCaption: Boolean = true,
)

enum class LibrarySortMode { RECENTLY_ADDED, OLDEST_ADDED, TITLE_ASC, TITLE_DESC, ARTIST_ASC }
enum class LibraryGroupMode { NONE, ARTIST, ALBUM, YEAR }

data class LibraryDisplaySettings(
    val sortMode: LibrarySortMode = LibrarySortMode.RECENTLY_ADDED,
    val groupMode: LibraryGroupMode = LibraryGroupMode.NONE,
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val lyricsTextScalePercent: Int = 100,
    val reduceMotion: Boolean = false,
)

data class PlaybackPreferenceSettings(
    val defaultMode: PlaybackMode = PlaybackMode.ORDERED,
    val defaultRepeat: RepeatMode = RepeatMode.OFF,
    val resumeOnLaunch: Boolean = true,
    val loudnessNormalizationEnabled: Boolean = false,
    val crossfadeDurationSeconds: Int = 0,
)

data class RecommendationPreferenceSettings(
    val smartRecommendationsEnabled: Boolean = true,
    val explorationPercent: Int = 15,
    val audioAnalysisEnabled: Boolean = true,
    val diagnosticsVisible: Boolean = false,
)

interface SettingsRepository {
    val networkPlaybackSettings: Flow<NetworkPlaybackSettings>
    val storagePolicySettings: Flow<StoragePolicySettings>
    val telegramForwardSettings: Flow<TelegramForwardSettings>
    val libraryDisplaySettings: Flow<LibraryDisplaySettings>
    val appearanceSettings: Flow<AppearanceSettings>
    val playbackPreferenceSettings: Flow<PlaybackPreferenceSettings>
    val recommendationPreferenceSettings: Flow<RecommendationPreferenceSettings>

    suspend fun setOfflineMode(enabled: Boolean)
    suspend fun setWifiOnlyDownloads(enabled: Boolean)
    suspend fun setPrefetchEnabled(enabled: Boolean)
    suspend fun setPrefetchOnMetered(enabled: Boolean)
    suspend fun setListeningHistoryEnabled(enabled: Boolean)
    suspend fun setTemporaryCacheBudgetBytes(bytes: Long)
    suspend fun setTelegramForwardDefaults(includeSourceAttribution: Boolean, keepCaption: Boolean)
    suspend fun setLibrarySortMode(mode: LibrarySortMode)
    suspend fun setLibraryGroupMode(mode: LibraryGroupMode)
    suspend fun setThemePreference(theme: ThemePreference)
    suspend fun setLyricsTextScalePercent(percent: Int)
    suspend fun setReduceMotion(enabled: Boolean)
    suspend fun setDefaultPlaybackMode(mode: PlaybackMode)
    suspend fun setDefaultRepeatMode(mode: RepeatMode)
    suspend fun setResumeOnLaunch(enabled: Boolean)
    suspend fun setLoudnessNormalizationEnabled(enabled: Boolean)
    suspend fun setCrossfadeDurationSeconds(seconds: Int)
    suspend fun setSmartRecommendationsEnabled(enabled: Boolean)
    suspend fun setExplorationPercent(percent: Int)
    suspend fun setAudioAnalysisEnabled(enabled: Boolean)
    suspend fun setDiagnosticsVisible(enabled: Boolean)
}
