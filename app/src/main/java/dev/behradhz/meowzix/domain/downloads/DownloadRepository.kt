package dev.behradhz.meowzix.domain.downloads

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED }

data class DownloadDisplayRecord(
    val download: OfflineDownload,
    val title: String,
    val artworkRef: String?,
)

data class OfflineDownload(
    val trackId: UUID,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val pinned: Boolean,
    val failureReason: String?,
    val updatedAtEpochMs: Long = 0L,
)

interface DownloadRepository {
    fun observeDownloads(): Flow<List<OfflineDownload>>
    fun observeDownloadDisplayRecords(): Flow<List<DownloadDisplayRecord>> =
        observeDownloads().map { downloads ->
            downloads.map { DownloadDisplayRecord(it, "Unknown track", null) }
        }
    fun pinOffline(trackId: UUID)
    fun pause(trackId: UUID)
    fun retry(trackId: UUID)
    fun cancel(trackId: UUID)
    suspend fun removeOfflineCopy(trackId: UUID)
    suspend fun storageBytes(): Long
    suspend fun clearTemporaryCache()
}
