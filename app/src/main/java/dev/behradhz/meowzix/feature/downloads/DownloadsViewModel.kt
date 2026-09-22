package dev.behradhz.meowzix.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

data class ChatDownloadProgress(
    val completedTracks: Int,
    val totalTracks: Int,
) {
    val fraction: Float
        get() = if (totalTracks <= 0) 0f else {
            (completedTracks.toFloat() / totalTracks.toFloat()).coerceIn(0f, 1f)
        }
}

private data class ChatDownloadSession(
    val token: UUID,
    val allTrackIds: Set<UUID>,
    val pendingTrackIds: Set<UUID>,
    val ownedTrackIds: Set<UUID>,
    val baselineUpdatedAtEpochMs: Map<UUID, Long>,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    library: MusicLibraryRepository,
    private val settings: SettingsRepository,
    private val telegram: TelegramRepository,
) : ViewModel() {
    private val bulkDownloadMutex = Mutex()
    private val bulkDownloadSessions = MutableStateFlow<Map<Long, ChatDownloadSession>>(emptyMap())
    private val bulkMonitorJobs = mutableMapOf<Long, Job>()

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

    val chatDownloadProgress = combine(
        bulkDownloadSessions,
        downloads.observeDownloads(),
    ) { sessions, records ->
        sessions.mapValues { (_, session) ->
            calculateChatDownloadProgress(session.allTrackIds, records)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val networkSettings = settings.networkPlaybackSettings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings(),
        )

    fun toggleDownloadAll(chatId: Long) = viewModelScope.launch {
        bulkDownloadMutex.withLock {
            val activeSession = bulkDownloadSessions.value[chatId]
            if (activeSession != null) {
                stopBulkDownload(chatId, activeSession)
                return@withLock
            }

            val allTrackIds = telegram.trackIdsForChat(chatId).toSet()
            if (allTrackIds.isEmpty()) return@withLock

            val currentRecords = downloads.observeDownloads().first()
            val recordsByTrack = currentRecords.associateBy(OfflineDownload::trackId)
            val pendingTrackIds = allTrackIds.filterTo(mutableSetOf()) { trackId ->
                recordsByTrack[trackId]?.status != DownloadStatus.COMPLETED
            }
            if (pendingTrackIds.isEmpty()) return@withLock

            val alreadyActiveTrackIds = pendingTrackIds.filterTo(mutableSetOf()) { trackId ->
                recordsByTrack[trackId]?.status.isActiveDownload()
            }
            val ownedTrackIds = pendingTrackIds - alreadyActiveTrackIds
            val session = ChatDownloadSession(
                token = UUID.randomUUID(),
                allTrackIds = allTrackIds,
                pendingTrackIds = pendingTrackIds,
                ownedTrackIds = ownedTrackIds,
                baselineUpdatedAtEpochMs = pendingTrackIds.associateWith { trackId ->
                    recordsByTrack[trackId]?.updatedAtEpochMs ?: 0L
                },
            )

            bulkDownloadSessions.value = bulkDownloadSessions.value + (chatId to session)
            ownedTrackIds.forEach(downloads::pinOffline)

            bulkMonitorJobs.remove(chatId)?.cancel()
            bulkMonitorJobs[chatId] = viewModelScope.launch {
                monitorBulkDownload(chatId, session)
            }
        }
    }

    fun retry(trackId: UUID) = downloads.retry(trackId)
    fun cancel(trackId: UUID) = downloads.cancel(trackId)
    fun remove(trackId: UUID) = viewModelScope.launch { downloads.removeOfflineCopy(trackId) }
    fun setOfflineMode(enabled: Boolean) = viewModelScope.launch { settings.setOfflineMode(enabled) }
    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnlyDownloads(enabled) }
    fun setPrefetch(enabled: Boolean) = viewModelScope.launch { settings.setPrefetchEnabled(enabled) }
    fun setPrefetchOnMetered(enabled: Boolean) = viewModelScope.launch { settings.setPrefetchOnMetered(enabled) }

    private fun stopBulkDownload(chatId: Long, session: ChatDownloadSession) {
        bulkMonitorJobs.remove(chatId)?.cancel()
        bulkDownloadSessions.value = bulkDownloadSessions.value - chatId
        session.ownedTrackIds.forEach(downloads::cancel)
    }

    private suspend fun monitorBulkDownload(chatId: Long, session: ChatDownloadSession) {
        downloads.observeDownloads()
            .map { records -> records.associateBy(OfflineDownload::trackId) }
            .first { recordsByTrack ->
                session.pendingTrackIds.all { trackId ->
                    val record = recordsByTrack[trackId] ?: return@all false
                    val baseline = session.baselineUpdatedAtEpochMs[trackId] ?: 0L
                    record.updatedAtEpochMs > baseline && record.status.isTerminalDownload()
                }
            }

        bulkDownloadMutex.withLock {
            if (bulkDownloadSessions.value[chatId]?.token == session.token) {
                bulkDownloadSessions.value = bulkDownloadSessions.value - chatId
                bulkMonitorJobs.remove(chatId)
            }
        }
    }
}

internal fun calculateChatDownloadProgress(
    trackIds: Set<UUID>,
    records: List<OfflineDownload>,
): ChatDownloadProgress {
    val recordsByTrack = records.associateBy(OfflineDownload::trackId)
    val completed = trackIds.count { trackId ->
        recordsByTrack[trackId]?.status == DownloadStatus.COMPLETED
    }
    return ChatDownloadProgress(
        completedTracks = completed,
        totalTracks = trackIds.size,
    )
}

private fun DownloadStatus?.isActiveDownload(): Boolean =
    this == DownloadStatus.QUEUED || this == DownloadStatus.DOWNLOADING

private fun DownloadStatus.isTerminalDownload(): Boolean = when (this) {
    DownloadStatus.COMPLETED,
    DownloadStatus.FAILED,
    DownloadStatus.CANCELED,
    -> true
    DownloadStatus.QUEUED,
    DownloadStatus.DOWNLOADING,
    -> false
}
