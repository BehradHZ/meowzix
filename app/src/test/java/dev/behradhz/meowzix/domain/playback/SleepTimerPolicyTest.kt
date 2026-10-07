package dev.behradhz.meowzix.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerPolicyTest {
    @Test
    fun durationUsesMonotonicDeadlineAndCountsDown() {
        val persisted = SleepTimerPolicy.duration(30_000, 1_000, "boot-7", 5_000)
        val restored = SleepTimerPolicy.restore(persisted, 11_000, "boot-7")
        assertEquals(SleepTimerMode.DURATION, restored.mode)
        assertEquals(20_000, restored.remainingMs)
        assertEquals(10_000, SleepTimerPolicy.refresh(restored, 21_000).remainingMs)
    }

    @Test
    fun overdueRestoreCancelsInsteadOfResurrectingWork() {
        val persisted = SleepTimerPolicy.duration(5_000, 10_000, "boot-1", 0)
        assertFalse(SleepTimerPolicy.restore(persisted, 15_001, "boot-1").active)
    }

    @Test
    fun differentBootCancelsSafely() {
        val persisted = SleepTimerPolicy.duration(60_000, 10_000, "boot-1", 0)
        assertFalse(SleepTimerPolicy.restore(persisted, 20_000, "boot-2").active)
    }

    @Test
    fun endOfTrackSurvivesSameBootAndKeepsArmedIdentity() {
        val persisted = SleepTimerPolicy.endOfTrack("track-a", "boot-3", 3_000)
        val restored = SleepTimerPolicy.restore(persisted, 123_000, "boot-3")
        assertEquals(SleepTimerMode.END_OF_TRACK, restored.mode)
        assertEquals("track-a", restored.armedMediaId)
        assertEquals(3_000, restored.fadeDurationMs)
    }

    @Test
    fun extendAddsTimeWithoutChangingAbsoluteClockModel() {
        val state = SleepTimerPolicy.restore(
            SleepTimerPolicy.duration(30_000, 1_000, "boot", 0),
            11_000,
            "boot",
        )
        val extended = SleepTimerPolicy.extend(state, 15_000, 11_000)
        assertEquals(35_000, extended.remainingMs)
        assertEquals(46_000, extended.deadlineElapsedRealtimeMs)
    }

    @Test
    fun fadeIsNeutralUntilFadeWindowThenReachesZero() {
        val base = SleepTimerPolicy.restore(
            SleepTimerPolicy.duration(30_000, 0, "boot", 5_000),
            0,
            "boot",
        )
        assertEquals(1f, SleepTimerPolicy.fadeGain(base), 0.0001f)
        val nearEnd = SleepTimerPolicy.refresh(base, 27_500)
        assertEquals(0.5f, SleepTimerPolicy.fadeGain(nearEnd), 0.0001f)
        val expired = SleepTimerPolicy.refresh(base, 30_000)
        assertEquals(0f, SleepTimerPolicy.fadeGain(expired), 0.0001f)
        assertTrue(expired.active)
    }
}
