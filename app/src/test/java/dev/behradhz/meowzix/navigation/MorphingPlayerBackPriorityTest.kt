package dev.behradhz.meowzix.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorphingPlayerBackPriorityTest {
    @Test
    fun backgroundBackStaysBlockedUntilPlayerHasFinishedCollapsing() {
        val player = MorphingPlayerState()
        assertFalse(player.blocksBackgroundBack)

        player.expand()
        assertTrue(player.targetExpanded)
        assertTrue(player.blocksBackgroundBack)

        player.updateOverlayVisibility(true)
        player.collapse()
        assertFalse(player.targetExpanded)
        assertTrue(player.blocksBackgroundBack)

        player.updateOverlayVisibility(false)
        assertFalse(player.blocksBackgroundBack)
    }

    @Test
    fun collapsingAgainWhileOverlayIsVisibleForcesItToFinish() {
        val player = MorphingPlayerState()
        player.expand()
        player.updateOverlayVisibility(true)
        player.collapse()
        val firstRequest = player.settleRequestId
        assertFalse(player.finishCollapseImmediately)

        player.collapse()
        assertTrue(player.settleRequestId > firstRequest)
        assertTrue(player.finishCollapseImmediately)

        player.updateOverlayVisibility(false)
        assertFalse(player.blocksBackgroundBack)
        player.expand()
        assertFalse(player.finishCollapseImmediately)
        assertTrue(player.blocksBackgroundBack)
    }

    @Test
    fun swipeCollapseAfterArtworkCollapseDoesNotSkipAnimation() {
        val player = MorphingPlayerState()
        player.expand()
        player.updateOverlayVisibility(true)
        player.collapse() // Artwork recognizer finished first.
        assertFalse(player.finishCollapseImmediately)

        player.settleCollapseFromGesture() // Outer surface finishes the same swipe.
        assertFalse(player.targetExpanded)
        assertFalse(player.finishCollapseImmediately)
        assertTrue(player.blocksBackgroundBack)

        // The explicit second Back still snaps a stalled collapse closed.
        player.collapse()
        assertTrue(player.finishCollapseImmediately)
    }

    @Test
    fun artworkAndOuterRecognizerSettlingSameSwipeNeverSnaps() {
        for (artworkFinishesFirst in listOf(true, false)) {
            val player = MorphingPlayerState()
            player.expand()
            player.updateOverlayVisibility(true)

            val artworkRelease = { player.settleCollapseFromGesture() }
            val outerRelease = { player.settleCollapseFromGesture() }
            if (artworkFinishesFirst) {
                artworkRelease()
                outerRelease()
            } else {
                outerRelease()
                artworkRelease()
            }

            assertFalse(player.targetExpanded)
            assertFalse(player.finishCollapseImmediately)
            assertTrue(player.blocksBackgroundBack)

            // A separate explicit second Back retains its original fast-finish behavior.
            player.collapse()
            assertTrue(player.finishCollapseImmediately)
        }
    }

    @Test
    fun swipeEndSettlesTowardTheExpectedSide() {
        assertFalse(shouldSettlePlayerExpanded(1f, 100f, 72f))
        assertTrue(shouldSettlePlayerExpanded(1f, 30f, 72f))
        assertTrue(shouldSettlePlayerExpanded(0f, -100f, 72f))
        assertFalse(shouldSettlePlayerExpanded(0f, -30f, 72f))
    }

    @Test
    fun partiallyDraggedPlayerBlocksBackgroundNavigationWithoutExpansionCommit() {
        val player = MorphingPlayerState()
        player.updateOverlayVisibility(true)

        assertFalse(player.targetExpanded)
        assertTrue(player.blocksBackgroundBack)

        player.updateOverlayVisibility(false)
        assertFalse(player.blocksBackgroundBack)
    }
}
