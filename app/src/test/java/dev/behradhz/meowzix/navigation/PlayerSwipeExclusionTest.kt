package dev.behradhz.meowzix.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSwipeExclusionTest {
    private val moreButton = Rect(950f, 48f, 1010f, 108f)

    @Test
    fun verticalDragStartingInsideMoreButtonCannotCollapsePlayer() {
        assertTrue(shouldIgnorePlayerSwipeFromMoreActions(Offset(980f, 82f), moreButton, 10f))
        assertTrue(shouldIgnorePlayerSwipeFromMoreActions(Offset(955f, 50f), moreButton, 10f))
    }

    @Test
    fun slightMissAroundThreeDotsAlsoCannotCollapsePlayer() {
        assertTrue(shouldIgnorePlayerSwipeFromMoreActions(Offset(944f, 90f), moreButton, 10f))
        assertTrue(shouldIgnorePlayerSwipeFromMoreActions(Offset(1016f, 95f), moreButton, 10f))
    }

    @Test
    fun draggingFromNormalPlayerSurfaceStillUsesExistingCollapseGesture() {
        assertFalse(shouldIgnorePlayerSwipeFromMoreActions(Offset(430f, 90f), moreButton, 10f))
        assertFalse(shouldIgnorePlayerSwipeFromMoreActions(Offset(600f, 400f), moreButton, 10f))
        assertFalse(shouldIgnorePlayerSwipeFromMoreActions(Offset(980f, 138f), moreButton, 10f))
    }

    @Test
    fun noMeasuredButtonDoesNotBlockGesturesElsewhere() {
        assertFalse(shouldIgnorePlayerSwipeFromMoreActions(Offset(980f, 82f), null, 10f))
    }
}
