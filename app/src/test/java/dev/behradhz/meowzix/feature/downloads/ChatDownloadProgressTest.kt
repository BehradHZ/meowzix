package dev.behradhz.meowzix.feature.downloads

import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDownloadProgressTest {
    @Test
    fun `progress uses bytes when every selected track has a known size`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val progress = calculateChatDownloadProgress(
            setOf(first, second),
            listOf(
                record(first, DownloadStatus.DOWNLOADING, downloaded = 50L, total = 100L),
                record(second, DownloadStatus.DOWNLOADING, downloaded = 100L, total = 300L),
            ),
        )

        assertTrue(progress.usesBytes)
        assertEquals(150L, progress.downloadedBytes)
        assertEquals(400L, progress.totalBytes)
        assertEquals(0.375f, progress.fraction, 0.0001f)
    }

    @Test
    fun `progress falls back to completed track count when any size is unknown`() {
        val completed = UUID.randomUUID()
        val queued = UUID.randomUUID()
        val progress = calculateChatDownloadProgress(
            setOf(completed, queued),
            listOf(
                record(completed, DownloadStatus.COMPLETED, downloaded = 100L, total = 100L),
                record(queued, DownloadStatus.QUEUED, downloaded = 0L, total = null),
            ),
        )

        assertFalse(progress.usesBytes)
        assertEquals(1, progress.completedTracks)
        assertEquals(2, progress.totalTracks)
        assertEquals(0.5f, progress.fraction, 0.0001f)
    }

    @Test
    fun `progress falls back to track count while a selected track has no record yet`() {
        val alreadyOffline = UUID.randomUUID()
        val newDownload = UUID.randomUUID()

        val progress = calculateChatDownloadProgress(
            trackIds = setOf(alreadyOffline, newDownload),
            records = listOf(record(alreadyOffline, DownloadStatus.COMPLETED, 1L, 1L)),
        )

        assertFalse(progress.usesBytes)
        assertEquals(1, progress.completedTracks)
        assertEquals(2, progress.totalTracks)
        assertEquals(0.5f, progress.fraction, 0.0001f)
    }

    private fun record(
        trackId: UUID,
        status: DownloadStatus,
        downloaded: Long,
        total: Long?,
    ) = OfflineDownload(
        trackId = trackId,
        status = status,
        downloadedBytes = downloaded,
        totalBytes = total,
        pinned = true,
        failureReason = null,
        updatedAtEpochMs = 1L,
    )
}
