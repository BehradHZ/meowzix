package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
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
}
