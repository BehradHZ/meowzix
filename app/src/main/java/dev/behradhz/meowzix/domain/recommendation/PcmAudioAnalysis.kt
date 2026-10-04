package dev.behradhz.meowzix.domain.recommendation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Fixed acoustic order; descriptors are normalized to [0,1], frequency relative to Nyquist. */
object AudioFeatureSchema {
    const val VERSION = 3
    const val RMS = 0
    const val ZERO_CROSSING_RATE = 1
    const val SILENCE_RATIO = 2
    const val DYNAMIC_RANGE = 3
    const val SPECTRAL_CENTROID = 4
    const val SPECTRAL_ROLLOFF = 5
    const val TEMPO_BPM = 6
    const val TEMPO_CONFIDENCE = 7
    const val MIN_TEMPO_BPM = 60.0
    const val MAX_TEMPO_BPM = 200.0
    val names = listOf(
        "rms",
        "zero_crossing_rate",
        "silence_ratio",
        "dynamic_range",
        "spectral_centroid",
        "spectral_rolloff",
        "tempo_bpm_norm",
        "tempo_confidence",
    )

    fun isCompatible(values: DoubleArray): Boolean =
        values.size == names.size && values.all { it.isFinite() && it in 0.0..1.0 }

    fun normalizeTempoBpm(bpm: Double): Double =
        ((bpm - MIN_TEMPO_BPM) / (MAX_TEMPO_BPM - MIN_TEMPO_BPM)).coerceIn(0.0, 1.0)

    fun denormalizeTempoBpm(value: Double): Double =
        MIN_TEMPO_BPM + value.coerceIn(0.0, 1.0) * (MAX_TEMPO_BPM - MIN_TEMPO_BPM)
}

/** In-place radix-2 FFT, forward unscaled transform. Compact bounded implementation, no new dependency. */
object Radix2Fft {
    fun transform(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0 && imaginary.size == n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val im = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = im
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wr = cos(angle)
            val wi = sin(angle)
            var offset = 0
            while (offset < n) {
                var ur = 1.0
                var ui = 0.0
                for (k in 0 until length / 2) {
                    val even = offset + k
                    val odd = even + length / 2
                    val tr = ur * real[odd] - ui * imaginary[odd]
                    val ti = ur * imaginary[odd] + ui * real[odd]
                    real[odd] = real[even] - tr
                    imaginary[odd] = imaginary[even] - ti
                    real[even] += tr
                    imaginary[even] += ti
                    val next = ur * wr - ui * wi
                    ui = ur * wi + ui * wr
                    ur = next
                }
                offset += length
            }
            length = length shl 1
        }
    }
}

/** Streaming mono descriptors with a bounded frame-energy envelope for tempo estimation. */
class PcmAudioAnalysis(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) {
    private val frame = DoubleArray(FRAME_SIZE)
    private val spectralReal = DoubleArray(FRAME_SIZE)
    private val spectralImaginary = DoubleArray(FRAME_SIZE)
    private val spectralPowers = DoubleArray(FRAME_SIZE / 2 + 1)
    private val hann = DoubleArray(FRAME_SIZE) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (FRAME_SIZE - 1)) }
    private var filled = 0
    private var sumSquares = 0.0
    private var crossings = 0L
    private var previous: Double? = null
    private val energies = ArrayList<Double>()
    private var silentFrames = 0
    private var centroidSum = 0.0
    private var rolloffSum = 0.0
    private var spectralFrames = 0
    var sampleCount = 0L
        private set

    fun accept(sample: Double) {
        require(sample.isFinite())
        val safe = sample.coerceIn(-1.0, 1.0)
        previous?.let { if ((it < 0.0) != (safe < 0.0)) crossings++ }
        previous = safe
        sumSquares += safe * safe
        sampleCount++
        frame[filled++] = safe
        if (filled == FRAME_SIZE) analyzeFrame()
    }

    fun finish(): DoubleArray? {
        if (sampleCount == 0L) return null
        if (filled > 0) analyzeFrame()
        val sorted = energies.sorted()
        val low = sorted[((sorted.size - 1) * 0.10).toInt()]
        val high = sorted[((sorted.size - 1) * 0.90).toInt()]
        val tempo = estimateTempo()
        return doubleArrayOf(
            sqrt(sumSquares / sampleCount).coerceIn(0.0, 1.0),
            crossings.toDouble() / (sampleCount - 1).coerceAtLeast(1L),
            silentFrames.toDouble() / energies.size,
            (high - low).coerceIn(0.0, 1.0),
            if (spectralFrames == 0) 0.0 else centroidSum / spectralFrames,
            if (spectralFrames == 0) 0.0 else rolloffSum / spectralFrames,
            tempo?.let { AudioFeatureSchema.normalizeTempoBpm(it.first) } ?: 0.0,
            tempo?.second ?: 0.0,
        )
    }

    private fun analyzeFrame() {
        val count = filled
        var energy = 0.0
        for (i in 0 until count) energy += frame[i] * frame[i]
        val rms = sqrt(energy / count)
        energies += rms
        if (rms < SILENCE_THRESHOLD) {
            silentFrames++
        } else {
            val real = spectralReal
            val imaginary = spectralImaginary
            real.fill(0.0); imaginary.fill(0.0)
            for (i in 0 until count) real[i] = frame[i] * hann[i]
            Radix2Fft.transform(real, imaginary)
            val powers = spectralPowers
            for (i in powers.indices) powers[i] = real[i] * real[i] + imaginary[i] * imaginary[i]
            val total = powers.sum()
            if (total > 1e-12) {
                var weighted = 0.0
                var cumulative = 0.0
                var rolloff = powers.lastIndex
                var found = false
                powers.forEachIndexed { bin, power ->
                    weighted += bin * power
                    cumulative += power
                    if (!found && cumulative >= 0.85 * total) { rolloff = bin; found = true }
                }
                centroidSum += (weighted / total / powers.lastIndex).coerceIn(0.0, 1.0)
                rolloffSum += rolloff.toDouble() / powers.lastIndex
                spectralFrames++
            }
        }
        filled = 0
    }

    private fun estimateTempo(): Pair<Double, Double>? {
        if (energies.size < MIN_TEMPO_FRAMES || sampleRate <= 0) return null
        val onsets = DoubleArray(energies.size - 1) { index ->
            max(0.0, energies[index + 1] - energies[index])
        }
        val onsetPower = onsets.sumOf { it * it }
        if (onsetPower <= 1e-8) return null

        val framesPerSecond = sampleRate.toDouble() / FRAME_SIZE
        val minLag = (framesPerSecond * 60.0 / AudioFeatureSchema.MAX_TEMPO_BPM).toInt().coerceAtLeast(1)
        val maxLag = (framesPerSecond * 60.0 / AudioFeatureSchema.MIN_TEMPO_BPM).toInt()
            .coerceAtMost(onsets.size - 2)
        if (minLag > maxLag) return null

        var bestLag = -1
        var bestCorrelation = 0.0
        for (lag in minLag..maxLag) {
            var dot = 0.0
            var leftPower = 0.0
            var rightPower = 0.0
            for (i in lag until onsets.size) {
                val left = onsets[i]
                val right = onsets[i - lag]
                dot += left * right
                leftPower += left * left
                rightPower += right * right
            }
            val denominator = sqrt(leftPower * rightPower)
            if (denominator <= 1e-10) continue
            val correlation = (dot / denominator).coerceIn(0.0, 1.0)
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        if (bestLag <= 0) return null
        val bpm = (60.0 * framesPerSecond / bestLag)
            .coerceIn(AudioFeatureSchema.MIN_TEMPO_BPM, AudioFeatureSchema.MAX_TEMPO_BPM)
        val confidence = ((bestCorrelation - MIN_TEMPO_CORRELATION) / (1.0 - MIN_TEMPO_CORRELATION))
            .coerceIn(0.0, 1.0)
        return bpm to confidence
    }

    companion object {
        const val FRAME_SIZE = 1024
        const val SILENCE_THRESHOLD = 0.01
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val MIN_TEMPO_FRAMES = 24
        const val MIN_TEMPO_CORRELATION = 0.15
    }
}

interface TrackSimilarityCalculator {
    fun similarity(first: SimilarityProfile, second: SimilarityProfile): SimilarityEvidence
}
data class SimilarityProfile(val artist: String?, val album: String?, val audio: DoubleArray?)
data class SimilarityEvidence(val score: Double, val sameArtist: Boolean, val sameAlbum: Boolean, val audioSimilarity: Double?)

class LocalTrackSimilarityCalculator : TrackSimilarityCalculator {
    override fun similarity(first: SimilarityProfile, second: SimilarityProfile): SimilarityEvidence {
        val artist = !first.artist.isNullOrBlank() && first.artist == second.artist
        val album = artist && !first.album.isNullOrBlank() && first.album == second.album
        val a = first.audio?.takeIf(AudioFeatureSchema::isCompatible)
        val b = second.audio?.takeIf(AudioFeatureSchema::isCompatible)
        val audio = if (a != null && b != null) {
            (1.0 - sqrt(a.indices.sumOf { i -> (a[i] - b[i]) * (a[i] - b[i]) } / a.size)).coerceIn(0.0, 1.0)
        } else null
        val score = (if (artist) 0.35 else 0.0) + (if (album) 0.15 else 0.0) + (audio?.times(0.50) ?: 0.0)
        return SimilarityEvidence(score, artist, album, audio)
    }
}
