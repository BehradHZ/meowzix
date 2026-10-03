package dev.behradhz.meowzix.domain.recommendation

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class PcmAudioAnalysisTest {
    @Test fun noSamplesHasNoFeatureVector() { assertNull(PcmAudioAnalysis().finish()) }
    @Test fun silenceProducesRealSilenceRatioAndZeroEnergy() {
        val analyzer = PcmAudioAnalysis()
        repeat(4096) { analyzer.accept(0.0) }
        val vector = analyzer.finish()!!
        assertTrue(AudioFeatureSchema.isCompatible(vector))
        assertEquals(0.0, vector[AudioFeatureSchema.RMS], 0.0)
        assertEquals(1.0, vector[AudioFeatureSchema.SILENCE_RATIO], 0.0)
    }
    @Test fun exactBinSineHasExpectedEnergyCentroidAndCrossings() {
        val bin = 64
        val analyzer = PcmAudioAnalysis()
        repeat(4096) { analyzer.accept(0.5 * sin(2.0 * PI * bin * it / 1024.0)) }
        val v = analyzer.finish()!!
        assertEquals(0.5 / sqrt(2.0), v[AudioFeatureSchema.RMS], 1e-5)
        assertEquals(bin / 512.0, v[AudioFeatureSchema.SPECTRAL_CENTROID], 0.004)
        assertEquals(bin / 512.0, v[AudioFeatureSchema.ZERO_CROSSING_RATE], 0.004)
        assertEquals(bin / 512.0, v[AudioFeatureSchema.SPECTRAL_ROLLOFF], 0.004)
    }
    @Test fun energyChangesProduceDynamicRange() {
        val analyzer = PcmAudioAnalysis()
        repeat(4096) { analyzer.accept(0.02 * sin(2.0 * PI * it / 16)) }
        repeat(4096) { analyzer.accept(0.8 * sin(2.0 * PI * it / 16)) }
        assertTrue(analyzer.finish()!![AudioFeatureSchema.DYNAMIC_RANGE] > 0.5)
    }
    @Test fun shortFrameIsPaddedAndFinishIsIdempotent() {
        val analyzer = PcmAudioAnalysis()
        repeat(20) { analyzer.accept(0.5) }
        val one = analyzer.finish()!!
        assertArrayEquals(one, analyzer.finish()!!, 0.0)
        assertTrue(AudioFeatureSchema.isCompatible(one))
    }
    @Test fun fftImpulseHasFlatSpectrum() {
        val real = DoubleArray(16).also { it[0] = 1.0 }
        val imaginary = DoubleArray(16)
        Radix2Fft.transform(real, imaginary)
        assertArrayEquals(DoubleArray(16) { 1.0 }, real, 1e-12)
        assertArrayEquals(DoubleArray(16), imaginary, 1e-12)
    }
    @Test(expected = IllegalArgumentException::class) fun fftRejectsNonPowerOfTwo() { Radix2Fft.transform(DoubleArray(7), DoubleArray(7)) }
    @Test fun compatibleNormalizedAudioHasSymmetricBoundedSimilarity() {
        val calculator = LocalTrackSimilarityCalculator()
        val first = SimilarityProfile(null, null, DoubleArray(6) { 0.2 })
        val second = SimilarityProfile(null, null, DoubleArray(6) { 0.4 })
        assertEquals(0.8, calculator.similarity(first, second).audioSimilarity!!, 1e-12)
        assertEquals(calculator.similarity(first, second), calculator.similarity(second, first))
        assertNull(calculator.similarity(first, second.copy(audio = null)).audioSimilarity)
    }
}
