package dev.behradhz.meowzix.feature.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardAutoDismissPolicyTest {
    @Test fun idleFirstAcceptedSendCanAutoDismiss() {
        assertTrue(shouldAutoDismissAfterFirstForward(1, touchedSinceFirst = false))
    }

    @Test fun noAcceptedSendOrDuplicateDoesNotAutoDismiss() {
        assertFalse(shouldAutoDismissAfterFirstForward(0, touchedSinceFirst = false))
        assertFalse(shouldAutoDismissAfterFirstForward(2, touchedSinceFirst = false))
    }

    @Test fun touchAfterFirstSendKeepsPanelOpen() {
        assertFalse(shouldAutoDismissAfterFirstForward(1, touchedSinceFirst = true))
        assertFalse(shouldAutoDismissAfterFirstForward(3, touchedSinceFirst = true))
    }
}
