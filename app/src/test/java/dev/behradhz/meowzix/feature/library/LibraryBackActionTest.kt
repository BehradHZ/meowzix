package dev.behradhz.meowzix.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBackActionTest {
    @Test fun firstBackCancelsSelectionWhileStillInArtist() {
        assertEquals(
            LibraryBackAction.CANCEL_SELECTION,
            libraryBackAction(displaySheet = false, selecting = true, inAggregate = true),
        )
        assertEquals(
            LibraryBackAction.CLOSE_AGGREGATE,
            libraryBackAction(displaySheet = false, selecting = false, inAggregate = true),
        )
    }

    @Test fun openDialogHasPriorityOverSelection() {
        assertEquals(
            LibraryBackAction.DISMISS_DISPLAY,
            libraryBackAction(displaySheet = true, selecting = true, inAggregate = true),
        )
    }
}
