package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureSchema
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureSource
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureVector
import dev.behradhz.meowzix.domain.recommendation.AudioVectorFormat
import dev.behradhz.meowzix.domain.recommendation.PcmAudioAnalysis
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Bounded source-agnostic PCM analysis.
 *
 * At most three 10-second representative regions are decoded: shortly after the intro, near the
 * middle, and near the end. Short or effectively unseekable tracks degrade to whatever regions can
 * be read safely. Raw audio is never persisted; only the compact normalized schema-v3 vector is.
 */
@Singleton
class AndroidPcmAudioFeatureExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AudioFeatureExtractor {
    override val extractorName: String = "android-pcm-summary"
    override val extractorVersion: String = "3-multisegment-3x10s"
    override val schemaVersion: Int = AudioFeatureSchema.VERSION

    override suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? =
        withContext(Dispatchers.IO) {
            try {
                decode(trackId, source)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun decode(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? {
        val probe = probe(source) ?: return null
        val vectors = representativeStarts(probe.durationUs).mapNotNull { startUs ->
            currentCoroutineContext().ensureActive()
            runCatching { decodeSegment(source, probe, startUs) }.getOrNull()
        }
        if (vectors.isEmpty()) return null
        val combined = combineSegments(vectors) ?: return null
        return AudioFeatureVector(
            id = UUID.randomUUID(),
            trackId = trackId,
            sourceIdUsed = source.sourceId,
            extractorName = extractorName,
            extractorVersion = extractorVersion,
            schemaVersion = schemaVersion,
            vectorFormat = AudioVectorFormat.FLOAT64_LE,
            values = combined,
            generatedAt = Instant.now(),
            sourceContentHash = source.contentHashSha256,
        )
    }

    private fun probe(source: AudioFeatureSource): AudioProbe? {
        val extractor = MediaExtractor()
        return try {
            setDataSource(extractor, source)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    return AudioProbe(
                        trackIndex = index,
                        mime = mime,
                        sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: DEFAULT_SAMPLE_RATE,
                        channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1,
                        durationUs = format.longOrNull(MediaFormat.KEY_DURATION) ?: 0L,
                    )
                }
            }
            null
        } finally {
            extractor.release()
        }
    }

    private suspend fun decodeSegment(source: AudioFeatureSource, probe: AudioProbe, startUs: Long): DoubleArray? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            setDataSource(extractor, source)
            extractor.selectTrack(probe.trackIndex)
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
            val format = extractor.getTrackFormat(probe.trackIndex)
            val accumulator = PcmAccumulator(
                sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: probe.sampleRate,
                channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: probe.channelCount,
                analysisSeconds = SEGMENT_SECONDS,
            )

            if (probe.mime == "audio/raw") {
                accumulator.updateFormat(format)
                val pcm = ByteBuffer.allocate(BUFFER_BYTES)
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
                decoder = MediaCodec.createDecoderByType(probe.mime)
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
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                input.clear()
                                val sampleSize = extractor.readSampleData(input, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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
                            if (output != null && info.size > 0) accumulator.accept(output, info)
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            decoder.releaseOutputBuffer(outputIndex, false)
                            progressed = true
                        }
                    }
                    timeoutStreak = if (progressed) 0 else timeoutStreak + 1
                }
            }
            accumulator.vector()
        } finally {
            decoder?.let { codec ->
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
            extractor.release()
        }
    }

    private fun setDataSource(extractor: MediaExtractor, source: AudioFeatureSource) {
        when {
            !source.contentUri.isNullOrBlank() -> extractor.setDataSource(
                context,
                Uri.parse(source.contentUri),
                emptyMap(),
            )
            !source.localPath.isNullOrBlank() -> extractor.setDataSource(source.localPath)
            else -> error("No readable source")
        }
    }

    private fun representativeStarts(durationUs: Long): List<Long> {
        if (durationUs <= 0L || durationUs <= SHORT_TRACK_US) return listOf(0L)
        val latestStart = (durationUs - SEGMENT_US).coerceAtLeast(0L)
        val starts = listOf(
            INTRO_SKIP_US.coerceAtMost(latestStart),
            (durationUs / 2L - SEGMENT_US / 2L).coerceIn(0L, latestStart),
            (durationUs - SEGMENT_US - OUTRO_MARGIN_US).coerceIn(0L, latestStart),
        )
        return starts.distinct().take(MAX_SEGMENTS)
    }

    /**
     * Stable mean for general descriptors. Tempo is confidence-weighted so a segment where no
     * reliable pulse was measurable does not drag a valid BPM toward 60 BPM.
     */
    private fun combineSegments(vectors: List<DoubleArray>): DoubleArray? {
        val compatible = vectors.filter(AudioFeatureSchema::isCompatible)
        if (compatible.isEmpty()) return null
        val result = DoubleArray(AudioFeatureSchema.names.size)
        for (index in 0 until AudioFeatureSchema.TEMPO_BPM) {
            result[index] = compatible.map { it[index] }.average().coerceIn(0.0, 1.0)
        }
        val tempoWeight = compatible.sumOf { it[AudioFeatureSchema.TEMPO_CONFIDENCE] }
        result[AudioFeatureSchema.TEMPO_BPM] = if (tempoWeight > 1e-8) {
            compatible.sumOf {
                it[AudioFeatureSchema.TEMPO_BPM] * it[AudioFeatureSchema.TEMPO_CONFIDENCE]
            } / tempoWeight
        } else 0.0
        result[AudioFeatureSchema.TEMPO_CONFIDENCE] = compatible
            .map { it[AudioFeatureSchema.TEMPO_CONFIDENCE] }
            .average()
            .coerceIn(0.0, 1.0)
        return result.takeIf(AudioFeatureSchema::isCompatible)
    }

    private data class AudioProbe(
        val trackIndex: Int,
        val mime: String,
        val sampleRate: Int,
        val channelCount: Int,
        val durationUs: Long,
    )

    private class PcmAccumulator(
        sampleRate: Int,
        channelCount: Int,
        private val analysisSeconds: Long,
    ) {
        private var sampleRate = sampleRate.coerceAtLeast(1)
        private var channelCount = channelCount.coerceAtLeast(1)
        private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        private var analysis = PcmAudioAnalysis(this.sampleRate)
        private var channelSum = 0.0
        private var channelsRead = 0
        val sampleCount: Long get() = analysis.sampleCount
        val full: Boolean get() = sampleCount >= sampleRate.toLong() * analysisSeconds

        fun updateFormat(format: MediaFormat) {
            val newSampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.coerceAtLeast(1) ?: sampleRate
            channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.coerceAtLeast(1) ?: channelCount
            pcmEncoding = format.intOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
            require(pcmEncoding in setOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_FLOAT)) {
                "Unsupported PCM encoding"
            }
            if (sampleCount == 0L && newSampleRate != sampleRate) {
                sampleRate = newSampleRate
                analysis = PcmAudioAnalysis(sampleRate)
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
        const val SEGMENT_SECONDS = 10L
        const val SEGMENT_US = SEGMENT_SECONDS * 1_000_000L
        const val MAX_SEGMENTS = 3
        const val INTRO_SKIP_US = 3_000_000L
        const val OUTRO_MARGIN_US = 3_000_000L
        const val SHORT_TRACK_US = 24_000_000L
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val BUFFER_BYTES = 1_048_576
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_TIMEOUT_STREAK = 50
    }
}

private fun MediaFormat.intOrNull(key: String): Int? =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

private fun MediaFormat.longOrNull(key: String): Long? =
    if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
