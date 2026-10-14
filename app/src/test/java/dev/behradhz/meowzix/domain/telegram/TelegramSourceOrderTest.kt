package dev.behradhz.meowzix.domain.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramSourceOrderTest {
    private fun chat(id: Long, selected: Boolean = false) =
        TelegramChatSummary(id, "Chat $id", TelegramChatKind.PRIVATE, selected)

    @Test fun activeThenPreviouslySelectedThenRecent_preservesRecencyWithinGroups() {
        val chats = listOf(chat(1), chat(2), chat(3, true), chat(4), chat(5, true), chat(6))
        val ordered = prioritizeTelegramSourceChats(chats, setOf(2, 3, 4, 5))
        assertEquals(listOf(3L, 5L, 2L, 4L, 1L, 6L), ordered.map { it.chatId })
    }

    @Test fun emptyHistoryDoesNotReorderRecentChats() {
        val chats = listOf(chat(10), chat(11), chat(12))
        assertEquals(chats, prioritizeTelegramSourceChats(chats, emptySet()))
    }

    @Test fun removingASelectionMovesItIntoHistoryNotToTheBottom() {
        val chats = listOf(chat(1), chat(2), chat(3))
        assertEquals(listOf(2L, 1L, 3L),
            prioritizeTelegramSourceChats(chats, setOf(2L)).map { it.chatId })
    }
}
