package dev.behradhz.meowzix.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessAndGaplessPolicyTest {
    @Test
    fun replayGainParserAcceptsDbSuffixAndRejectsGarbage() {
        assertEquals(-7.25f, ReplayGainParser.parseDb("-7.25 dB")!!, 0.0001f)
        assertEquals(3f, ReplayGainParser.parseDb("+3.0 dB")!!, 0.0001f)
        assertEquals(null, ReplayGainParser.parseDb("not-a-number"))
        assertEquals(null, ReplayGainParser.parseDb("+99 dB"))
    }

    @Test
    fun rmsEstimatorIsExplicitlyNonLufsAndBounded() {
        val analysis = RmsLoudnessEstimator.estimate(0.1)
        assertEquals(LoudnessAlgorithm.RMS_DBFS_APPROX_V1, analysis.algorithm)
        assertEquals(RmsLoudnessEstimator.VERSION, analysis.algorithmVersion)
        assertNotNull(analysis.measuredDb)
        assertTrue(analysis.suggestedGainDb in PlaybackGainCoordinator.MIN_NORMALIZATION_DB..PlaybackGainCoordinator.MAX_NORMALIZATION_DB)
    }

    @Test
    fun invalidRmsFallsBackToNeutral() {
        val analysis = RmsLoudnessEstimator.estimate(0.0)
        assertEquals(LoudnessAlgorithm.NEUTRAL, analysis.algorithm)
        assertEquals(0f, analysis.suggestedGainDb, 0f)
    }

    @Test
    fun remoteProgressiveIsNeverClaimedGapless() {
        val capability = GaplessPolicy.capability(isLocallyReadable = false, mimeType = "audio/mpeg")
        assertFalse(capability.eligible)
        assertTrue(capability.reason.contains("cannot guarantee"))
    }

    @Test
    fun supportedLocalAudioCanUseMedia3GaplessPath() {
        assertTrue(GaplessPolicy.capability(true, "audio/mpeg").eligible)
        assertFalse(GaplessPolicy.capability(true, "audio/unknown").eligible)
    }

    @Test
    fun crossfadeArchitectureSupportsRealOverlapButPolicyCanFallback() {
        assertTrue(CrossfadeCapability.CurrentArchitecture.supported)
        assertTrue(CrossfadeCapability.CurrentArchitecture.reason.contains("overlap"))
        assertFalse(
            CrossfadePolicy.capability(
                configuredSeconds = 6,
                currentLocallyReadable = false,
                nextLocallyReadable = true,
                equalizerEnabled = false,
                sleepTimerActive = false,
                repeatMode = RepeatMode.OFF,
                currentDurationMs = 180_000,
                nextDurationMs = 180_000,
            ).supported,
        )
    }

    @Test
    fun crossfadeEligibilityRejectsCompetingAudioEffectsAndShortTracks() {
        val base = { eq: Boolean, timer: Boolean, repeat: RepeatMode, duration: Long ->
            CrossfadePolicy.capability(
                configuredSeconds = 6,
                currentLocallyReadable = true,
                nextLocallyReadable = true,
                equalizerEnabled = eq,
                sleepTimerActive = timer,
                repeatMode = repeat,
                currentDurationMs = duration,
                nextDurationMs = 180_000,
            )
        }
        assertFalse(base(true, false, RepeatMode.OFF, 180_000).supported)
        assertFalse(base(false, true, RepeatMode.OFF, 180_000).supported)
        assertFalse(base(false, false, RepeatMode.ONE, 180_000).supported)
        assertFalse(base(false, false, RepeatMode.OFF, 6_500).supported)
        assertTrue(base(false, false, RepeatMode.OFF, 180_000).supported)
    }

    @Test
    fun crossfadeCurveIsComplementaryAndIdentitySwitchesAtMidpoint() {
        val start = CrossfadePolicy.gains(0f)
        val middle = CrossfadePolicy.gains(0.5f)
        val end = CrossfadePolicy.gains(1f)
        assertEquals(1f, start.outgoing, 0.0001f)
        assertEquals(0f, start.incoming, 0.0001f)
        assertEquals(0.5f, middle.outgoing, 0.0001f)
        assertEquals(0.5f, middle.incoming, 0.0001f)
        assertEquals(0f, end.outgoing, 0.0001f)
        assertEquals(1f, end.incoming, 0.0001f)
        assertFalse(CrossfadePolicy.switchIdentity(0.499f))
        assertTrue(CrossfadePolicy.switchIdentity(0.5f))
    }

    @Test
    fun crossfadeDurationIsBoundedAndTooSmallRealOverlapFallsBack() {
        assertEquals(1, CrossfadePolicy.sanitizeSeconds(1))
        assertEquals(12, CrossfadePolicy.sanitizeSeconds(99))
        assertEquals(0, CrossfadePolicy.sanitizeSeconds(0))
        assertEquals(0L, CrossfadePolicy.effectiveOverlapMs(6, 900, 180_000))
        assertEquals(1_500L, CrossfadePolicy.effectiveOverlapMs(6, 1_500, 180_000))
        assertEquals(6_000L, CrossfadePolicy.effectiveOverlapMs(6, 20_000, 180_000))
    }
}
