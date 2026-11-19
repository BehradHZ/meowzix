package dev.behradhz.meowzix.feature.profile

import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionMenuDragLogicTest {
    private val first = LibrarySelectionAction.DOWNLOAD
    private val second = LibrarySelectionAction.LIKE
    private val third = LibrarySelectionAction.PLAY_SELECTED
    private val fourth = LibrarySelectionAction.SHUFFLE
    private val initial = listOf(first, second, third, fourth)

    @Test fun neighborsMoveBeforeReleaseAsFingerCrossesMidpoints() {
        val beforeThreshold = advanceSelectionMenuDrag(initial, first, 0f, 24f, 54f)
        assertEquals(initial, beforeThreshold.order)
        assertEquals(24f, beforeThreshold.offsetPx)

        val crossed = advanceSelectionMenuDrag(beforeThreshold.order, first, beforeThreshold.offsetPx, 5f, 54f)
        assertEquals(listOf(second, first, third, fourth), crossed.order)
        assertEquals(-25f, crossed.offsetPx)

        val crossedAgain = advanceSelectionMenuDrag(crossed.order, first, crossed.offsetPx, 62f, 54f)
        assertEquals(listOf(second, third, first, fourth), crossedAgain.order)
        assertEquals(-17f, crossedAgain.offsetPx)
    }

    @Test fun movingBackwardsRestoresOriginalOrderWithoutLiftingFinger() {
        val down = advanceSelectionMenuDrag(initial, second, 0f, 34f, 54f)
        assertEquals(listOf(first, third, second, fourth), down.order)

        val up = advanceSelectionMenuDrag(down.order, second, down.offsetPx, -40f, 54f)
        assertEquals(initial, up.order)
        assertTrue(up.offsetPx < 0f)
    }

    @Test fun largeDragCrossesMultipleRowsAndClampsAtEnds() {
        val moved = advanceSelectionMenuDrag(initial, first, 0f, 250f, 54f)
        assertEquals(listOf(second, third, fourth, first), moved.order)
        assertEquals(54f * 0.65f, moved.offsetPx)

        val restored = advanceSelectionMenuDrag(moved.order, first, moved.offsetPx, -250f, 54f)
        assertEquals(initial, restored.order)
        assertEquals(-54f * 0.65f, restored.offsetPx)
    }

    @Test fun invalidOrMissingDragCannotReorder() {
        assertEquals(initial, advanceSelectionMenuDrag(initial, first, 0f, 70f, 0f).order)
        assertEquals(initial, advanceSelectionMenuDrag(initial, LibrarySelectionAction.DELETE, 0f, 70f, 54f).order)
    }
}
