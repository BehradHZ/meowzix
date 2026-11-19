package dev.behradhz.meowzix.feature.library

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionLogicTest {
    private val a = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val b = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test fun unselectingTheLastSongLeavesNoSelection() {
        val selected = toggleSelectedEntries(emptyMap(), mapOf(a to "A"))
        assertEquals(1, selected.size)
        assertTrue(toggleSelectedEntries(selected, mapOf(a to "A")).isEmpty())
    }

    @Test fun collectionTogglingHonorsPartialSelectionAndDeduplicatesTracks() {
        val whole = mapOf(a to "A", b to "B")
        val partiallySelected = mapOf(a to "A")
        assertEquals(whole, toggleSelectedEntries(partiallySelected, whole))
        assertTrue(toggleSelectedEntries(whole, whole).isEmpty())
        assertEquals(partiallySelected, toggleSelectedEntries(partiallySelected, emptyMap()))
    }
}
