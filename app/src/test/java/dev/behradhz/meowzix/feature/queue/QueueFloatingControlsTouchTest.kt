package dev.behradhz.meowzix.feature.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueFloatingControlsTouchTest {
    @Test fun fullyExposedRowRemainsInteractive() {
        assertFalse(queueRowBlockedByFloatingControls(979f, 1000f, 16f))
        assertFalse(queueRowBlockedByFloatingControls(984f, 1000f, 16f))
    }

    @Test fun rowTouchingProtectedAreaIsBlockedEvenWhenTopIsVisible() {
        assertTrue(queueRowBlockedByFloatingControls(985f, 1000f, 16f))
        assertTrue(queueRowBlockedByFloatingControls(1050f, 1000f, 16f))
    }

    @Test fun layoutUnknownInitiallyDisablesClickUntilMeasured() {
        assertTrue(queueRowBlockedByFloatingControls(Float.NaN, 1000f, 16f))
        assertFalse(queueRowBlockedByFloatingControls(1050f, Float.POSITIVE_INFINITY, 16f))
    }

    @Test fun compactDockUsesActualProtectedTop() {
        assertFalse(queueRowBlockedByFloatingControls(1080f, 1100f, 16f))
        assertTrue(queueRowBlockedByFloatingControls(1085f, 1100f, 16f))
    }
}
