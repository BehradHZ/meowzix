package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TdDownloadProgressWatchdogTest {
    @Test
    fun noProgressGetsAFullGracePeriodBeforeBeingMarkedStalled() {
        val watchdog = TdDownloadProgressWatchdog(stallTimeoutMs = 100L)

        assertEquals(TdDownloadProgressState.INITIAL, watchdog.observe(10L, 0L, 0L))
        assertEquals(TdDownloadProgressState.WAITING, watchdog.observe(10L, 0L, 99L))
        assertEquals(TdDownloadProgressState.STALLED, watchdog.observe(10L, 0L, 100L))
    }

    @Test
    fun downloadedBytesResetTheStallClockEvenBeforeSeekOffsetIsReadable() {
        val watchdog = TdDownloadProgressWatchdog(stallTimeoutMs = 100L)

        watchdog.observe(10L, 5_000L, 0L)
        assertEquals(TdDownloadProgressState.PROGRESSED, watchdog.observe(11L, 5_000L, 99L))
        assertEquals(TdDownloadProgressState.WAITING, watchdog.observe(11L, 5_000L, 198L))
        assertEquals(TdDownloadProgressState.STALLED, watchdog.observe(11L, 5_000L, 199L))
    }

    @Test
    fun contiguousReadableBytesAlsoCountAsProgress() {
        val watchdog = TdDownloadProgressWatchdog(stallTimeoutMs = 100L)

        watchdog.observe(10L, 5_000L, 0L)
        assertEquals(TdDownloadProgressState.PROGRESSED, watchdog.observe(10L, 5_128L, 99L))
        assertEquals(TdDownloadProgressState.WAITING, watchdog.observe(10L, 5_128L, 198L))
    }

    @Test
    fun restartResetsTheProgressBaseline() {
        val watchdog = TdDownloadProgressWatchdog(stallTimeoutMs = 100L)

        watchdog.observe(500L, 8_000L, 0L)
        watchdog.reset(0L, 0L, 50L)

        assertEquals(TdDownloadProgressState.WAITING, watchdog.observe(0L, 0L, 149L))
        assertEquals(TdDownloadProgressState.STALLED, watchdog.observe(0L, 0L, 150L))
    }
}
