package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveAudioStartupTest {
    @Test fun coldStartPrioritizesOnlyAContiguousPrefixNotTheFullDownload() {
        val limit = initialStreamingPrefixLimit(10_000_000L)
        assertEquals(64L * 1024L, limit)
        assertTrue(limit < 10_000_000L)
    }

    @Test fun shortFilesAndUnknownSizesAreHandledSafely() {
        assertEquals(32_000L, initialStreamingPrefixLimit(32_000L))
        assertEquals(INITIAL_STREAMING_PREFIX_BYTES, initialStreamingPrefixLimit(null))
        assertEquals(INITIAL_STREAMING_PREFIX_BYTES, initialStreamingPrefixLimit(0L))
    }
}
