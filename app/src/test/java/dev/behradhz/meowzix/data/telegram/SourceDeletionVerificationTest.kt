package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDeletionVerificationTest {
    @Test fun onlyAnExplicitNotFoundConfirmsTheMessageIsGone() {
        assertTrue(isConfirmedMissingTelegramMessage(404))
        listOf(400, 401, 403, 408, 420, 429, 500, 503).forEach {
            assertFalse("Code $it must preserve the queue", isConfirmedMissingTelegramMessage(it))
        }
    }
}
