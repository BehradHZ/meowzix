package dev.behradhz.meowzix.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RulePlaylistClockTest {
    @Test
    fun boundaryDelayTracksInjectedClockWithoutPolling() {
        val clock = FakeRulePlaylistClock(1_000L)
        assertEquals(500L, ruleBoundaryDelayMillis(clock, 1_500L))
        clock.now = 1_480L
        assertEquals(50L, ruleBoundaryDelayMillis(clock, 1_500L))
        clock.now = 1_800L
        assertEquals(50L, ruleBoundaryDelayMillis(clock, 1_500L))
    }

    private class FakeRulePlaylistClock(var now: Long) : RulePlaylistClock() {
        override fun millis(): Long = now
    }
}
