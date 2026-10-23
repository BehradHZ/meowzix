package dev.behradhz.meowzix.feature.nowplaying

import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylTonearmPolicyTest {
    @Test fun pauseAndTerminalStatesParkTheStylus() {
        assertTrue(tonearmParked(PlaybackStatus.PAUSED))
        assertTrue(tonearmParked(PlaybackStatus.IDLE))
        assertTrue(tonearmParked(PlaybackStatus.ERROR))
    }

    @Test fun playbackPreparationAndBufferingDoNotRepeatedlyRetractTheStylus() {
        assertFalse(tonearmParked(PlaybackStatus.PLAYING))
        assertFalse(tonearmParked(PlaybackStatus.BUFFERING))
        assertFalse(tonearmParked(PlaybackStatus.PREPARING))
        assertFalse(tonearmParked(PlaybackStatus.DOWNLOADING))
    }
}
