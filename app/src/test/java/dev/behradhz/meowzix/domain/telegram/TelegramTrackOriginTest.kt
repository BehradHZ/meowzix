package dev.behradhz.meowzix.domain.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramTrackOriginTest {
    @Test fun anOriginDefaultsToOneAvailableChat() {
        val origin = TelegramTrackOrigin(chatId = 10L, title = "Saved", profilePhotoRef = null)
        org.junit.Assert.assertEquals(1, origin.availableChatCount)
        org.junit.Assert.assertEquals(3, origin.copy(availableChatCount = 3).availableChatCount)
    }

    @Test fun profileAudioUsesNegativeSyntheticMessageIds() {
        assertTrue(telegramSourceIsProfileMusic(-1L))
        assertTrue(telegramSourceIsProfileMusic(-9_223L))
        assertFalse(telegramSourceIsProfileMusic(0L))
        assertFalse(telegramSourceIsProfileMusic(1L))
        assertFalse(telegramSourceIsProfileMusic(2_000_000L))
    }
}
