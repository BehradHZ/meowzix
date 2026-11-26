package dev.behradhz.meowzix.data.telegram

import dev.behradhz.meowzix.data.db.TelegramSelectedSourceEntity
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.telegramPlaylistId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramManualRefreshPolicyTest {
    @Test fun manualRefreshBypassesCheckpointAndOnlyPrunesAfterFullHistory() {
        assertEquals(100L, telegramSyncCheckpoint(100L, true, false))
        assertNull(telegramSyncCheckpoint(100L, true, true))
        assertNull(telegramSyncCheckpoint(100L, false, false))
        assertFalse(shouldReconcileTelegramMissing(true, true, false))
        assertFalse(shouldReconcileTelegramMissing(false, false, false))
        assertFalse(shouldReconcileTelegramMissing(true, false, true))
        assertTrue(shouldReconcileTelegramMissing(true, true, true))
        assertTrue(shouldReconcileTelegramMissing(false, false, true))
    }

    @Test fun interruptedInitialIndexDoesNotClaimToHaveReadAllHistory() {
        assertFalse(telegramInitialScanComplete(false, false))
        assertTrue(telegramInitialScanComplete(false, true))
        assertTrue(telegramInitialScanComplete(true, false))
    }

    @Test fun resolvesOnlyTheSelectedChatForItsOwnAccountAndPlaylist() {
        fun source(account: String, chatId: Long, kind: TelegramChatKind) =
            TelegramSelectedSourceEntity(
                accountId = account, chatId = chatId, title = "Music", kind = kind.name,
                includeInLibrary = true, newestMessageId = 100L,
                initialScanComplete = true, lastSyncedAtEpochMs = null,
            )
        val sources = listOf(
            source("alice", 10L, TelegramChatKind.SUPERGROUP_OR_CHANNEL),
            source("alice", 20L, TelegramChatKind.PRIVATE),
            source("bob", 30L, TelegramChatKind.SAVED_MESSAGES),
        )
        assertEquals(10L, chatIdFromSelectedSources("alice", telegramPlaylistId("alice", 10L), sources))
        assertEquals(20L, chatIdFromSelectedSources("alice", telegramPlaylistId("alice", 20L), sources))
        assertNull(chatIdFromSelectedSources("alice", telegramPlaylistId("bob", 30L), sources))
        assertNull(chatIdFromSelectedSources("alice", telegramPlaylistId("alice", 40L), sources))
    }
}
