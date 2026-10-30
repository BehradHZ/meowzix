package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.playback.AudioInterruptionResumePolicy.Effect
import dev.behradhz.meowzix.playback.AudioInterruptionResumePolicy.Source
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioInterruptionResumePolicyTest {
    private fun policy() = AudioInterruptionResumePolicy()

    @Test fun externalInterruptionPausesAndResumesOnceAfterItsEnd() {
        val p = policy()
        assertEquals(Effect.PAUSE, p.interrupted(Source.EXTERNAL_PLAYBACK, true))
        assertTrue(p.isResumeArmed())
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
        assertTrue(p.isResumeRequested())
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
        p.playbackStarted()
        assertFalse(p.isResumeArmed())
        assertFalse(p.isResumeRequested())
    }

    @Test fun manuallyPausedMusicCannotResumeAfterAnotherAppStops() {
        val p = policy()
        p.userPaused()
        assertEquals(Effect.NONE, p.interrupted(Source.EXTERNAL_PLAYBACK, false))
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }

    @Test fun userPausesDuringInterruptionSoNoAutomaticResumeHappens() {
        val p = policy()
        p.interrupted(Source.EXTERNAL_PLAYBACK, true)
        p.userPaused()
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }

    @Test fun microphoneAndExternalMustBothEndBeforeResuming() {
        val p = policy()
        assertEquals(Effect.PAUSE, p.interrupted(Source.EXTERNAL_PLAYBACK, true))
        assertEquals(Effect.NONE, p.interrupted(Source.MICROPHONE, false))
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.MICROPHONE))
    }

    @Test fun oppositeInterruptionCompletionOrderAlsoWorks() {
        val p = policy()
        assertEquals(Effect.PAUSE, p.interrupted(Source.MICROPHONE, true))
        assertEquals(Effect.NONE, p.interrupted(Source.EXTERNAL_PLAYBACK, false))
        assertEquals(Effect.NONE, p.interruptionEnded(Source.MICROPHONE))
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }

    @Test fun duplicateStartsAndStopsCannotIssueMultipleResumeRequests() {
        val p = policy()
        p.interrupted(Source.EXTERNAL_PLAYBACK, true)
        assertEquals(Effect.NONE, p.interrupted(Source.EXTERNAL_PLAYBACK, true))
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }

    @Test fun aNewExternalInterruptionCancelsPendingResumeUntilItStops() {
        val p = policy()
        p.interrupted(Source.EXTERNAL_PLAYBACK, true)
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
        assertEquals(Effect.PAUSE, p.interrupted(Source.EXTERNAL_PLAYBACK, true))
        assertFalse(p.isResumeRequested())
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }

    @Test fun aNewMicrophoneInterruptionCancelsPendingResumeUntilItStops() {
        val p = policy()
        p.interrupted(Source.EXTERNAL_PLAYBACK, true)
        p.interruptionEnded(Source.EXTERNAL_PLAYBACK)
        assertEquals(Effect.NONE, p.interrupted(Source.MICROPHONE, false))
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.MICROPHONE))
    }

    @Test fun newTrackSelectionInvalidatesOldAutoResume() {
        val p = policy()
        p.interrupted(Source.EXTERNAL_PLAYBACK, true)
        p.newPlaybackSelection()
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }

    @Test fun turningOffAutoResumeDoesNotBlockSubsequentSystemPauses() {
        val p = policy()
        p.interrupted(Source.EXTERNAL_PLAYBACK, true)
        p.disableAutoResume()
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
        assertEquals(Effect.PAUSE, p.interrupted(Source.MICROPHONE, true))
    }

    @Test fun explicitPlayAfterUserPauseIsNotTreatedAsAnOldAutoResume() {
        val p = policy()
        p.userPaused()
        p.userPlayed()
        assertEquals(Effect.PAUSE, p.interrupted(Source.EXTERNAL_PLAYBACK, true))
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }

    @Test fun explicitPlayDuringInterruptionRetainsOnlyNewIntent() {
        val p = policy()
        p.interrupted(Source.EXTERNAL_PLAYBACK, true)
        p.userPaused()
        p.userPlayed()
        assertEquals(Effect.REQUEST_RESUME, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }

    @Test fun rejectedResumeIsNeverRepeatedWithoutANewInterruption() {
        val p = policy()
        p.interrupted(Source.EXTERNAL_PLAYBACK, true)
        p.interruptionEnded(Source.EXTERNAL_PLAYBACK)
        p.resumeRejected()
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
        assertFalse(p.isResumeRequested())
    }

    @Test fun serviceDestructionClearsAllPendingState() {
        val p = policy()
        p.interrupted(Source.MICROPHONE, true)
        p.reset()
        assertFalse(p.isInterruptedBy(Source.MICROPHONE))
        assertEquals(Effect.NONE, p.interruptionEnded(Source.MICROPHONE))
    }

    @Test fun unrelatedEndEventNeverTriggersPlayback() {
        val p = policy()
        assertEquals(Effect.NONE, p.interruptionEnded(Source.MICROPHONE))
        assertEquals(Effect.NONE, p.interruptionEnded(Source.EXTERNAL_PLAYBACK))
    }
}
