package dev.behradhz.meowzix.feature.telegram

import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramForwardFolderTest {
    private fun chat(id: Long, name: String) =
        TelegramChatSummary(id, name, TelegramChatKind.PRIVATE, selected = false)

    @Test fun allChatsKeepGlobalSearchAndFolderShowsItsOwnMembership() {
        val global = listOf(chat(1, "Mina"), chat(2, "Arman"))
        val folder = TelegramForwardFolder(10, "Friends", listOf(chat(3, "Parisa"), global[0]))
        assertEquals(global, visibleForwardChats(global, null, ""))
        assertEquals(listOf(3L, 1L), visibleForwardChats(global, folder, "").map { it.chatId })
        assertEquals(listOf(3L), visibleForwardChats(global, folder, "pari").map { it.chatId })
        assertEquals(emptyList<Long>(), visibleForwardChats(global, folder, "Arman").map { it.chatId })
    }
}
