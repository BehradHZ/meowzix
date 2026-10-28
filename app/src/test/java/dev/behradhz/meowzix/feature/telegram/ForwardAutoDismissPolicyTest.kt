package dev.behradhz.meowzix.feature.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForwardAutoDismissPolicyTest {
    @Test fun noAcceptedSendNeverArmsAutoDismiss() {
        assertNull(forwardIdleRemainingMillis(0, 0L, 800L, false, 1000L))
    }

    @Test fun firstSendStartsTwoSecondCountdown() {
        assertEquals(
            1_500L,
            forwardIdleRemainingMillis(1, 1_000L, 100L, false, 1_500L),
        )
        assertEquals(
            0L,
            forwardIdleRemainingMillis(1, 1_000L, 100L, false, 3_001L),
        )
    }

    @Test fun newerTouchExtendsCountdownRatherThanDisablingIt() {
        assertEquals(
            1_700L,
            forwardIdleRemainingMillis(1, 1_000L, 1_800L, false, 2_100L),
        )
        assertEquals(
            0L,
            forwardIdleRemainingMillis(1, 1_000L, 1_800L, false, 3_800L),
        )
    }

    @Test fun secondSendCanMoveDeadlinePastEarlierTouch() {
        assertEquals(
            2_000L,
            forwardIdleRemainingMillis(2, 4_000L, 3_000L, false, 4_000L),
        )
    }

    @Test fun fingerHeldOnPanelPreventsDismissUntilRelease() {
        assertNull(forwardIdleRemainingMillis(1, 1_000L, 2_000L, true, 10_000L))
        assertEquals(
            2_000L,
            forwardIdleRemainingMillis(1, 1_000L, 10_000L, false, 10_000L),
        )
    }
}
