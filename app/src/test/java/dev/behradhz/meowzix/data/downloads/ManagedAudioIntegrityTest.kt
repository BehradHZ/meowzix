package dev.behradhz.meowzix.data.downloads

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedAudioIntegrityTest {
    @Test fun rejectsMissingEmptyAndTruncatedFiles() {
        val directory = Files.createTempDirectory("meowzix-integrity").toFile()
        try {
            assertFalse(ManagedAudioIntegrity.isValid(File(directory, "missing.mp3"), 4L))
            val audio = File(directory, "song.mp3")
            audio.writeBytes(byteArrayOf())
            assertFalse(ManagedAudioIntegrity.isValid(audio))
            audio.writeBytes(byteArrayOf(1, 2, 3))
            assertFalse(ManagedAudioIntegrity.isValid(audio, 4L))
            assertTrue(ManagedAudioIntegrity.isValid(audio, 3L))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun detectsSameLengthCorruptionUsingPersistedDigest() {
        val file = Files.createTempFile("meowzix-audio", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val originalHash = ManagedAudioIntegrity.sha256(file)
            assertTrue(ManagedAudioIntegrity.isValid(file, 4L, originalHash))
            file.writeBytes(byteArrayOf(1, 9, 3, 4))
            assertFalse(ManagedAudioIntegrity.isValid(file, 4L, originalHash))
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsInvalidDigestAndPrefersFreshSize() {
        val file = Files.createTempFile("meowzix-audio", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2))
            assertFalse(ManagedAudioIntegrity.isValid(file, 2L, "bad-hash"))
            assertEquals(8L, ManagedAudioIntegrity.expectedBytes(null, 8L, 2L))
            assertEquals(12L, ManagedAudioIntegrity.expectedBytes(12L, 8L))
        } finally {
            file.delete()
        }
    }
}
