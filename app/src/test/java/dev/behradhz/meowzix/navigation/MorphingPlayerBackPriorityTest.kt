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
    fun partiallyDraggedPlayerBlocksBackgroundNavigationWithoutExpansionCommit() {
        val player = MorphingPlayerState()
        player.updateOverlayVisibility(true)

        assertFalse(player.targetExpanded)
        assertTrue(player.blocksBackgroundBack)

        player.updateOverlayVisibility(false)
        assertFalse(player.blocksBackgroundBack)
    }
}
