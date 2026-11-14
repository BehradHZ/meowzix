package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramMessageDeepLinksTest {
    @Test fun savedMessagesUsesServerMessageId() {
        assertEquals(
            "tg://openmessage?user_id=12345&message_id=42",
            telegramMessageDeepLink(12345L, 42L shl 20),
        )
    }

    @Test fun supergroupUsesMessageLink() {
        assertEquals(
            "https://t.me/c/987654321/42",
            telegramMessageDeepLink(-1_000_987_654_321L, 42L shl 20),
        )
    }

    @Test fun basicGroupUsesAndroidChatDeepLink() {
        assertEquals(
            "tg://openmessage?chat_id=77&message_id=42",
            telegramMessageDeepLink(-77L, 42L shl 20),
        )
    }

    @Test fun profileMusicOpensItsOwnerProfileInsteadOfASyntheticMessage() {
        assertEquals(
            "https://t.me/MusicOwner?profile",
            telegramProfileDeepLink(51234L, "MusicOwner"),
        )
        assertEquals(
            "https://t.me/MusicOwner?profile",
            telegramProfileDeepLink(51234L, "@MusicOwner"),
        )
        assertEquals(
            "tg://openmessage?user_id=51234",
            telegramProfileDeepLink(51234L, null),
        )
        assertEquals(
            "tg://openmessage?user_id=51234",
            telegramProfileDeepLink(51234L, "invalid/name"),
        )
        assertNull(telegramProfileDeepLink(0L, "MusicOwner"))
        assertNull(telegramProfileDeepLink(-3L, null))
        // The synthetic negative IDs must never be interpreted as message IDs.
        assertNull(telegramMessageDeepLink(51234L, -12345L))
    }

    @Test fun invalidIdentifiersDoNotProduceLinks() {
        assertNull(telegramMessageDeepLink(0L, 42L shl 20))
        assertNull(telegramMessageDeepLink(123L, -1L))
        assertNull(telegramMessageDeepLink(123L, 42L))
    }
}
