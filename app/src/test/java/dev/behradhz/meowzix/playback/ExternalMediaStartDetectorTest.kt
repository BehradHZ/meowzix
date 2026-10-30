package dev.behradhz.meowzix.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalMediaStartDetectorTest {
    @Test
    fun startingOurOwnMediaNeverCountsAsExternal() {
        val detector = ExternalMediaStartDetector()
        detector.baseline(activeMediaPlayers = 0, audibleLocalPlayers = 0)
        assertFalse(detector.observe(1, 1, playbackRequested = true))
        assertEquals(0, detector.externalCount)
    }

    @Test
    fun aNewExternalPlayerPausesImmediatelyAndOnlyOnce() {
        val detector = ExternalMediaStartDetector()
        detector.baseline(activeMediaPlayers = 1, audibleLocalPlayers = 1)
        assertTrue(detector.observe(2, 1, playbackRequested = true))
        assertFalse(detector.observe(2, 1, playbackRequested = true))
        // Meowzix paused, so the remaining player is now entirely external.
        assertFalse(detector.observe(1, 0, playbackRequested = false))
        assertEquals(1, detector.externalCount)
    }

    @Test
    fun aSecondLocalPlayerUsedByCrossfadeNeverTriggersPause() {
        val detector = ExternalMediaStartDetector()
        detector.baseline(activeMediaPlayers = 1, audibleLocalPlayers = 1)
        assertFalse(detector.observe(2, 2, playbackRequested = true))
        assertFalse(detector.observe(1, 1, playbackRequested = true))
        assertEquals(0, detector.externalCount)
    }

    @Test
    fun manualResumeWhileAnotherPlayerRemainsActiveDoesNotImmediatelyRePause() {
        val detector = ExternalMediaStartDetector()
        detector.baseline(activeMediaPlayers = 1, audibleLocalPlayers = 1)
        assertTrue(detector.observe(2, 1, playbackRequested = true))
        assertFalse(detector.observe(1, 0, playbackRequested = false))
        detector.baseline(activeMediaPlayers = 1, audibleLocalPlayers = 0)
        assertFalse(detector.observe(2, 1, playbackRequested = true))
        assertTrue(detector.observe(3, 1, playbackRequested = true))
    }

    @Test
    fun aPlayerStartingWhileMeowzixIsPausedCannotInterruptItsQueue() {
        val detector = ExternalMediaStartDetector()
        detector.baseline(0, 0)
        assertFalse(detector.observe(1, 0, playbackRequested = false))
        assertEquals(1, detector.externalCount)
        assertFalse(detector.observe(2, 1, playbackRequested = true))
    }

    @Test
    fun countsAreClampedIfDeviceProvidesInconsistentSnapshots() {
        val detector = ExternalMediaStartDetector()
        detector.baseline(0, 2)
        assertFalse(detector.observe(-1, -1, playbackRequested = true))
        assertEquals(0, detector.externalCount)
    }
}
