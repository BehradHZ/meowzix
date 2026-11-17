package dev.behradhz.meowzix.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderProgressTest {
    @Test fun clampsOutsideTheRange() {
        assertEquals(0f, normalizedSliderProgress(-10f, 0f..100f), 0f)
        assertEquals(1f, normalizedSliderProgress(120f, 0f..100f), 0f)
    }

    @Test fun handlesNonZeroStartAndDegenerateRange() {
        assertEquals(0.5f, normalizedSliderProgress(15f, 10f..20f), 0f)
        assertEquals(0f, normalizedSliderProgress(5f, 5f..5f), 0f)
    }
}
