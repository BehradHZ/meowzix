package dev.behradhz.meowzix.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockMotionTest {
    @Test
    fun collapseSettlesFasterThanExpansion() {
        assertTrue(DockCollapseDurationMs < DockExpandDurationMs)
        assertTrue(DockCollapseDurationMs <= 260)
    }

    @Test
    fun transientBlurIsZeroAtBothRestingStates() {
        assertEquals(0f, dockMotionBlurDp(0f, false), 0.0001f)
        assertEquals(0f, dockMotionBlurDp(1f, false), 0.0001f)
        assertTrue(dockMotionBlurDp(0.5f, false) > 1f)
    }

    @Test
    fun blurIsSymmetricClampedAndDisabledForReducedMotion() {
        assertEquals(dockMotionBlurDp(0.25f, false), dockMotionBlurDp(0.75f, false), 0.0001f)
        assertEquals(0f, dockMotionBlurDp(-2f, false), 0.0001f)
        assertEquals(0f, dockMotionBlurDp(4f, false), 0.0001f)
        assertEquals(0f, dockMotionBlurDp(0.5f, true), 0.0001f)
    }

    @Test
    fun compactButtonsRemainEqualSizeWithRoomForMiniPlayer() {
        assertEquals(52, CompactDockButtonDp)
        assertTrue(CompactMiniPlayerInsetDp > CompactDockButtonDp)
    }
}
