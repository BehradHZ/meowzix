package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMusicIdentityTest {
    @Test fun profileAudioIdentityIsStableNegativeAndDistinct() {
        val first = telegramProfileMessageId("remote-file-1")
        assertTrue(first < 0L)
        assertEquals(first, telegramProfileMessageId("remote-file-1"))
        assertNotEquals(first, telegramProfileMessageId("remote-file-2"))
    }
}
