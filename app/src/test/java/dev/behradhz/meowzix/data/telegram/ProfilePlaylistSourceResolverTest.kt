package dev.behradhz.meowzix.data.telegram

import dev.behradhz.meowzix.data.db.TelegramSelectedSourceEntity
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.telegramPlaylistId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilePlaylistSourceResolverTest {
    private val account = "100"
    private fun source(chatId: Long, kind: TelegramChatKind) = TelegramSelectedSourceEntity(
        accountId = account,
        chatId = chatId,
        title = "Person",
        kind = kind.name,
        includeInLibrary = false,
        newestMessageId = null,
        initialScanComplete = false,
        lastSyncedAtEpochMs = null,
    )

    @Test fun privatePlaylistIsDetectedWithoutLoadingSelectableChats() {
        assertEquals(
            23L,
            personChatIdFromSelectedSources(
                account, telegramPlaylistId(account, 23L),
                listOf(source(23L, TelegramChatKind.PRIVATE)),
            ),
        )
    }

    @Test fun savedMessagesAreAValidPersonProfileSource() {
        assertEquals(
            88L,
            personChatIdFromSelectedSources(
                account, telegramPlaylistId(account, 88L),
                listOf(source(88L, TelegramChatKind.SAVED_MESSAGES)),
            ),
        )
    }

    @Test fun groupsAndOtherAccountsAreNotPersonProfiles() {
        assertNull(personChatIdFromSelectedSources(
            account, telegramPlaylistId(account, 11L),
            listOf(source(11L, TelegramChatKind.BASIC_GROUP)),
        ))
        assertNull(personChatIdFromSelectedSources(
            "other", telegramPlaylistId(account, 23L),
            listOf(source(23L, TelegramChatKind.PRIVATE)),
        ))
    }
}
