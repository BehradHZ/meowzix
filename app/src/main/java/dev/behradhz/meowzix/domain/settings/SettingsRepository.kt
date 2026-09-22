package dev.behradhz.meowzix.domain.settings

import kotlinx.coroutines.flow.Flow

data class NetworkPlaybackSettings(
    val offlineMode: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val prefetchEnabled: Boolean = true,
    val prefetchOnMetered: Boolean = false,
    val listeningHistoryEnabled: Boolean = true,
)

data class TelegramForwardSettings(
    val includeSourceAttribution: Boolean = true,
    val keepCaption: Boolean = true,
)

interface SettingsRepository {
    val networkPlaybackSettings: Flow<NetworkPlaybackSettings>
    val telegramForwardSettings: Flow<TelegramForwardSettings>

    suspend fun setOfflineMode(enabled: Boolean)
    suspend fun setWifiOnlyDownloads(enabled: Boolean)
    suspend fun setPrefetchEnabled(enabled: Boolean)
    suspend fun setPrefetchOnMetered(enabled: Boolean)
    suspend fun setListeningHistoryEnabled(enabled: Boolean)
    suspend fun setTelegramForwardDefaults(includeSourceAttribution: Boolean, keepCaption: Boolean)
}
