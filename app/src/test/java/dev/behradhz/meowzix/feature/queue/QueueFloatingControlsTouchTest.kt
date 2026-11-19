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

    @Test fun exposedDragHandleWorksOnPartlyCoveredRowWithoutEnablingItsClicks() {
        // The card overlaps the floating dock, but its 48dp handle is clear.
        assertTrue(queueRowBlockedByFloatingControls(1015f, 1000f, 16f))
        assertTrue(queueDragHandleEnabled(
            allowed = true, isDragging = false,
            handleBottomInWindow = 945f, controlsTopInWindow = 1000f, guardPx = 16f,
        ))
        // A handle actually inside the protected region cannot start a drag.
        assertFalse(queueDragHandleEnabled(
            allowed = true, isDragging = false,
            handleBottomInWindow = 990f, controlsTopInWindow = 1000f, guardPx = 16f,
        ))
        // Once begun, scrolling a row into that region must not cancel its gesture.
        assertTrue(queueDragHandleEnabled(
            allowed = true, isDragging = true,
            handleBottomInWindow = 1050f, controlsTopInWindow = 1000f, guardPx = 16f,
        ))
        assertFalse(queueDragHandleEnabled(
            allowed = false, isDragging = true,
            handleBottomInWindow = 945f, controlsTopInWindow = 1000f, guardPx = 16f,
        ))
    }

    @Test fun compactDockUsesActualProtectedTop() {
        assertFalse(queueRowBlockedByFloatingControls(1080f, 1100f, 16f))
        assertTrue(queueRowBlockedByFloatingControls(1085f, 1100f, 16f))
    }
}
