package dev.behradhz.meowzix.domain.settings

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPlaylistSectionTest {
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test fun collapsedProfileDoesNotCollapseTheChatSectionOrOtherPlaylists() {
        val collapsed = setOf(telegramPlaylistSectionKey(first, TelegramPlaylistSection.PROFILE_MUSIC))
        assertFalse(isTelegramPlaylistSectionExpanded(collapsed, first, TelegramPlaylistSection.PROFILE_MUSIC))
        assertTrue(isTelegramPlaylistSectionExpanded(collapsed, first, TelegramPlaylistSection.SHARED_IN_CHAT))
        assertTrue(isTelegramPlaylistSectionExpanded(collapsed, second, TelegramPlaylistSection.PROFILE_MUSIC))
        assertTrue(isTelegramPlaylistSectionExpanded(emptySet(), first, TelegramPlaylistSection.PROFILE_MUSIC))
    }
}
