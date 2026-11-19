package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardFolderLoadPolicyTest {
    @Test fun fullIndexExpiresExactlyAfterSevenDays() {
        val created = 1_700_000_000_000L
        assertTrue(isForwardFolderIndexFresh(created, created))
        assertTrue(isForwardFolderIndexFresh(created, created + FORWARD_FOLDER_INDEX_TTL_MS - 1))
        assertFalse(isForwardFolderIndexFresh(created, created + FORWARD_FOLDER_INDEX_TTL_MS))
        assertFalse(isForwardFolderIndexFresh(created, created - 1))
        assertFalse(isForwardFolderIndexFresh(0L, created))
    }

    @Test fun emptyGetChatsBeforeEndOfListIsNotAConfirmedEmptyFolder() {
        assertFalse(isConfirmedEmptyForwardFolder(0, 0, reachedEnd = false, definitionLoaded = true))
    }

    @Test fun failedFolderDefinitionOrUnresolvedChatsCannotBeReportedAsEmpty() {
        assertFalse(isConfirmedEmptyForwardFolder(0, 0, reachedEnd = true, definitionLoaded = false))
        assertFalse(isConfirmedEmptyForwardFolder(3, 0, reachedEnd = true, definitionLoaded = true))
    }

    @Test fun emptyFolderMustBeFullyLoadedAndHaveValidDefinition() {
        assertTrue(isConfirmedEmptyForwardFolder(0, 0, reachedEnd = true, definitionLoaded = true))
    }

    @Test fun onlyCompleteNonemptyFoldersAreCached() {
        assertFalse(shouldCacheForwardFolderChats(0, 0, reachedEnd = true, definitionLoaded = true))
        assertFalse(shouldCacheForwardFolderChats(50, 50, reachedEnd = false, definitionLoaded = true))
        assertFalse(shouldCacheForwardFolderChats(50, 50, reachedEnd = true, definitionLoaded = false))
        assertFalse(shouldCacheForwardFolderChats(51, 50, reachedEnd = true, definitionLoaded = true))
        assertTrue(shouldCacheForwardFolderChats(50, 50, reachedEnd = true, definitionLoaded = true))
    }
}
