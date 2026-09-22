package dev.behradhz.meowzix.domain.downloads

import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELED }

data class OfflineDownload(
    val trackId: UUID,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val pinned: Boolean,
    val failureReason: String?,
)

interface DownloadRepository {
    fun observeDownloads(): Flow<List<OfflineDownload>>
    fun pinOffline(trackId: UUID)
    fun retry(trackId: UUID)
    fun cancel(trackId: UUID)

    /** Reconciles persisted interrupted downloads and resumes them when their provider is ready. */
    fun resumeInterruptedDownloads()

    suspend fun removeOfflineCopy(trackId: UUID)
    suspend fun storageBytes(): Long
    suspend fun clearTemporaryCache()
}
