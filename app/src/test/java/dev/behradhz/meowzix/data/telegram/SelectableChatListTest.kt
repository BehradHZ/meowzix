package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectableChatListTest {
    @Test fun archiveChatsAreIncludedAfterMainAndBeforePersistedChatIds() {
        val result = mergeSelectableTelegramChatIds(
            main = listOf(10, 20),
            archived = listOf(30, 40),
            selected = setOf(50),
            previouslySelected = setOf(60),
        )
        assertEquals(listOf(10L, 20L, 30L, 40L, 50L, 60L), result)
    }

    @Test fun archivedChatAlreadyInMainOrSelectedIsNotDuplicated() {
        val result = mergeSelectableTelegramChatIds(
            main = listOf(10, 20),
            archived = listOf(20, 30, 30),
            selected = setOf(30L, 40L),
            previouslySelected = setOf(10L, 40L, 50L),
        )
        assertEquals(listOf(10L, 20L, 30L, 40L, 50L), result)
    }

    @Test fun archivedOnlyChatIsSelectableWithoutPersistedHistory() {
        assertEquals(listOf(99L), mergeSelectableTelegramChatIds(
            main = emptyList(),
            archived = listOf(99L),
            selected = emptySet(),
            previouslySelected = emptySet(),
        ))
    }
}
