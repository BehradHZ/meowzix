package dev.behradhz.meowzix.domain.settings

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

enum class LibrarySortMode {
    RECENTLY_ADDED,
    OLDEST_ADDED,
    TITLE_ASC,
    TITLE_DESC,
    ARTIST_ASC,
}

enum class LibraryGroupMode {
    NONE,
    ARTIST,
    ALBUM,
    YEAR,
}

data class LibraryDisplaySettings(
    val sortMode: LibrarySortMode = LibrarySortMode.RECENTLY_ADDED,
    val groupMode: LibraryGroupMode = LibraryGroupMode.NONE,
)

interface SettingsRepository {
    val networkPlaybackSettings: Flow<NetworkPlaybackSettings>
    val storagePolicySettings: Flow<StoragePolicySettings>
    val telegramForwardSettings: Flow<TelegramForwardSettings>
    val libraryDisplaySettings: Flow<LibraryDisplaySettings>

    suspend fun setOfflineMode(enabled: Boolean)
    suspend fun setWifiOnlyDownloads(enabled: Boolean)
    suspend fun setPrefetchEnabled(enabled: Boolean)
    suspend fun setPrefetchOnMetered(enabled: Boolean)
    suspend fun setListeningHistoryEnabled(enabled: Boolean)
    suspend fun setTemporaryCacheBudgetBytes(bytes: Long)
    suspend fun setTelegramForwardDefaults(includeSourceAttribution: Boolean, keepCaption: Boolean)
    suspend fun setLibrarySortMode(mode: LibrarySortMode)
    suspend fun setLibraryGroupMode(mode: LibraryGroupMode)
}
