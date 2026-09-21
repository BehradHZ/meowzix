package dev.behradhz.meowzix.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadRow(
    val trackId: UUID,
    val title: String,
    val status: DownloadStatus,
    val progress: Float?,
    val downloadedBytes: Long,
    val failureReason: String?,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    library: MusicLibraryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val rows = combine(downloads.observeDownloads(), library.observeTracks()) { records, tracks ->
        val titles = tracks.associate { it.id to it.title }
        records.map { record ->
            DownloadRow(
                trackId = record.trackId,
                title = titles[record.trackId] ?: "Unknown track",
                status = record.status,
                progress = record.totalBytes?.takeIf { it > 0L }?.let {
                    (record.downloadedBytes.toFloat() / it).coerceIn(0f, 1f)
                },
                downloadedBytes = record.downloadedBytes,
                failureReason = record.failureReason,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val networkSettings = settings.networkPlaybackSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings())

    fun retry(trackId: UUID) = downloads.retry(trackId)
    fun cancel(trackId: UUID) = downloads.cancel(trackId)
    fun remove(trackId: UUID) = viewModelScope.launch { downloads.removeOfflineCopy(trackId) }
    fun setOfflineMode(enabled: Boolean) = viewModelScope.launch { settings.setOfflineMode(enabled) }
    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnlyDownloads(enabled) }
    fun setPrefetch(enabled: Boolean) = viewModelScope.launch { settings.setPrefetchEnabled(enabled) }
    fun setPrefetchOnMetered(enabled: Boolean) = viewModelScope.launch { settings.setPrefetchOnMetered(enabled) }
}
