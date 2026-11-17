package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkVerticalSwipeTest {
    private val openThreshold = 72f
    private val closeThreshold = 96f

    private fun action(x: Float, y: Float) =
        artworkVerticalSwipeAction(Offset(x, y), openThreshold, closeThreshold)

    @Test fun upwardCoverSwipeOpensQueue() {
        assertEquals(ArtworkVerticalSwipeAction.OPEN_QUEUE, action(0f, -100f))
        assertEquals(ArtworkVerticalSwipeAction.OPEN_QUEUE, action(12f, -72f))
    }

    @Test fun downwardCoverSwipeRetainsCloseBehavior() {
        assertEquals(ArtworkVerticalSwipeAction.CLOSE_PLAYER, action(0f, 110f))
        assertEquals(ArtworkVerticalSwipeAction.CLOSE_PLAYER, action(6f, 96f))
    }

    @Test fun shortVerticalSwipeDoesNotTriggerEitherAction() {
        assertEquals(ArtworkVerticalSwipeAction.NONE, action(0f, -71f))
        assertEquals(ArtworkVerticalSwipeAction.NONE, action(0f, 95f))
    }

    @Test fun horizontalAndDiagonalGesturesDoNotOpenQueue() {
        assertEquals(ArtworkVerticalSwipeAction.NONE, action(-150f, -90f))
        assertEquals(ArtworkVerticalSwipeAction.NONE, action(100f, 100f))
    }

    @Test fun artworkQueueDragFollowsFingerBeforeReleaseAndCanReverse() {
        val dragRange = 260f
        val firstFrame = queueDragFractionAfterDelta(0f, -65f, dragRange)
        assertEquals(0.25f, firstFrame, 0.0001f)
        assertFalse(shouldExpandInlineQueue(firstFrame, alreadyExpanded = false))

        val secondFrame = queueDragFractionAfterDelta(firstFrame, -65f, dragRange)
        assertEquals(0.5f, secondFrame, 0.0001f)
        assertTrue(shouldExpandInlineQueue(secondFrame, alreadyExpanded = false))

        val reversed = queueDragFractionAfterDelta(secondFrame, 65f, dragRange)
        assertEquals(0.25f, reversed, 0.0001f)
        assertFalse(shouldExpandInlineQueue(reversed, alreadyExpanded = false))
    }

    @Test fun artworkQueueDragClampsAndUsesSameCollapseThreshold() {
        assertEquals(1f, queueDragFractionAfterDelta(0.8f, -200f, 260f), 0f)
        assertEquals(0f, queueDragFractionAfterDelta(0.2f, 200f, 260f), 0f)
        assertFalse(shouldExpandInlineQueue(0.54f, alreadyExpanded = true))
        assertTrue(shouldExpandInlineQueue(0.55f, alreadyExpanded = true))
    }
}
