package dev.behradhz.meowzix.feature.telegram

import dev.behradhz.meowzix.domain.telegram.TelegramSendJob
import dev.behradhz.meowzix.domain.telegram.TelegramSendState
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardSendVisibilityTest {
    private val song = UUID.randomUUID()
    private val now = 10_000L
    private fun send(state: TelegramSendState, updated: Long = now) = TelegramSendJob(
        id = UUID.randomUUID(),
        trackId = song,
        targetChatId = 42L,
        targetTitle = "Test chat",
        state = state,
        createdAtEpochMs = 0,
        updatedAtEpochMs = updated,
    )

    @Test fun canceledJobsNeverReturnAfterReopeningTheSheet() {
        assertFalse(isForwardStatusVisible(send(TelegramSendState.CANCELED), song, now))
        assertFalse(isForwardStatusVisible(send(TelegramSendState.CANCELED, 1), song, now + 86_400_000))
    }

    @Test fun failedAndUncertainJobsRemainUntilExplicitlyDismissed() {
        assertTrue(isForwardStatusVisible(send(TelegramSendState.FAILED), song, now))
        assertTrue(isForwardStatusVisible(send(TelegramSendState.VERIFYING), song, now))
        assertTrue(isForwardStatusVisible(send(TelegramSendState.CANCELING), song, now))
    }

    @Test fun sentStatusExpiresButActiveUploadRemainsVisible() {
        assertTrue(isForwardStatusVisible(send(TelegramSendState.SENT, now - 2_000), song, now))
        assertFalse(isForwardStatusVisible(send(TelegramSendState.SENT, now - 3_001), song, now))
        assertTrue(isForwardStatusVisible(send(TelegramSendState.UPLOADING, 1), song, now))
        assertFalse(isForwardStatusVisible(send(TelegramSendState.FAILED), UUID.randomUUID(), now))
    }
}
