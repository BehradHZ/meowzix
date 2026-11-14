package dev.behradhz.meowzix.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StagedSwipeRevealTest {
    private val removeStarts = 118f
    private val transitionDistance = 72f

    @Test fun primaryActionStaysExposedDuringShortPull() {
        assertEquals(0f, stagedActionSlidePx(75f, removeStarts, transitionDistance), 0.001f)
        assertEquals(0f, stagedActionSlidePx(-110f, removeStarts, transitionDistance), 0.001f)
    }

    @Test fun primaryPanelSlidesUnderSongAndRemoveReplacesItGradually() {
        val offset = 154f
        val slide = stagedActionSlidePx(offset, removeStarts, transitionDistance)
        assertEquals(77f, slide, 0.001f)
        assertTrue(slide > 0f && slide < offset)
        // At the midpoint the primary and secondary gutter have equal exposed widths.
        assertEquals(slide, offset - slide, 0.001f)
        assertEquals(-slide, stagedActionSlidePx(-offset, removeStarts, transitionDistance), 0.001f)
    }

    @Test fun primaryPanelIsCompletelyConcealedAtRemoveThreshold() {
        assertEquals(190f, stagedActionSlidePx(190f, removeStarts, transitionDistance), 0.001f)
        assertEquals(-190f, stagedActionSlidePx(-190f, removeStarts, transitionDistance), 0.001f)
        assertEquals(240f, stagedActionSlidePx(240f, removeStarts, transitionDistance), 0.001f)
    }

    @Test fun foregroundRemainsSeventyPercentTransparent() {
        assertEquals(0.30f, TRACK_CARD_ALPHA, 0.001f)
    }
}
