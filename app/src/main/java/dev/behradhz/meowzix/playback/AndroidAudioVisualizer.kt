package dev.behradhz.meowzix.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.data.telegram.TdLibClientAdapter
import dev.behradhz.meowzix.domain.playback.AUDIO_SPECTRUM_BAND_COUNT
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@Singleton
class AndroidAudioVisualizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioVisualizerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _spectrum = MutableStateFlow(AudioSpectrumState())
    override val spectrum: StateFlow<AudioSpectrumState> = _spectrum.asStateFlow()
    private var analysisJob: Job? = null
    private val cache = object : LinkedHashMap<String, FloatArray>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > 24
    }

    override fun analyze(sourceUri: String?) {
        if (sourceUri.isNullOrBlank()) {
            analysisJob?.cancel()
            _spectrum.value = AudioSpectrumState()
            return
        }
        synchronized(cache) { cache[sourceUri]?.copyOf() }?.let { cached ->
            _spectrum.value = AudioSpectrumState(bands = cached, sourceUri = sourceUri)
            return
        }
        analysisJob?.cancel()
        analysisJob = scope.launch {
            _spectrum.value = AudioSpectrumState(sourceUri = sourceUri, isAnalyzing = true)
            val resolvedUri = runCatching { resolveReadableUri(sourceUri) }.getOrNull()
            val result = resolvedUri?.let { uri ->
                runCatching { decodePcmWaveform(uri) }
                    .getOrNull()
                    ?.takeIf { bands -> bands.any { it > 0.001f } }
                    ?: runCatching { fileByteFallback(uri) }.getOrNull()
            }
            if (result != null) {
                synchronized(cache) { cache[sourceUri] = result.copyOf() }
                _spectrum.value = AudioSpectrumState(
                    bands = result,
                    sourceUri = sourceUri,
                    isAnalyzing = false,
                )
            } else {
                _spectrum.value = AudioSpectrumState(
                    sourceUri = sourceUri,
                    isAnalyzing = false,
                    errorMessage = "Waveform becomes available as the track buffers",
                )
            }
        }
    }

    override fun release() {
        // This repository is a process singleton. PlaybackService can be recreated, so cancelling
        // the backing scope here would permanently disable waveform analysis for the next service.
        analysisJob?.cancel()
        analysisJob = null
        synchronized(cache) { cache.clear() }
        _spectrum.value = AudioSpectrumState()
    }

    private suspend fun resolveReadableUri(sourceUri: String): Uri {
        val uri = Uri.parse(sourceUri)
        if (uri.scheme != TDLIB_SCHEME) return uri
        val fileId = uri.lastPathSegment?.toIntOrNull() ?: return uri
        val client = TdLibClientAdapter.activeOrNull() ?: return uri
        val file = client.send(TdApi.GetFile(fileId))
        val path = file.local.path
        return if (path.isNotBlank() && File(path).isFile) Uri.fromFile(File(path)) else uri
    }

    private fun decodePcmWaveform(uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return FloatArray(AUDIO_SPECTRUM_BAND_COUNT)
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: return FloatArray(AUDIO_SPECTRUM_BAND_COUNT)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val info = MediaCodec.BufferInfo()
            val energy = ArrayList<Float>(384)
            var inputEnded = false
            var outputEnded = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var decodedUs = 0L

            while (!outputEnded && decodedUs < MAX_DECODE_US && energy.size < MAX_ENERGY_POINTS) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEnded = true
                            } else {
                                val sampleTime = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                                decodedUs = sampleTime
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            energy += rms(output.slice().order(ByteOrder.nativeOrder()), pcmEncoding)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            return resample(energy, AUDIO_SPECTRUM_BAND_COUNT)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun rms(buffer: ByteBuffer, encoding: Int): Float {
        if (!buffer.hasRemaining()) return 0f
        var sum = 0.0
        var count = 0
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                while (buffer.remaining() >= 4 && count < MAX_SAMPLES_PER_BUFFER) {
                    val sample = buffer.getFloat().coerceIn(-1f, 1f)
                    sum += sample * sample
                    count++
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                while (buffer.hasRemaining() && count < MAX_SAMPLES_PER_BUFFER) {
                    val sample = ((buffer.get().toInt() and 0xFF) - 128) / 128.0
                    sum += sample * sample
                    count++
                }
            }
            else -> {
                while (buffer.remaining() >= 2 && count < MAX_SAMPLES_PER_BUFFER) {
                    val sample = buffer.getShort() / 32768.0
                    sum += sample * sample
                    count++
                }
            }
        }
        return if (count == 0) 0f else sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }

    private fun resample(values: List<Float>, count: Int): FloatArray {
        if (values.isEmpty()) return FloatArray(count)
        val result = FloatArray(count)
        for (band in 0 until count) {
            val start = (band * values.size / count).coerceIn(0, values.lastIndex)
            val endExclusive = (((band + 1) * values.size + count - 1) / count)
                .coerceIn(start + 1, values.size)
            var peak = 0f
            var sum = 0f
            for (index in start until endExclusive) {
                val value = values[index]
                peak = maxOf(peak, value)
                sum += value
            }
            val average = sum / (endExclusive - start)
            result[band] = (0.62f * peak + 0.38f * average)
                .coerceIn(0.025f, 1f)
        }
        normalize(result)
        return result
    }

    private fun fileByteFallback(uri: Uri): FloatArray {
        val buckets = FloatArray(AUDIO_SPECTRUM_BAND_COUNT)
        val counts = IntArray(AUDIO_SPECTRUM_BAND_COUNT)
        val buffer = ByteArray(8192)
        var total = 0
        openInputStream(uri)?.buffered()?.use { input ->
            while (total < FALLBACK_MAX_BYTES) {
                val read = input.read(buffer, 0, minOf(buffer.size, FALLBACK_MAX_BYTES - total))
                if (read <= 0) break
                for (index in 0 until read) {
                    val bucket = ((total + index) / 256) % AUDIO_SPECTRUM_BAND_COUNT
                    val centered = abs((buffer[index].toInt() and 0xFF) - 128) / 128f
                    buckets[bucket] += centered
                    counts[bucket]++
                }
                total += read
            }
        }
        for (index in buckets.indices) {
            buckets[index] = if (counts[index] == 0) 0.025f else {
                (buckets[index] / counts[index]).coerceIn(0.025f, 1f)
            }
        }
        normalize(buckets)
        return buckets
    }

    private fun openInputStream(uri: Uri): InputStream? = when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }

    private fun normalize(values: FloatArray) {
        val max = values.maxOrNull()?.takeIf { it > 0f } ?: return
        for (index in values.indices) {
            values[index] = (values[index] / max).coerceIn(0.025f, 1f)
        }
    }

    private companion object {
        const val TDLIB_SCHEME = "meowzix-tdlib"
        const val CODEC_TIMEOUT_US = 8_000L
        const val MAX_DECODE_US = 45_000_000L
        const val MAX_ENERGY_POINTS = 512
        const val MAX_SAMPLES_PER_BUFFER = 4096
        const val FALLBACK_MAX_BYTES = 192 * 1024
    }
}
