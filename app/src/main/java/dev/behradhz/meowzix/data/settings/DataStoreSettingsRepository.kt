package dev.behradhz.meowzix.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.TelegramForwardSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SettingsRepository {
    override val networkPlaybackSettings: Flow<NetworkPlaybackSettings> = context.settingsDataStore.data.map { values ->
        NetworkPlaybackSettings(
            offlineMode = values[OFFLINE_MODE] ?: false,
            wifiOnlyDownloads = values[WIFI_ONLY] ?: false,
            prefetchEnabled = values[PREFETCH] ?: true,
            prefetchOnMetered = values[PREFETCH_METERED] ?: false,
            listeningHistoryEnabled = values[LISTENING_HISTORY] ?: true,
        )
    }

    override val telegramForwardSettings: Flow<TelegramForwardSettings> = context.settingsDataStore.data.map { values ->
        TelegramForwardSettings(
            includeSourceAttribution = values[FORWARD_INCLUDE_SOURCE] ?: true,
            keepCaption = values[FORWARD_KEEP_CAPTION] ?: true,
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
    }
}
