package dev.behradhz.meowzix.feature.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardFolderSlideDirectionTest {
    private val folderIds = listOf(10, 20, 30)

    @Test fun movingToLaterFoldersSlidesForward() {
        assertEquals(1, forwardFolderSlideDirection(null, 10, folderIds))
        assertEquals(1, forwardFolderSlideDirection(10, 30, folderIds))
    }

    @Test fun movingToEarlierFoldersSlidesBackward() {
        assertEquals(-1, forwardFolderSlideDirection(30, 20, folderIds))
        assertEquals(-1, forwardFolderSlideDirection(20, null, folderIds))
    }

    @Test fun refreshedMissingFolderFallsBackToAllPosition() {
        assertEquals(-1, forwardFolderSlideDirection(20, 99, folderIds))
        assertEquals(1, forwardFolderSlideDirection(99, 10, folderIds))
    }
}
