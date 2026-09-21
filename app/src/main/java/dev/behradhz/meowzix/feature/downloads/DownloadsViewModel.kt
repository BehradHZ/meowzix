package dev.behradhz.meowzix.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadRow(
    val trackId: UUID,
    val title: String,
    val artworkRef: String?,
    val status: DownloadStatus,
    val progress: Float?,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val failureReason: String?,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    library: MusicLibraryRepository,
    private val settings: SettingsRepository,
    private val telegram: TelegramRepository,
) : ViewModel() {
    val rows = combine(downloads.observeDownloads(), library.observeTracks()) { records, tracks ->
        val byId = tracks.associateBy { it.id }
        records.map { record ->
            val track = byId[record.trackId]
            DownloadRow(
                trackId = record.trackId,
                title = track?.title ?: "Unknown track",
                artworkRef = track?.artworkRef,
                status = record.status,
                progress = record.totalBytes?.takeIf { it > 0L }?.let {
                    (record.downloadedBytes.toFloat() / it).coerceIn(0f, 1f)
                },
                downloadedBytes = record.downloadedBytes,
                totalBytes = record.totalBytes,
                failureReason = record.failureReason,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedTelegramChats = telegram.musicSourceState
        .map { state -> state.chats.filter(TelegramChatSummary::selected) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val networkSettings = settings.networkPlaybackSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings())

    fun downloadAll(chatId: Long) = viewModelScope.launch {
        telegram.trackIdsForChat(chatId).forEach(downloads::pinOffline)
    }
    fun retry(trackId: UUID) = downloads.retry(trackId)
    fun cancel(trackId: UUID) = downloads.cancel(trackId)
    fun remove(trackId: UUID) = viewModelScope.launch { downloads.removeOfflineCopy(trackId) }
    fun setOfflineMode(enabled: Boolean) = viewModelScope.launch { settings.setOfflineMode(enabled) }
    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnlyDownloads(enabled) }
    fun setPrefetch(enabled: Boolean) = viewModelScope.launch { settings.setPrefetchEnabled(enabled) }
    fun setPrefetchOnMetered(enabled: Boolean) = viewModelScope.launch { settings.setPrefetchOnMetered(enabled) }
}
