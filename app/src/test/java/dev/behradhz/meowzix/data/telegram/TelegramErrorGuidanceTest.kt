package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TelegramErrorGuidanceTest {
    @Test fun inputErrorsAreActionableAndMustNotBeRetried() {
        val code = telegramErrorGuidance(TdLibException(400, "PHONE_CODE_INVALID"))
        assertTrue(code.message.contains("verification code"))
        assertFalse(code.retryable)

        val wait = telegramErrorGuidance(TdLibException(429, "FLOOD_WAIT_125"))
        assertTrue(wait.message.contains("3 minute"))
        assertFalse(wait.retryable)

        val privacy = telegramErrorGuidance(TdLibException(400, "opaque token=mySecret"))
        assertFalse(privacy.message.contains("mySecret"))
    }

    @Test fun transientReadRetriesOnlyUntilSuccess() = runTest {
        var attempts = 0
        val delays = mutableListOf<Long>()
        val result = retryTransientTelegramRead(
            wait = { delays += it },
        ) {
            attempts += 1
            if (attempts < 3) throw TdLibException(500, "server unavailable")
            "chats loaded"
        }
        assertEquals("chats loaded", result)
        assertEquals(3, attempts)
        assertEquals(listOf(750L, 1_500L), delays)
    }

    @Test fun authenticationErrorsAreNotRepeated() = runTest {
        var attempts = 0
        try {
            retryTransientTelegramRead(attempts = 3, wait = {}) {
                attempts += 1
                throw TdLibException(400, "PHONE_CODE_INVALID")
            }
            fail("Verification requests cannot be retried automatically")
        } catch (_: TdLibException) {
            assertEquals(1, attempts)
        }
    }

    @Test fun cancellationIsNeverRetried() = runTest {
        var attempts = 0
        try {
            retryTransientTelegramRead(wait = {}) {
                attempts += 1
                throw CancellationException("screen closed")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, attempts)
        }
    }
}
