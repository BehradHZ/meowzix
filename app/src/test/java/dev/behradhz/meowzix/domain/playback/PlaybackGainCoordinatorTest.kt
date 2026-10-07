package dev.behradhz.meowzix.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGainCoordinatorTest {
    @Test
    fun baseVolumeRemainsIndependentOfTransientGains() {
        val state = PlaybackGainState(
            userBaseVolume = 0.7f,
            normalizationGainDb = -3f,
            sleepTimerGain = 0.5f,
            crossfadeGain = 0.8f,
        )
        PlaybackGainCoordinator.resolve(state)
        assertEquals(0.7f, state.userBaseVolume, 0f)
    }

    @Test
    fun neutralBypassAtUnityIsExactlyNeutral() {
        val result = PlaybackGainCoordinator.resolve(PlaybackGainState())
        assertEquals(1f, result.playerVolume, 0.0001f)
        assertEquals(1f, result.normalizationLinear, 0.0001f)
        assertEquals(1f, result.safetyHeadroomLinear, 0.0001f)
    }

    @Test
    fun timerAndCrossfadeContributionsMultiplyWithoutMutatingBase() {
        val result = PlaybackGainCoordinator.resolve(
            PlaybackGainState(userBaseVolume = 0.8f, sleepTimerGain = 0.5f, crossfadeGain = 0.5f),
        )
        assertEquals(0.2f, result.playerVolume, 0.0001f)
    }

    @Test
    fun positiveNormalizationAndEqReserveHeadroom() {
        val result = PlaybackGainCoordinator.resolve(
            PlaybackGainState(normalizationGainDb = 4f, equalizerPeakBoostDb = 6f),
        )
        assertTrue(result.playerVolume <= 1f)
        val postEqWorstCase = result.playerVolume *
            PlaybackGainCoordinator.dbToLinear(6f)
        assertTrue(postEqWorstCase <= 1.0001f)
    }

    @Test
    fun allExternalGainInputsAreBounded() {
        val result = PlaybackGainCoordinator.resolve(
            PlaybackGainState(
                userBaseVolume = 4f,
                normalizationGainDb = 100f,
                equalizerPeakBoostDb = 100f,
                sleepTimerGain = 4f,
                crossfadeGain = 4f,
            ),
        )
        assertTrue(result.playerVolume in 0f..1f)
    }
}
