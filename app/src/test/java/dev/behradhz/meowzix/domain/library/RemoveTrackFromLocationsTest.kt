package dev.behradhz.meowzix.domain.library

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RemoveTrackFromLocationsTest {
    @Test fun libraryOnlyPreservesOfflineFiles() = runTest {
        val actions = mutableListOf<String>()
        executeTrackRemoval(
            offline = false,
            fromLibrary = true,
            removeOffline = { actions += "offline" },
            removeFromLibrary = { actions += "library" },
        )
        assertEquals(listOf("library"), actions)
    }

    @Test fun offlineOnlyLeavesLibraryVisible() = runTest {
        val actions = mutableListOf<String>()
        executeTrackRemoval(
            offline = true,
            fromLibrary = false,
            removeOffline = { actions += "offline" },
            removeFromLibrary = { actions += "library" },
        )
        assertEquals(listOf("offline"), actions)
    }

    @Test fun bothRemovesOfflineBeforeHidingLibrary() = runTest {
        val actions = mutableListOf<String>()
        executeTrackRemoval(
            offline = true,
            fromLibrary = true,
            removeOffline = { actions += "offline" },
            removeFromLibrary = { actions += "library" },
        )
        assertEquals(listOf("offline", "library"), actions)
    }

    @Test fun failedOfflineCleanupDoesNotAlsoHideLibrary() = runTest {
        val actions = mutableListOf<String>()
        try {
            executeTrackRemoval(
                offline = true,
                fromLibrary = true,
                removeOffline = { actions += "offline"; error("Storage failure") },
                removeFromLibrary = { actions += "library" },
            )
            fail("The failure should propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("Storage failure", expected.message)
            assertEquals(listOf("offline"), actions)
        }
    }

    @Test fun nothingSelectedNeverDeletesOrHidesAnything() = runTest {
        val actions = mutableListOf<String>()
        try {
            executeTrackRemoval(
                offline = false,
                fromLibrary = false,
                removeOffline = { actions += "offline" },
                removeFromLibrary = { actions += "library" },
            )
            fail("Selection is required")
        } catch (expected: IllegalArgumentException) {
            assertEquals(emptyList<String>(), actions)
        }
    }
}
