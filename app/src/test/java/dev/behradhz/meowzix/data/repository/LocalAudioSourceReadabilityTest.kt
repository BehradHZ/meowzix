package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioSourceReadabilityTest {
    private fun source(path: String, expectedSize: Long?) = TrackSourceEntity(
        id = "source", trackId = "track", type = TrackSourceType.APP_OFFLINE_COPY,
        availability = SourceAvailability.AVAILABLE_LOCAL,
        contentUri = null, localPath = path, mimeType = "audio/mpeg",
        fileSizeBytes = expectedSize, contentHashSha256 = null, trainingEligible = true,
        createdAtEpochMs = 0, lastVerifiedAtEpochMs = null,
    )

    @Test
    fun `missing and empty managed copies are not playable`() {
        val missing = File.createTempFile("meowzix-missing", ".mp3")
        try {
            assertTrue(missing.delete())
            assertFalse(source(missing.absolutePath, 50L).isReadableForPlayback())
            val empty = File.createTempFile("meowzix-empty", ".mp3")
            try {
                assertFalse(source(empty.absolutePath, null).isReadableForPlayback())
            } finally {
                empty.delete()
            }
        } finally {
            missing.delete()
        }
    }

    @Test
    fun `truncated files are rejected while complete copies remain playable`() {
        val file = File.createTempFile("meowzix-audio", ".mp3")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            assertFalse(source(file.absolutePath, 8L).isReadableForPlayback())
            assertTrue(source(file.absolutePath, 4L).isReadableForPlayback())
            assertTrue(source(file.absolutePath, null).isReadableForPlayback())
        } finally {
            file.delete()
        }
    }
}
