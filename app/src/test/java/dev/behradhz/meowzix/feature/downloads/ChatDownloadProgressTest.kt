package dev.behradhz.meowzix.feature.downloads

import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDownloadProgressTest {
    @Test
    fun `progress counts completed tracks instead of byte progress`() {
        val completedA = UUID.randomUUID()
        val completedB = UUID.randomUUID()
        val downloading = UUID.randomUUID()
        val failed = UUID.randomUUID()
        val trackIds = setOf(completedA, completedB, downloading, failed)
        val records = listOf(
            record(completedA, DownloadStatus.COMPLETED),
            record(completedB, DownloadStatus.COMPLETED),
            record(downloading, DownloadStatus.DOWNLOADING),
            record(failed, DownloadStatus.FAILED),
        )

        val progress = calculateChatDownloadProgress(trackIds, records)

        assertEquals(2, progress.completedTracks)
        assertEquals(4, progress.totalTracks)
        assertEquals(0.5f, progress.fraction, 0.0001f)
    }

    @Test
    fun `progress includes tracks already available offline`() {
        val alreadyOffline = UUID.randomUUID()
        val newDownload = UUID.randomUUID()

        val progress = calculateChatDownloadProgress(
            trackIds = setOf(alreadyOffline, newDownload),
            records = listOf(record(alreadyOffline, DownloadStatus.COMPLETED)),
        )

        assertEquals(1, progress.completedTracks)
        assertEquals(2, progress.totalTracks)
        assertEquals(0.5f, progress.fraction, 0.0001f)
    }

    private fun record(trackId: UUID, status: DownloadStatus) = OfflineDownload(
        trackId = trackId,
        status = status,
        downloadedBytes = if (status == DownloadStatus.COMPLETED) 1L else 0L,
        totalBytes = 1L,
        pinned = true,
        failureReason = null,
        updatedAtEpochMs = 1L,
    )
}
