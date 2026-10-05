package dev.behradhz.meowzix.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.DEFAULT_TEMPORARY_CACHE_BUDGET_BYTES
import dev.behradhz.meowzix.domain.settings.LibraryDisplaySettings
import dev.behradhz.meowzix.domain.settings.LibraryGroupMode
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
import dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings
import dev.behradhz.meowzix.domain.settings.PlaybackPreferenceSettings
import dev.behradhz.meowzix.domain.settings.RecommendationPreferenceSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.StoragePolicySettings
import dev.behradhz.meowzix.domain.settings.TelegramForwardSettings
import dev.behradhz.meowzix.domain.settings.ThemePreference
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SettingsRepository {
    private val safeData = context.settingsDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val networkPlaybackSettings: Flow<NetworkPlaybackSettings> = safeData.map { values ->
        NetworkPlaybackSettings(
            offlineMode = values[OFFLINE_MODE] ?: false,
            wifiOnlyDownloads = values[WIFI_ONLY] ?: false,
            prefetchEnabled = values[PREFETCH] ?: true,
            prefetchOnMetered = values[PREFETCH_METERED] ?: false,
            listeningHistoryEnabled = values[LISTENING_HISTORY] ?: true,
        )
    }

    override val storagePolicySettings: Flow<StoragePolicySettings> = safeData.map { values ->
        StoragePolicySettings(
            temporaryCacheBudgetBytes = (values[TEMPORARY_CACHE_BUDGET_BYTES]
                ?: DEFAULT_TEMPORARY_CACHE_BUDGET_BYTES).coerceIn(MIN_TEMPORARY_CACHE_BUDGET_BYTES, MAX_TEMPORARY_CACHE_BUDGET_BYTES),
        )
    }

    override val telegramForwardSettings: Flow<TelegramForwardSettings> = safeData.map { values ->
        TelegramForwardSettings(
            includeSourceAttribution = values[FORWARD_INCLUDE_SOURCE] ?: true,
            keepCaption = values[FORWARD_KEEP_CAPTION] ?: true,
        )
    }

    override val libraryDisplaySettings: Flow<LibraryDisplaySettings> = safeData.map { values ->
        LibraryDisplaySettings(
            sortMode = values[LIBRARY_SORT_MODE].enumOrDefault(LibrarySortMode.RECENTLY_ADDED),
            groupMode = values[LIBRARY_GROUP_MODE].enumOrDefault(LibraryGroupMode.NONE),
        )
    }

    override val appearanceSettings: Flow<AppearanceSettings> = safeData.map { values ->
        AppearanceSettings(
            theme = values[THEME].enumOrDefault(ThemePreference.SYSTEM),
            lyricsTextScalePercent = (values[LYRICS_TEXT_SCALE] ?: 100).coerceIn(80, 160),
            reduceMotion = values[REDUCE_MOTION] ?: false,
        )
    }

    override val playbackPreferenceSettings: Flow<PlaybackPreferenceSettings> = safeData.map { values ->
        PlaybackPreferenceSettings(
            defaultMode = values[DEFAULT_PLAYBACK_MODE].enumOrDefault(PlaybackMode.ORDERED),
            defaultRepeat = values[DEFAULT_REPEAT_MODE].enumOrDefault(RepeatMode.OFF),
            resumeOnLaunch = values[RESUME_ON_LAUNCH] ?: true,
        )
    }

    override val recommendationPreferenceSettings: Flow<RecommendationPreferenceSettings> = safeData.map { values ->
        RecommendationPreferenceSettings(
            smartRecommendationsEnabled = values[SMART_RECOMMENDATIONS] ?: true,
            explorationPercent = (values[EXPLORATION_PERCENT] ?: 15).coerceIn(0, 50),
            audioAnalysisEnabled = values[AUDIO_ANALYSIS] ?: true,
            diagnosticsVisible = values[DIAGNOSTICS_VISIBLE] ?: false,
        )
    }

    override suspend fun setOfflineMode(enabled: Boolean) = set(OFFLINE_MODE, enabled)
    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = set(WIFI_ONLY, enabled)
    override suspend fun setPrefetchEnabled(enabled: Boolean) = set(PREFETCH, enabled)
    override suspend fun setPrefetchOnMetered(enabled: Boolean) = set(PREFETCH_METERED, enabled)
    override suspend fun setListeningHistoryEnabled(enabled: Boolean) = set(LISTENING_HISTORY, enabled)
    override suspend fun setReduceMotion(enabled: Boolean) = set(REDUCE_MOTION, enabled)
    override suspend fun setResumeOnLaunch(enabled: Boolean) = set(RESUME_ON_LAUNCH, enabled)
    override suspend fun setSmartRecommendationsEnabled(enabled: Boolean) = set(SMART_RECOMMENDATIONS, enabled)
    override suspend fun setAudioAnalysisEnabled(enabled: Boolean) = set(AUDIO_ANALYSIS, enabled)
    override suspend fun setDiagnosticsVisible(enabled: Boolean) = set(DIAGNOSTICS_VISIBLE, enabled)

    override suspend fun setTemporaryCacheBudgetBytes(bytes: Long) {
        context.settingsDataStore.edit { it[TEMPORARY_CACHE_BUDGET_BYTES] = bytes.coerceIn(MIN_TEMPORARY_CACHE_BUDGET_BYTES, MAX_TEMPORARY_CACHE_BUDGET_BYTES) }
    }

    override suspend fun setTelegramForwardDefaults(includeSourceAttribution: Boolean, keepCaption: Boolean) {
        context.settingsDataStore.edit {
            it[FORWARD_INCLUDE_SOURCE] = includeSourceAttribution
            it[FORWARD_KEEP_CAPTION] = if (includeSourceAttribution) true else keepCaption
        }
    }

    override suspend fun setLibrarySortMode(mode: LibrarySortMode) = setEnum(LIBRARY_SORT_MODE, mode)
    override suspend fun setLibraryGroupMode(mode: LibraryGroupMode) = setEnum(LIBRARY_GROUP_MODE, mode)
    override suspend fun setThemePreference(theme: ThemePreference) = setEnum(THEME, theme)
    override suspend fun setDefaultPlaybackMode(mode: PlaybackMode) = setEnum(DEFAULT_PLAYBACK_MODE, mode)
    override suspend fun setDefaultRepeatMode(mode: RepeatMode) = setEnum(DEFAULT_REPEAT_MODE, mode)

    override suspend fun setLyricsTextScalePercent(percent: Int) {
        context.settingsDataStore.edit { it[LYRICS_TEXT_SCALE] = percent.coerceIn(80, 160) }
    }

    override suspend fun setExplorationPercent(percent: Int) {
        context.settingsDataStore.edit { it[EXPLORATION_PERCENT] = percent.coerceIn(0, 50) }
    }

    private suspend fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun setEnum(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: Enum<*>) {
        context.settingsDataStore.edit { it[key] = value.name }
    }

    private companion object {
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val PREFETCH = booleanPreferencesKey("prefetch_enabled")
        val PREFETCH_METERED = booleanPreferencesKey("prefetch_on_metered")
        val LISTENING_HISTORY = booleanPreferencesKey("listening_history_enabled")
        val TEMPORARY_CACHE_BUDGET_BYTES = longPreferencesKey("temporary_cache_budget_bytes")
        val FORWARD_INCLUDE_SOURCE = booleanPreferencesKey("telegram_forward_include_source")
        val FORWARD_KEEP_CAPTION = booleanPreferencesKey("telegram_forward_keep_caption")
        val LIBRARY_SORT_MODE = stringPreferencesKey("library_sort_mode")
        val LIBRARY_GROUP_MODE = stringPreferencesKey("library_group_mode")
        val THEME = stringPreferencesKey("theme_preference")
        val LYRICS_TEXT_SCALE = intPreferencesKey("lyrics_text_scale_percent")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val DEFAULT_PLAYBACK_MODE = stringPreferencesKey("default_playback_mode")
        val DEFAULT_REPEAT_MODE = stringPreferencesKey("default_repeat_mode")
        val RESUME_ON_LAUNCH = booleanPreferencesKey("resume_on_launch")
        val SMART_RECOMMENDATIONS = booleanPreferencesKey("smart_recommendations_enabled")
        val EXPLORATION_PERCENT = intPreferencesKey("recommendation_exploration_percent")
        val AUDIO_ANALYSIS = booleanPreferencesKey("audio_analysis_enabled")
        val DIAGNOSTICS_VISIBLE = booleanPreferencesKey("diagnostics_visible")
        const val MIN_TEMPORARY_CACHE_BUDGET_BYTES = 64L * 1024L * 1024L
        const val MAX_TEMPORARY_CACHE_BUDGET_BYTES = 8L * 1024L * 1024L * 1024L
    }
}

private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
