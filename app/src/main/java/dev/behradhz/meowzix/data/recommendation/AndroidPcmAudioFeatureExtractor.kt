package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureSource
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureVector
import dev.behradhz.meowzix.domain.recommendation.AudioVectorFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight, source-agnostic PCM analysis for the first learned model.
 *
 * It decodes only a short prefix and stores normalized signal summaries rather than raw audio. The
 * vector is deliberately small so extraction remains cheap and the model can later swap in a richer
 * extractor without changing recommendation/training interfaces.
 */
@Singleton
class AndroidPcmAudioFeatureExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AudioFeatureExtractor {
    override val extractorName: String = "android-pcm-summary"
    override val extractorVersion: String = "1"
    override val schemaVersion: Int = 1

    override suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? =
        withContext(Dispatchers.IO) {
            runCatching { decode(trackId, source) }.getOrNull()
        }

    private fun decode(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            when {
                !source.contentUri.isNullOrBlank() -> extractor.setDataSource(
                    context,
                    Uri.parse(source.contentUri),
                    emptyMap(),
                )
                !source.localPath.isNullOrBlank() -> extractor.setDataSource(source.localPath)
                else -> return null
            }

            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    trackIndex = index
                    inputFormat = candidate
                    break
                }
            }
            val format = inputFormat ?: return null
            if (trackIndex < 0) return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)

            val accumulator = PcmAccumulator(
                sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: DEFAULT_SAMPLE_RATE,
                channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1,
                bitrate = format.intOrNull(MediaFormat.KEY_BIT_RATE) ?: 0,
                durationUs = format.longOrNull(MediaFormat.KEY_DURATION) ?: 0L,
            )

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var timeoutStreak = 0

            while (!outputDone && !accumulator.full && timeoutStreak < MAX_TIMEOUT_STREAK) {
                var progressed = false
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                        if (input == null) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            input.clear()
                            val sampleSize = extractor.readSampleData(input, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime.coerceAtLeast(0L),
                                    0,
                                )
                                extractor.advance()
                            }
                        }
                        progressed = true
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        accumulator.updateFormat(decoder.outputFormat)
                        progressed = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = decoder.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            accumulator.accept(output, info)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                        progressed = true
                    }
                }

                timeoutStreak = if (progressed) 0 else timeoutStreak + 1
            }

            if (accumulator.sampleCount == 0L) return null
            return AudioFeatureVector(
                id = UUID.randomUUID(),
                trackId = trackId,
                sourceIdUsed = source.sourceId,
                extractorName = extractorName,
                extractorVersion = extractorVersion,
                schemaVersion = schemaVersion,
                vectorFormat = AudioVectorFormat.FLOAT64_LE,
                values = accumulator.vector(),
                generatedAt = Instant.now(),
                sourceContentHash = source.contentHashSha256,
            )
        } finally {
            decoder?.let { codec ->
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
            extractor.release()
        }
    }

    private class PcmAccumulator(
        sampleRate: Int,
        channelCount: Int,
        private val bitrate: Int,
        private val durationUs: Long,
    ) {
        private var sampleRate = sampleRate.coerceAtLeast(1)
        private var channelCount = channelCount.coerceAtLeast(1)
        private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        private var sumAbs = 0.0
        private var sumSquares = 0.0
        private var peak = 0.0
        private var zeroCrossings = 0L
        private var previous: Double? = null
        private val segmentSquares = DoubleArray(SEGMENTS)
        private val segmentCounts = LongArray(SEGMENTS)
        var sampleCount: Long = 0L
            private set

        val full: Boolean
            get() = sampleCount >= targetSamples()

        fun updateFormat(format: MediaFormat) {
            sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.coerceAtLeast(1) ?: sampleRate
            channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.coerceAtLeast(1) ?: channelCount
            pcmEncoding = format.intOrNull(MediaFormat.KEY_PCM_ENCODING) ?: pcmEncoding
        }

        fun accept(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            val safeStart = info.offset.coerceIn(0, buffer.capacity())
            val safeEnd = (info.offset + info.size).coerceIn(safeStart, buffer.capacity())
            val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            data.position(safeStart)
            data.limit(safeEnd)

            when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> while (data.remaining() >= Float.SIZE_BYTES && !full) {
                    add(data.float.toDouble().coerceIn(-1.0, 1.0))
                }
                AudioFormat.ENCODING_PCM_8BIT -> while (data.hasRemaining() && !full) {
                    val unsigned = data.get().toInt() and 0xff
                    add(((unsigned - 128) / 128.0).coerceIn(-1.0, 1.0))
                }
                else -> while (data.remaining() >= Short.SIZE_BYTES && !full) {
                    add((data.short / 32768.0).coerceIn(-1.0, 1.0))
                }
            }
        }

        private fun add(sample: Double) {
            val absolute = kotlin.math.abs(sample)
            sumAbs += absolute
            sumSquares += sample * sample
            peak = maxOf(peak, absolute)
            previous?.let { prior ->
                if ((prior < 0.0 && sample >= 0.0) || (prior >= 0.0 && sample < 0.0)) zeroCrossings++
            }
            previous = sample

            val segment = ((sampleCount * SEGMENTS) / targetSamples().coerceAtLeast(1L))
                .toInt()
                .coerceIn(0, SEGMENTS - 1)
            segmentSquares[segment] += sample * sample
            segmentCounts[segment]++
            sampleCount++
        }

        fun vector(): DoubleArray {
            val count = sampleCount.coerceAtLeast(1L).toDouble()
            val meanAbs = (sumAbs / count).coerceIn(0.0, 1.0)
            val rms = sqrt(sumSquares / count).coerceIn(0.0, 1.0)
            val zcr = (zeroCrossings.toDouble() / sampleCount.coerceAtLeast(2L)).coerceIn(0.0, 1.0)
            val crest = if (rms > 1e-6) (peak / rms / 10.0).coerceIn(0.0, 1.0) else 0.0
            val segmentRms = DoubleArray(SEGMENTS) { index ->
                val segmentCount = segmentCounts[index]
                if (segmentCount == 0L) 0.0 else sqrt(segmentSquares[index] / segmentCount).coerceIn(0.0, 1.0)
            }
            val dynamic = ((segmentRms.maxOrNull() ?: 0.0) - (segmentRms.minOrNull() ?: 0.0))
                .coerceIn(0.0, 1.0)

            return doubleArrayOf(
                meanAbs,
                rms,
                peak.coerceIn(0.0, 1.0),
                zcr,
                crest,
                dynamic,
                segmentRms[0],
                segmentRms[1],
                segmentRms[2],
                segmentRms[3],
                segmentRms[4],
                segmentRms[5],
                (sampleRate / 96_000.0).coerceIn(0.0, 1.0),
                (channelCount / 8.0).coerceIn(0.0, 1.0),
                (bitrate / 512_000.0).coerceIn(0.0, 1.0),
                (durationUs / (20.0 * 60.0 * 1_000_000.0)).coerceIn(0.0, 1.0),
            )
        }

        private fun targetSamples(): Long =
            (sampleRate.toLong() * channelCount.toLong() * ANALYSIS_SECONDS).coerceAtLeast(1L)
    }

    private companion object {
        const val ANALYSIS_SECONDS = 12L
        const val SEGMENTS = 6
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_TIMEOUT_STREAK = 50
    }
}

private fun MediaFormat.intOrNull(key: String): Int? =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

private fun MediaFormat.longOrNull(key: String): Long? =
    if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
