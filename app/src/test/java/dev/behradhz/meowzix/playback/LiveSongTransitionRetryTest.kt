package dev.behradhz.meowzix.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSongTransitionRetryTest {
    @Test
    fun onlyLatestTransitionRetriesAfterTenSeconds() = runTest {
        val calls = mutableListOf<String>()
        val retry = LiveSongTransitionRetry(this, 10_000L, calls::add)

        retry.schedule("track-a")
        advanceTimeBy(5_000L)
        runCurrent()
        retry.schedule("track-b")
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(emptyList<String>(), calls)

        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(listOf("track-b"), calls)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(listOf("track-b"), calls)
    }

    @Test
    fun cancellationAndEmptyTrackPreventStaleRetries() = runTest {
        val calls = mutableListOf<String>()
        val retry = LiveSongTransitionRetry(this, 10_000L, calls::add)

        retry.schedule("track-a")
        retry.cancel()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(emptyList<String>(), calls)

        retry.schedule("track-b")
        retry.schedule(null)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun sameMediaIdTransitionResetsTheRetryWindow() = runTest {
        val calls = mutableListOf<String>()
        val retry = LiveSongTransitionRetry(this, 10_000L, calls::add)

        retry.schedule("same-id")
        advanceTimeBy(9_000L)
        runCurrent()
        retry.schedule("same-id")
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(emptyList<String>(), calls)

        advanceTimeBy(9_000L)
        runCurrent()
        assertEquals(listOf("same-id"), calls)
    }
}
