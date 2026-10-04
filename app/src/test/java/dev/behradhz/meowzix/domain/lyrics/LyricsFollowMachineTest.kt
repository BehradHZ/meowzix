package dev.behradhz.meowzix.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsFollowMachineTest {
    @Test
    fun `user interaction suspends following immediately`() {
        val machine = LyricsFollowMachine()
        machine.onUserInteraction()
        assertEquals(LyricsFollowMode.USER_BROWSING, machine.mode)
    }

    @Test
    fun `stable active line inside anchor zone reattaches after dwell`() {
        val machine = LyricsFollowMachine()
        machine.onUserInteraction()
        assertEquals(
            LyricsFollowAction.NONE,
            machine.evaluate(100f, 100f, 40f, false, false, false, 1_000L),
        )
        assertEquals(LyricsFollowMode.REATTACH_ELIGIBLE, machine.mode)
        assertEquals(
            LyricsFollowAction.NONE,
            machine.evaluate(105f, 100f, 40f, false, false, false, 1_200L),
        )
        assertEquals(
            LyricsFollowAction.SETTLE_AND_FOLLOW,
            machine.evaluate(105f, 100f, 40f, false, false, false, 1_251L),
        )
        assertEquals(LyricsFollowMode.FOLLOWING, machine.mode)
    }

    @Test
    fun `fling through zone never reattaches`() {
        val machine = LyricsFollowMachine()
        machine.onUserInteraction()
        repeat(4) { index ->
            assertEquals(
                LyricsFollowAction.NONE,
                machine.evaluate(100f, 100f, 40f, false, true, true, 1_000L + index * 300L),
            )
        }
        assertEquals(LyricsFollowMode.USER_BROWSING, machine.mode)
    }

    @Test
    fun `hysteresis avoids flapping near enter boundary`() {
        val machine = LyricsFollowMachine()
        machine.onUserInteraction()
        machine.evaluate(112f, 100f, 40f, false, false, false, 1_000L)
        assertEquals(LyricsFollowMode.REATTACH_ELIGIBLE, machine.mode)
        machine.evaluate(118f, 100f, 40f, false, false, false, 1_100L)
        assertEquals(LyricsFollowMode.REATTACH_ELIGIBLE, machine.mode)
        machine.evaluate(124f, 100f, 40f, false, false, false, 1_200L)
        assertEquals(LyricsFollowMode.USER_BROWSING, machine.mode)
    }

    @Test
    fun `pointer interaction cancels eligibility`() {
        val machine = LyricsFollowMachine()
        machine.onUserInteraction()
        machine.evaluate(100f, 100f, 40f, false, false, false, 1_000L)
        machine.evaluate(100f, 100f, 40f, true, false, false, 1_400L)
        assertEquals(LyricsFollowMode.USER_BROWSING, machine.mode)
    }
}
