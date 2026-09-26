package dev.behradhz.meowzix.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.settings.LibraryDisplaySettings
import dev.behradhz.meowzix.domain.settings.LibraryGroupMode
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
import dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.TelegramForwardSettings
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

    override val telegramForwardSettings: Flow<TelegramForwardSettings> = safeData.map { values ->
        TelegramForwardSettings(
            includeSourceAttribution = values[FORWARD_INCLUDE_SOURCE] ?: true,
            keepCaption = values[FORWARD_KEEP_CAPTION] ?: true,
        )
    }

    override val libraryDisplaySettings: Flow<LibraryDisplaySettings> = safeData.map { values ->
        LibraryDisplaySettings(
            sortMode = values[LIBRARY_SORT_MODE]
                ?.let { saved -> runCatching { LibrarySortMode.valueOf(saved) }.getOrNull() }
                ?: LibrarySortMode.RECENTLY_ADDED,
            groupMode = values[LIBRARY_GROUP_MODE]
                ?.let { saved -> runCatching { LibraryGroupMode.valueOf(saved) }.getOrNull() }
                ?: LibraryGroupMode.NONE,
        )
    }

    override suspend fun setOfflineMode(enabled: Boolean) = set(OFFLINE_MODE, enabled)
    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = set(WIFI_ONLY, enabled)
    override suspend fun setPrefetchEnabled(enabled: Boolean) = set(PREFETCH, enabled)
    override suspend fun setPrefetchOnMetered(enabled: Boolean) = set(PREFETCH_METERED, enabled)
    override suspend fun setListeningHistoryEnabled(enabled: Boolean) = set(LISTENING_HISTORY, enabled)

    override suspend fun setTelegramForwardDefaults(includeSourceAttribution: Boolean, keepCaption: Boolean) {
        context.settingsDataStore.edit {
            it[FORWARD_INCLUDE_SOURCE] = includeSourceAttribution
            it[FORWARD_KEEP_CAPTION] = if (includeSourceAttribution) true else keepCaption
        }
    }

    override suspend fun setLibrarySortMode(mode: LibrarySortMode) {
        context.settingsDataStore.edit { it[LIBRARY_SORT_MODE] = mode.name }
    }

    override suspend fun setLibraryGroupMode(mode: LibraryGroupMode) {
        context.settingsDataStore.edit { it[LIBRARY_GROUP_MODE] = mode.name }
    }

    private suspend fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private companion object {
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val PREFETCH = booleanPreferencesKey("prefetch_enabled")
        val PREFETCH_METERED = booleanPreferencesKey("prefetch_on_metered")
        val LISTENING_HISTORY = booleanPreferencesKey("listening_history_enabled")
        val FORWARD_INCLUDE_SOURCE = booleanPreferencesKey("telegram_forward_include_source")
        val FORWARD_KEEP_CAPTION = booleanPreferencesKey("telegram_forward_keep_caption")
        val LIBRARY_SORT_MODE = stringPreferencesKey("library_sort_mode")
        val LIBRARY_GROUP_MODE = stringPreferencesKey("library_group_mode")
    }
}
