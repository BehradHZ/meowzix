package dev.behradhz.meowzix.feature.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardPickerVisibilityGateTest {
    @Test fun initialClosedEmissionMustNotDismissBeforePickerIsOpened() {
        val gate = ForwardPickerVisibilityGate()
        assertFalse(gate.shouldFinish(false))
        assertFalse(gate.shouldFinish(false))
        assertFalse(gate.hasShownPicker)
        assertFalse(gate.shouldFinish(true))
        assertTrue(gate.hasShownPicker)
        assertFalse(gate.shouldFinish(true))
        assertTrue(gate.shouldFinish(false))
    }

    @Test fun aNewLaunchHasItsOwnVisibilityGate() {
        val previous = ForwardPickerVisibilityGate()
        previous.shouldFinish(true)
        assertTrue(previous.shouldFinish(false))
        val next = ForwardPickerVisibilityGate()
        assertFalse(next.shouldFinish(false))
    }
}
