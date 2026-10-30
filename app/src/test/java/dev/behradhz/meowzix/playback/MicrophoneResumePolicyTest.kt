package dev.behradhz.meowzix.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneResumePolicyTest {
    private fun canResume(
        pausedByMicrophone: Boolean = true,
        playbackRequested: Boolean = false,
        otherMediaPlaying: Boolean = false,
        hasTrack: Boolean = true,
        playbackEnded: Boolean = false,
        sleepTimerExpired: Boolean = false,
    ) = shouldResumeAfterMicrophoneStops(
        pausedByMicrophone,
        playbackRequested,
        otherMediaPlaying,
        hasTrack,
        playbackEnded,
        sleepTimerExpired,
    )

    @Test fun recordingStopResumesOnlyMusicPausedByMicrophone() {
        assertTrue(canResume())
        assertFalse(canResume(pausedByMicrophone = false))
    }

    @Test fun manualPlayDuringRecordingCannotTriggerDuplicateResume() {
        assertFalse(canResume(playbackRequested = true))
    }

    @Test fun doNotStealFocusFromAnotherPlayerAfterRecording() {
        assertFalse(canResume(otherMediaPlaying = true))
    }

    @Test fun neverReviveCompletedOrInactiveSessions() {
        assertFalse(canResume(hasTrack = false))
        assertFalse(canResume(playbackEnded = true))
        assertFalse(canResume(sleepTimerExpired = true))
    }
}
