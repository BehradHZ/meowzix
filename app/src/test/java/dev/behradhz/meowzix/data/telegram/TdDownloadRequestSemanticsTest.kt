package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdDownloadRequestSemanticsTest {
    @Test
    fun fullDownloadDoesNotCompleteFromHighProgressAlone() {
        assertFalse(satisfiesDownloadRequest(0, 0, 10_000, 9_000, false))
        assertTrue(satisfiesDownloadRequest(0, 0, 10_000, 10_000, true))
    }

    @Test
    fun prefetchRequiresContiguousRequestedPrefix() {
        assertFalse(satisfiesDownloadRequest(0, 2_000, 10_000, 1_999, false))
        assertTrue(satisfiesDownloadRequest(0, 2_000, 10_000, 2_000, false))
        assertFalse(satisfiesDownloadRequest(5_000, 2_000, 10_000, 6_000, false))
        assertTrue(satisfiesDownloadRequest(5_000, 2_000, 10_000, 7_000, false))
    }

    @Test
    fun requestedPrefixIsClampedToShortFileLength() {
        assertTrue(satisfiesDownloadRequest(0, 2_000, 800, 800, false))
        assertFalse(satisfiesDownloadRequest(0, 2_000, 800, 799, false))
    }
}
