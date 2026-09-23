package dev.behradhz.meowzix.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.playback.AUDIO_SPECTRUM_BAND_COUNT
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidAudioVisualizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioVisualizerRepository {
    private val lock = Any()
    private val _spectrum = MutableStateFlow(AudioSpectrumState())
    override val spectrum: StateFlow<AudioSpectrumState> = _spectrum.asStateFlow()

    private var visualizer: Visualizer? = null
    private var audioSessionId: Int = NO_AUDIO_SESSION
    private var sourceUri: String? = null
    private val smoothedBands = FloatArray(AUDIO_SPECTRUM_BAND_COUNT)

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer?,
            waveform: ByteArray?,
            samplingRate: Int,
        ) = Unit

        override fun onFftDataCapture(
            visualizer: Visualizer?,
            fft: ByteArray?,
            samplingRate: Int,
        ) {
            if (fft == null || fft.size < 4) return
            synchronized(lock) {
                if (this@AndroidAudioVisualizer.visualizer !== visualizer) return
                val uri = sourceUri ?: return
                _spectrum.value = AudioSpectrumState(
                    bands = fftToBandsLocked(fft),
                    sourceUri = uri,
                )
            }
        }
    }

    override fun analyze(sourceUri: String?) {
        synchronized(lock) {
            this.sourceUri = sourceUri
            smoothedBands.fill(0f)
            if (sourceUri.isNullOrBlank()) {
                _spectrum.value = AudioSpectrumState()
                return
            }
            val hasPermission = hasRecordAudioPermission()
            _spectrum.value = AudioSpectrumState(
                sourceUri = sourceUri,
                isAnalyzing = visualizer == null && hasPermission,
                errorMessage = if (hasPermission) null else PERMISSION_ERROR,
            )
        }
        refresh()
    }

    override fun attachToAudioSession(audioSessionId: Int) {
        synchronized(lock) {
            if (this.audioSessionId == audioSessionId && visualizer != null) return
            releaseVisualizerLocked()
            this.audioSessionId = audioSessionId
            smoothedBands.fill(0f)
        }
        refresh()
    }

    override fun refresh() {
        synchronized(lock) {
            val uri = sourceUri
            if (uri.isNullOrBlank()) {
                _spectrum.value = AudioSpectrumState()
                return
            }
            if (!hasRecordAudioPermission()) {
                releaseVisualizerLocked()
                _spectrum.value = AudioSpectrumState(
                    sourceUri = uri,
                    errorMessage = PERMISSION_ERROR,
                )
                return
            }
            if (audioSessionId <= 0) {
                _spectrum.value = AudioSpectrumState(
                    sourceUri = uri,
                    isAnalyzing = true,
                )
                return
            }
            if (visualizer != null) {
                _spectrum.value = _spectrum.value.copy(
                    sourceUri = uri,
                    isAnalyzing = false,
                    errorMessage = null,
                )
                return
            }

            val started = startVisualizerLocked()
            _spectrum.value = AudioSpectrumState(
                sourceUri = uri,
                isAnalyzing = false,
                errorMessage = if (started) null else VISUALIZER_ERROR,
            )
        }
    }

    override fun release() {
        synchronized(lock) {
            releaseVisualizerLocked()
            audioSessionId = NO_AUDIO_SESSION
            sourceUri = null
            smoothedBands.fill(0f)
            _spectrum.value = AudioSpectrumState()
        }
    }

    private fun startVisualizerLocked(): Boolean {
        if (visualizer != null) return true
        if (audioSessionId <= 0 || !hasRecordAudioPermission()) return false

        val created = try {
            Visualizer(audioSessionId)
        } catch (_: RuntimeException) {
            return false
        } catch (_: UnsupportedOperationException) {
            return false
        }

        return try {
            val captureRange = Visualizer.getCaptureSizeRange()
            if (captureRange.size >= 2) created.captureSize = captureRange[1]
            created.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            val listenerStatus = created.setDataCaptureListener(
                captureListener,
                Visualizer.getMaxCaptureRate(),
                false,
                true,
            )
            if (listenerStatus != Visualizer.SUCCESS) {
                throw IllegalStateException("Visualizer listener setup failed: $listenerStatus")
            }
            created.enabled = true
            visualizer = created
            true
        } catch (_: RuntimeException) {
            runCatching { created.enabled = false }
            runCatching { created.release() }
            false
        }
    }

    private fun releaseVisualizerLocked() {
        val current = visualizer ?: return
        visualizer = null
        runCatching { current.enabled = false }
        runCatching { current.release() }
    }

    private fun fftToBandsLocked(fft: ByteArray): FloatArray {
        val binCount = fft.size / 2
        if (binCount <= 1) return FloatArray(AUDIO_SPECTRUM_BAND_COUNT)

        val bands = FloatArray(AUDIO_SPECTRUM_BAND_COUNT)
        for (band in bands.indices) {
            val start = mappedBin(band, binCount)
            val endExclusive = if (band == bands.lastIndex) {
                binCount
            } else {
                mappedBin(band + 1, binCount)
                    .coerceAtLeast(start + 1)
                    .coerceAtMost(binCount)
            }

            var peak = 0f
            var sum = 0f
            var count = 0
            for (bin in start until endExclusive) {
                val real = fft[bin * 2].toInt()
                val imaginary = fft[bin * 2 + 1].toInt()
                val magnitude = sqrt((real * real + imaginary * imaginary).toFloat())
                peak = maxOf(peak, magnitude)
                sum += magnitude
                count++
            }

            val average = if (count == 0) 0f else sum / count
            val mixed = 0.72f * peak + 0.28f * average
            val target = sqrt((mixed / FFT_MAX_MAGNITUDE).coerceIn(0f, 1f))
            val previous = smoothedBands[band]
            val smoothing = if (target >= previous) ATTACK else RELEASE
            val smoothed = previous + (target - previous) * smoothing
            smoothedBands[band] = if (smoothed < NOISE_FLOOR) 0f else smoothed
            bands[band] = smoothedBands[band]
        }
        return bands
    }

    private fun mappedBin(boundary: Int, binCount: Int): Int {
        if (binCount <= 2) return 1
        val ratio = boundary.toDouble() / AUDIO_SPECTRUM_BAND_COUNT.toDouble()
        return (1 + ((binCount - 2) * ratio.pow(LOG_DISTRIBUTION_EXPONENT)).toInt())
            .coerceIn(1, binCount - 1)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val NO_AUDIO_SESSION = -1
        const val FFT_MAX_MAGNITUDE = 181.02f
        const val LOG_DISTRIBUTION_EXPONENT = 1.75
        const val ATTACK = 0.68f
        const val RELEASE = 0.20f
        const val NOISE_FLOOR = 0.012f
        const val PERMISSION_ERROR = "Allow microphone access for the live equalizer"
        const val VISUALIZER_ERROR = "Live equalizer is unavailable on this audio output"
    }
}
