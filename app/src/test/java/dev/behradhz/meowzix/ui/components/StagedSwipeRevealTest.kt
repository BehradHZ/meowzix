package dev.behradhz.meowzix.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StagedSwipeRevealTest {
    private val actionThreshold = 100f
    private val removeThreshold = 176f
    private val transitionDistance = removeThreshold - actionThreshold

    @Test fun primaryActionIsFullyExposedUntilTheReadableThreshold() {
        assertEquals(0f, stagedActionSlidePx(75f, actionThreshold, transitionDistance), 0.001f)
        assertEquals(0f, stagedActionSlidePx(-100f, actionThreshold, transitionDistance), 0.001f)
    }

    @Test fun restoreTheOriginalFastExitWithDeterministicLayerGeometry() {
        val first = stagedActionSlidePx(101f, actionThreshold, transitionDistance)
        val middle = stagedActionSlidePx(138f, actionThreshold, transitionDistance)
        val last = stagedActionSlidePx(175f, actionThreshold, transitionDistance)
        assertTrue(first > 0f)
        assertTrue(middle > first)
        assertTrue(last > middle)
        assertTrue(last < 175f)
        assertEquals(-middle, stagedActionSlidePx(-138f, actionThreshold, transitionDistance), 0.001f)
        // The 190ms easing is applied by Compose, with stable target geometry.
        assertEquals(middle, stagedActionSlidePx(138f, actionThreshold, transitionDistance), 0.001f)
        assertEquals(190, SWIPE_ACTION_EXIT_DURATION_MS)
    }

    @Test fun removeIsFullyExposedAtTheDestructiveThreshold() {
        assertEquals(176f, stagedActionSlidePx(176f, actionThreshold, transitionDistance), 0.001f)
        assertEquals(-176f, stagedActionSlidePx(-176f, actionThreshold, transitionDistance), 0.001f)
        assertEquals(240f, stagedActionSlidePx(240f, actionThreshold, transitionDistance), 0.001f)
    }

    @Test fun foregroundKeepsSeventyPercentTransparency() {
        assertEquals(0.30f, TRACK_CARD_ALPHA, 0.001f)
        assertEquals(100f, TRACK_SWIPE_ACTION_THRESHOLD.value, 0.001f)
        assertEquals(176f, TRACK_SWIPE_REMOVE_THRESHOLD.value, 0.001f)
    }
}
