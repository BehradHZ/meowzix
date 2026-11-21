package dev.behradhz.meowzix.feature.telegramauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramAuthBackNavigationTest {
    @Test fun `code goes back to phone then exits`() {
        assertEquals(0, telegramPreviousStepIndex(-1, 1))
        assertNull(telegramPreviousStepIndex(0, 1))
    }

    @Test fun `each prior step is visited in reverse order`() {
        assertEquals(2, telegramPreviousStepIndex(-1, 3))
        assertEquals(1, telegramPreviousStepIndex(2, 3))
        assertEquals(0, telegramPreviousStepIndex(1, 3))
        assertNull(telegramPreviousStepIndex(0, 3))
    }

    @Test fun `first screen goes back to app without phantom steps`() {
        assertNull(telegramPreviousStepIndex(-1, 0))
    }
}
