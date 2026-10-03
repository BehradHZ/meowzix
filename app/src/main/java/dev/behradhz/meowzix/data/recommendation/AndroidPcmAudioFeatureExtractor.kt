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
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureSchema
import dev.behradhz.meowzix.domain.recommendation.PcmAudioAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    override val extractorVersion: String = "2"
    override val schemaVersion: Int = AudioFeatureSchema.VERSION

    override suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? =
        withContext(Dispatchers.IO) {
            try { decode(trackId, source) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        }

    private suspend fun decode(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? {
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

            if (mime == "audio/raw") {
                accumulator.updateFormat(format)
                val pcm = ByteBuffer.allocate(1_048_576)
                val info = MediaCodec.BufferInfo()
                while (!accumulator.full) {
                    currentCoroutineContext().ensureActive()
                    pcm.clear()
                    val size = extractor.readSampleData(pcm, 0)
                    if (size <= 0) break
                    info.set(0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                    accumulator.accept(pcm, info)
                    if (!extractor.advance()) break
                }
            } else {
                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()

                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var timeoutStreak = 0

                while (!outputDone && !accumulator.full && timeoutStreak < MAX_TIMEOUT_STREAK) {
                    currentCoroutineContext().ensureActive()
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
                values = accumulator.vector() ?: return null,
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
        @Suppress("UNUSED_PARAMETER") bitrate: Int,
        @Suppress("UNUSED_PARAMETER") durationUs: Long,
    ) {
        private var sampleRate = sampleRate.coerceAtLeast(1)
        private var channelCount = channelCount.coerceAtLeast(1)
        private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        private val analysis = PcmAudioAnalysis()
        private var channelSum = 0.0
        private var channelsRead = 0
        val sampleCount: Long get() = analysis.sampleCount
        val full: Boolean get() = sampleCount >= sampleRate.toLong() * ANALYSIS_SECONDS

        fun updateFormat(format: MediaFormat) {
            sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.coerceAtLeast(1) ?: sampleRate
            channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.coerceAtLeast(1) ?: channelCount
            pcmEncoding = format.intOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
            require(pcmEncoding in setOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_FLOAT)) {
                "Unsupported PCM encoding"
            }
        }

        fun accept(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            data.position(info.offset)
            data.limit(info.offset + info.size)
            val bytes = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> Float.SIZE_BYTES
                AudioFormat.ENCODING_PCM_8BIT -> 1
                else -> Short.SIZE_BYTES
            }
            while (data.remaining() >= bytes && !full) {
                val sample = when (pcmEncoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> data.float.toDouble()
                    AudioFormat.ENCODING_PCM_8BIT -> ((data.get().toInt() and 0xff) - 128) / 128.0
                    else -> data.short / 32768.0
                }
                require(sample.isFinite())
                channelSum += sample.coerceIn(-1.0, 1.0)
                channelsRead++
                if (channelsRead == channelCount) {
                    analysis.accept(channelSum / channelCount)
                    channelsRead = 0
                    channelSum = 0.0
                }
            }
        }
        fun vector(): DoubleArray? = analysis.finish()
    }

    private companion object {
        const val ANALYSIS_SECONDS = 30L
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_TIMEOUT_STREAK = 50
    }
}

private fun MediaFormat.intOrNull(key: String): Int? =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

private fun MediaFormat.longOrNull(key: String): Long? =
    if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
