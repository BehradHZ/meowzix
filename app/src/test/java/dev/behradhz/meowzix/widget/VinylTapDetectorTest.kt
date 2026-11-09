package dev.behradhz.meowzix.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylTapDetectorTest {
    @Test fun onlyTwoFastTapsOnTheSameWidgetAndZoneTrigger() {
        val detector = VinylTapDetector(450L)
        assertFalse(detector.register(1, "LEFT", 1000L))
        assertTrue(detector.register(1, "LEFT", 1300L))
        assertFalse(detector.register(1, "LEFT", 1400L))
        assertFalse(detector.register(1, "LEFT", 2000L))
        assertFalse(detector.register(1, "LEFT", 2600L))
    }

    @Test fun zoneAndWidgetChangesCancelThePendingTap() {
        val detector = VinylTapDetector()
        assertFalse(detector.register(1, "LEFT", 1000L))
        assertFalse(detector.register(1, "RIGHT", 1100L))
        assertFalse(detector.register(2, "RIGHT", 1200L))
        assertTrue(detector.register(2, "RIGHT", 1400L))
    }

    @Test fun clockResetCannotTriggerAnAccidentalAction() {
        val detector = VinylTapDetector()
        assertFalse(detector.register(1, "TOP", 1000L))
        assertFalse(detector.register(1, "TOP", 900L))
    }
}
