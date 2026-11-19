package dev.behradhz.meowzix.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylDiameterTest {
    @Test fun oneCellDiscFitsWithoutOverflow() {
        assertEquals(42f, vinylDiameter(50f, 50f), .01f)
    }

    @Test fun rectangularHostKeepsDiscSquareAndCentered() {
        assertEquals(208f, vinylDiameter(216f, 292f), .01f)
        assertEquals(208f, vinylDiameter(292f, 216f), .01f)
    }

    @Test fun tooSmallHostStillHasAUsableTouchTarget() {
        assertTrue(vinylDiameter(39f, 39f) >= 32f)
    }
}
