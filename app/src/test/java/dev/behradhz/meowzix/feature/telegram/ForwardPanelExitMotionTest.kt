package dev.behradhz.meowzix.feature.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardPanelExitMotionTest {
    @Test fun panelStartsFullyVisibleAndEndsBelowItsInitialPosition() {
        val start = forwardPanelExitFrame(0f)
        val end = forwardPanelExitFrame(1f)
        assertEquals(1f, start.alpha, 0.001f)
        assertEquals(0f, start.translationDp, 0.001f)
        assertEquals(1f, start.scale, 0.001f)
        assertEquals(0f, end.alpha, 0.001f)
        assertEquals(38f, end.translationDp, 0.001f)
        assertTrue(end.scale < start.scale)
    }

    @Test fun middleFrameCrossfadesAndTranslatesContinuously() {
        val half = forwardPanelExitFrame(0.5f)
        assertEquals(0.5f, half.alpha, 0.001f)
        assertEquals(19f, half.translationDp, 0.001f)
        assertTrue(half.scale > 0.97f)
    }

    @Test fun progressIsClampedAndMotionIsBrief() {
        assertEquals(forwardPanelExitFrame(0f), forwardPanelExitFrame(-12f))
        assertEquals(forwardPanelExitFrame(1f), forwardPanelExitFrame(12f))
        assertTrue(ForwardPanelExitDurationMs in 150..280)
    }
}
