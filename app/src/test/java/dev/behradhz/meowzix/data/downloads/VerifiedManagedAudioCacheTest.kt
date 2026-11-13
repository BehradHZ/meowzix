package dev.behradhz.meowzix.data.downloads

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedManagedAudioCacheTest {
    @Test fun repeatedPlaybackReusesPersistedVerification() {
        val file = Files.createTempFile("meowzix-cache", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val sha = ManagedAudioIntegrity.sha256(file)
            val stamps = mutableMapOf<String, String>()
            var hashes = 0
            fun verifier() = VerifiedManagedAudioCache(stamps::get, { k, v -> stamps[k] = v }, stamps::remove) {
                hashes++
                ManagedAudioIntegrity.sha256(it)
            }
            assertTrue(verifier().isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
            assertTrue(verifier().isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
            assertEquals(1, hashes)
        } finally { file.delete() }
    }

    @Test fun modifiedFileWithSameLengthMustBeHashedAgain() {
        val file = Files.createTempFile("meowzix-corrupt", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val sha = ManagedAudioIntegrity.sha256(file)
            val stamps = mutableMapOf<String, String>()
            val verifier = VerifiedManagedAudioCache(stamps::get, { k, v -> stamps[k] = v }, stamps::remove)
            assertTrue(verifier.isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
            val modified = file.lastModified()
            file.writeBytes(byteArrayOf(1, 9, 3, 4))
            assertTrue(file.setLastModified(modified + 2000L))
            assertFalse(verifier.isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
        } finally { file.delete() }
    }

    @Test fun changedExpectedHashAndRemoteSizeInvalidateCacheHit() {
        val file = Files.createTempFile("meowzix-revision", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val sha = ManagedAudioIntegrity.sha256(file)
            val stamps = mutableMapOf<String, String>()
            var hashes = 0
            val verifier = VerifiedManagedAudioCache(stamps::get, { k, v -> stamps[k] = v }, stamps::remove) {
                hashes++
                ManagedAudioIntegrity.sha256(it)
            }
            assertTrue(verifier.isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
            assertFalse(verifier.isValidForAnyRemoteSize(file, listOf(7L), 4L, sha))
            assertEquals(1, hashes)
            assertFalse(verifier.isValidForAnyRemoteSize(file, listOf(4L), 4L, "f".repeat(64)))
            assertEquals(2, hashes)
        } finally { file.delete() }
    }

    @Test fun verifiedDownloadCanBeRememberedAndExplicitlyInvalidated() {
        val file = Files.createTempFile("meowzix-new-download", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val sha = ManagedAudioIntegrity.sha256(file)
            val stamps = mutableMapOf<String, String>()
            var hashes = 0
            fun verifier() = VerifiedManagedAudioCache(stamps::get, { k, v -> stamps[k] = v }, stamps::remove) {
                hashes++
                ManagedAudioIntegrity.sha256(it)
            }
            verifier().rememberVerified(file, sha)
            assertTrue(verifier().isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
            assertEquals(0, hashes)
            verifier().forget(file)
            assertTrue(verifier().isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
            assertEquals(1, hashes)
        } finally { file.delete() }
    }

    @Test fun replacedFileWithSameSizeAndTimestampIsReverified() {
        val file = Files.createTempFile("meowzix-replaced", ".mp3").toFile()
        val replacement = Files.createTempFile("meowzix-replacement", ".mp3").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val sha = ManagedAudioIntegrity.sha256(file)
            val stamps = mutableMapOf<String, String>()
            val verifier = VerifiedManagedAudioCache(stamps::get, { k, v -> stamps[k] = v }, stamps::remove)
            assertTrue(verifier.isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
            val oldTime = Files.getLastModifiedTime(file.toPath())
            replacement.writeBytes(byteArrayOf(1, 9, 3, 4))
            Files.setLastModifiedTime(replacement.toPath(), oldTime)
            Files.move(replacement.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            assertFalse(verifier.isValidForAnyRemoteSize(file, listOf(4L), 4L, sha))
        } finally {
            file.delete()
            replacement.delete()
        }
    }
}
