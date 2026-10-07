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
    fun crossfadeCapabilityIsTruthfulInsteadOfSilentNoOp() {
        assertFalse(CrossfadeCapability.CurrentArchitecture.supported)
        assertTrue(CrossfadeCapability.CurrentArchitecture.reason.contains("real overlapping"))
    }
}
