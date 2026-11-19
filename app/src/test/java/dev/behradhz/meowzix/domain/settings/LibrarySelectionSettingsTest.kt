package dev.behradhz.meowzix.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionSettingsTest {
    @Test fun dockDefaultsToRequestedFourActionsInPhysicalLeftToRightOrder() {
        assertEquals(
            listOf(
                LibrarySelectionAction.DELETE,
                LibrarySelectionAction.PLAY_NEXT,
                LibrarySelectionAction.ADD_TO_PLAYLIST,
                LibrarySelectionAction.SHARE,
            ),
            LibrarySelectionSettings().dock,
        )
    }

    @Test fun reorderingPinnedActionsSwapsInsteadOfDuplicating() {
        val next = LibrarySelectionSettings().setDockSlot(0, LibrarySelectionAction.SHARE)
        assertEquals(
            listOf(
                LibrarySelectionAction.SHARE,
                LibrarySelectionAction.PLAY_NEXT,
                LibrarySelectionAction.ADD_TO_PLAYLIST,
                LibrarySelectionAction.DELETE,
            ),
            next.dock,
        )
        assertEquals(4, next.dock.distinct().size)
    }

    @Test fun replacingDockActionMovesDisplacedActionIntoMore() {
        val changed = LibrarySelectionSettings().setDockSlot(2, LibrarySelectionAction.DOWNLOAD)
        assertEquals(LibrarySelectionAction.DOWNLOAD, changed.dock[2])
        assertFalse(LibrarySelectionAction.DOWNLOAD in changed.more)
        assertTrue(LibrarySelectionAction.ADD_TO_PLAYLIST in changed.more)
        assertEquals(4, changed.dock.size)
    }

    @Test fun userControlsNumberOfMoreActionsWithoutChangingFourSlotDock() {
        val initial = LibrarySelectionSettings()
        val emptyMore = initial.more.fold(initial) { settings, action ->
            settings.setMoreEnabled(action, false)
        }
        assertTrue(emptyMore.more.isEmpty())
        assertEquals(DEFAULT_SELECTION_DOCK, emptyMore.dock)
        val restored = emptyMore.setMoreEnabled(LibrarySelectionAction.DOWNLOAD, true)
        assertEquals(listOf(LibrarySelectionAction.DOWNLOAD), restored.more)
        assertEquals(4, restored.dock.size)
    }

    @Test fun legacyUnlikePreferencesMigrateToOneFavoriteAction() {
        assertEquals(LibrarySelectionAction.LIKE, librarySelectionActionFromName("UNLIKE"))
        assertEquals("Like", LibrarySelectionAction.LIKE.selectionLabel(false))
        assertEquals("Unlike", LibrarySelectionAction.LIKE.selectionLabel(true))
        assertFalse(LibrarySelectionAction.entries.any { it.name == "UNLIKE" })
    }

    @Test fun favoriteActionLikesMixedSelectionsAndUnlikesOnlyIfAllAreLiked() {
        assertTrue(selectionShouldLike(listOf(false, false)))
        assertTrue(selectionShouldLike(listOf(true, false, true)))
        assertFalse(selectionShouldLike(listOf(true, true)))
        assertFalse(selectionShouldLike(emptyList()))
    }

    @Test fun disabledMoreActionKeepsItsDragPosition() {
        val moved = LibrarySelectionSettings().moveMoreAction(LibrarySelectionAction.LIKE, 0)
        val disabled = moved.setMoreEnabled(LibrarySelectionAction.LIKE, false)
        assertEquals(LibrarySelectionAction.LIKE, disabled.moreOrder.first())
        val restored = disabled.setMoreEnabled(LibrarySelectionAction.LIKE, true)
        assertEquals(LibrarySelectionAction.LIKE, restored.more.first())
        assertEquals(DEFAULT_SELECTION_DOCK, restored.dock)
    }

    @Test fun displacedPinnedActionKeepsAUniquePositionInMoreOrder() {
        val changed = LibrarySelectionSettings().setDockSlot(0, LibrarySelectionAction.DOWNLOAD)
        assertEquals(LibrarySelectionAction.DELETE, changed.moreOrder[1])
        assertEquals(changed.moreOrder.size, changed.moreOrder.distinct().size)
        assertFalse(LibrarySelectionAction.DOWNLOAD in changed.moreOrder)
    }

    @Test fun stalePreferencesCannotCreateDuplicatedOrUndersizedDock() {
        val corrupt = LibrarySelectionSettings(
            dock = listOf(LibrarySelectionAction.SHARE, LibrarySelectionAction.SHARE),
            more = listOf(
                LibrarySelectionAction.SHARE,
                LibrarySelectionAction.DOWNLOAD,
                LibrarySelectionAction.DOWNLOAD,
            ),
        )
        val normalized = corrupt.normalized()
        assertEquals(4, normalized.dock.size)
        assertEquals(4, normalized.dock.distinct().size)
        assertFalse(LibrarySelectionAction.SHARE in normalized.more)
        assertEquals(listOf(LibrarySelectionAction.DOWNLOAD), normalized.more)
    }
}
