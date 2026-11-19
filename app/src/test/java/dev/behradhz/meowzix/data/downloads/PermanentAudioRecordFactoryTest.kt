package dev.behradhz.meowzix.data.downloads

import dev.behradhz.meowzix.data.db.DownloadRecordEntity
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PermanentAudioRecordFactoryTest {
    @Test fun completedPlaybackCopyIsPersistedAsPinned() {
        val file = Files.createTempFile("meowzix-playback", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val record = PermanentAudioRecordFactory.completed("song", "local", 42, file, 100L, null)
            assertTrue(record.pinned)
            assertEquals("COMPLETED", record.status)
            assertEquals(file.absolutePath, record.localPath)
            assertEquals(3L, record.downloadedBytes)
            assertEquals(3L, record.totalBytes)
        } finally {
            file.delete()
        }
    }

    @Test fun legacyCopyPromotionPreservesExistingRecordIdentity() {
        val file = Files.createTempFile("meowzix-legacy", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(7, 8))
            val old = DownloadRecordEntity(
                id = "same-id",
                trackId = "song",
                trackSourceId = "remote",
                tdFileId = 77,
                status = "FAILED",
                downloadedBytes = 0L,
                totalBytes = null,
                localPath = null,
                pinned = false,
                failureReason = "old failure",
                createdAtEpochMs = 10L,
                updatedAtEpochMs = 20L,
            )
            val updated = PermanentAudioRecordFactory.completed("song", "local", null, file, 30L, old)
            assertEquals("same-id", updated.id)
            assertEquals(10L, updated.createdAtEpochMs)
            assertEquals(77, updated.tdFileId)
            assertEquals("COMPLETED", updated.status)
            assertTrue(updated.pinned)
            assertEquals(null, updated.failureReason)
        } finally {
            file.delete()
        }
    }

    @Test fun incompleteAudioCannotBeMarkedAsCompleted() {
        val file = Files.createTempFile("meowzix-empty", ".mp3").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PermanentAudioRecordFactory.completed("song", "source", null, file, 1L, null)
            }
        } finally {
            file.delete()
        }
    }
}
