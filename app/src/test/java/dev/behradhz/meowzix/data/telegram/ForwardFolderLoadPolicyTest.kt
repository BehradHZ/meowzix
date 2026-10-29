package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardFolderLoadPolicyTest {
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
        assertFalse(shouldCacheForwardFolderChats(0, reachedEnd = true, definitionLoaded = true))
        assertFalse(shouldCacheForwardFolderChats(50, reachedEnd = false, definitionLoaded = true))
        assertFalse(shouldCacheForwardFolderChats(50, reachedEnd = true, definitionLoaded = false))
        assertTrue(shouldCacheForwardFolderChats(50, reachedEnd = true, definitionLoaded = true))
    }
}
