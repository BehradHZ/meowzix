package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TelegramForwardSourceFallbackTest {
    @Test fun syntheticProfileSongUploadsAudioWithoutCallingGetMessage() = runTest {
        var forwardCalls = 0
        var uploadCalls = 0
        val outcome = sendWithTelegramSourceFallback(
            sourceMessageId = -1234L,
            forwardMessage = { forwardCalls++; "forwarded" },
            sendAudioCopy = { uploadCalls++; "uploaded" },
        )
        assertEquals("uploaded", outcome)
        assertEquals(0, forwardCalls)
        assertEquals(1, uploadCalls)
    }

    @Test fun ordinaryTelegramMessagePreservesForwardAndSenderAttribution() = runTest {
        var uploads = 0
        val outcome = sendWithTelegramSourceFallback(
            sourceMessageId = 54321L,
            forwardMessage = { "forwarded" },
            sendAudioCopy = { uploads++; "uploaded" },
        )
        assertEquals("forwarded", outcome)
        assertEquals(0, uploads)
    }

    @Test fun missingOriginalPreflightUsesSavedAudioOnlyOnce() = runTest {
        var forwards = 0
        var uploads = 0
        val outcome = sendWithTelegramSourceFallback(
            sourceMessageId = 42L,
            forwardMessage = { forwards++; throw OriginalTelegramMessageMissing() },
            sendAudioCopy = { uploads++; "saved-copy" },
        )
        assertEquals("saved-copy", outcome)
        assertEquals(1, forwards)
        assertEquals(1, uploads)
    }

    @Test fun failuresAfterAttemptedSendNeverCauseASecondUpload() = runTest {
        var uploads = 0
        try {
            sendWithTelegramSourceFallback(
                sourceMessageId = 42L,
                forwardMessage = { throw TdLibException(404, "Not found") },
                sendAudioCopy = { uploads++; "uploaded" },
            )
            fail("Post-send errors must propagate instead of silently resending")
        } catch (_: TdLibException) {
            assertEquals(0, uploads)
        }
    }

    @Test fun localTrackWithoutTelegramSourceUploads() = runTest {
        val outcome = sendWithTelegramSourceFallback(
            sourceMessageId = null,
            forwardMessage = { "unexpected" },
            sendAudioCopy = { "uploaded" },
        )
        assertEquals("uploaded", outcome)
    }
}
