package dev.behradhz.meowzix.domain.recommendation

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class PcmAudioAnalysisTest {
    @Test fun noSamplesHasNoFeatureVector() { assertNull(PcmAudioAnalysis().finish()) }

    @Test fun silenceProducesRealSilenceRatioAndZeroEnergy() {
        val analyzer = PcmAudioAnalysis()
        repeat(4096) { analyzer.accept(0.0) }
        val vector = analyzer.finish()!!
        assertTrue(AudioFeatureSchema.isCompatible(vector))
        assertEquals(AudioFeatureSchema.names.size, vector.size)
        assertEquals(0.0, vector[AudioFeatureSchema.RMS], 0.0)
        assertEquals(1.0, vector[AudioFeatureSchema.SILENCE_RATIO], 0.0)
        assertEquals(0.0, vector[AudioFeatureSchema.TEMPO_CONFIDENCE], 0.0)
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

    @Test fun periodicEnergyEnvelopeProducesTempoWithConfidence() {
        val sampleRate = 8192
        val analyzer = PcmAudioAnalysis(sampleRate)
        val frames = 64
        repeat(frames) { frameIndex ->
            val amplitude = if (frameIndex % 4 == 0) 0.9 else 0.06
            repeat(PcmAudioAnalysis.FRAME_SIZE) { sampleInFrame ->
                analyzer.accept(amplitude * sin(2.0 * PI * sampleInFrame / 32.0))
            }
        }
        val vector = analyzer.finish()!!
        val bpm = AudioFeatureSchema.denormalizeTempoBpm(vector[AudioFeatureSchema.TEMPO_BPM])
        assertEquals(120.0, bpm, 8.0)
        assertTrue(vector[AudioFeatureSchema.TEMPO_CONFIDENCE] > 0.25)
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

    @Test(expected = IllegalArgumentException::class)
    fun fftRejectsNonPowerOfTwo() {
        Radix2Fft.transform(DoubleArray(7), DoubleArray(7))
    }

    @Test fun compatibleNormalizedAudioHasSymmetricBoundedSimilarity() {
        val calculator = LocalTrackSimilarityCalculator()
        val first = SimilarityProfile(null, null, DoubleArray(AudioFeatureSchema.names.size) { 0.2 })
        val second = SimilarityProfile(null, null, DoubleArray(AudioFeatureSchema.names.size) { 0.4 })
        assertEquals(0.8, calculator.similarity(first, second).audioSimilarity!!, 1e-12)
        assertEquals(calculator.similarity(first, second), calculator.similarity(second, first))
        assertNull(calculator.similarity(first, second.copy(audio = null)).audioSimilarity)
    }
}
