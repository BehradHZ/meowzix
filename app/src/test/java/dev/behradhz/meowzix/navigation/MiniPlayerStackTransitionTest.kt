package dev.behradhz.meowzix.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniPlayerStackTransitionTest {
    @Test
    fun nextCardMovesFromRightToCoverCurrent() {
        assertEquals(1f, miniPlayerForegroundOffsetFraction(MiniCardDirection.NEXT, 0f), 0.0001f)
        assertEquals(0.5f, miniPlayerForegroundOffsetFraction(MiniCardDirection.NEXT, 0.5f), 0.0001f)
        assertEquals(0f, miniPlayerForegroundOffsetFraction(MiniCardDirection.NEXT, 1f), 0.0001f)
    }

    @Test
    fun previousRevealsStationaryCardAsCurrentMovesRight() {
        assertEquals(0f, miniPlayerForegroundOffsetFraction(MiniCardDirection.PREVIOUS, 0f), 0.0001f)
        assertEquals(0.5f, miniPlayerForegroundOffsetFraction(MiniCardDirection.PREVIOUS, 0.5f), 0.0001f)
        assertEquals(1f, miniPlayerForegroundOffsetFraction(MiniCardDirection.PREVIOUS, 1f), 0.0001f)
    }

    @Test
    fun gestureProgressIsClampedToVisibleBounds() {
        assertEquals(1f, miniPlayerForegroundOffsetFraction(MiniCardDirection.NEXT, -1f), 0.0001f)
        assertEquals(0f, miniPlayerForegroundOffsetFraction(MiniCardDirection.NEXT, 2f), 0.0001f)
        assertEquals(0f, miniPlayerForegroundOffsetFraction(MiniCardDirection.PREVIOUS, -1f), 0.0001f)
        assertEquals(1f, miniPlayerForegroundOffsetFraction(MiniCardDirection.PREVIOUS, 2f), 0.0001f)
    }
}
