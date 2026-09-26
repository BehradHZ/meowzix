package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TelegramSendDedupeKeyTest {
    @Test
    fun sameCanonicalTrackAndDestinationProduceSameKey() {
        val first = telegramSendDedupeKey("42", 1001L, "track-a")
        val second = telegramSendDedupeKey("42", 1001L, "track-a")

        assertEquals(first, second)
    }

    @Test
    fun differentDestinationDoesNotDeduplicate() {
        val first = telegramSendDedupeKey("42", 1001L, "track-a")
        val second = telegramSendDedupeKey("42", 1002L, "track-a")

        assertNotEquals(first, second)
    }
}
