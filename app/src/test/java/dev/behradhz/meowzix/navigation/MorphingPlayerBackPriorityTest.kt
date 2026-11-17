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
    fun partiallyDraggedPlayerBlocksBackgroundNavigationWithoutExpansionCommit() {
        val player = MorphingPlayerState()
        player.updateOverlayVisibility(true)

        assertFalse(player.targetExpanded)
        assertTrue(player.blocksBackgroundBack)

        player.updateOverlayVisibility(false)
        assertFalse(player.blocksBackgroundBack)
    }
}
