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
import kotlin.math.hypot
import kotlin.math.ln1p
import kotlin.math.pow
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
    private var sessionId: Int = 0
    private var captureRequested: Boolean = false
    private val smoothedBands = FloatArray(AUDIO_SPECTRUM_BAND_COUNT)

    override fun attachToSession(audioSessionId: Int) {
        synchronized(lock) {
            if (sessionId == audioSessionId) return
            sessionId = audioSessionId.coerceAtLeast(0)
            releaseVisualizerLocked(clearSession = false)
            _spectrum.value = AudioSpectrumState(sessionId = sessionId)
            if (captureRequested) startVisualizerLocked()
        }
    }

    override fun setCaptureEnabled(enabled: Boolean) {
        synchronized(lock) {
            captureRequested = enabled
            if (enabled) {
                startVisualizerLocked()
            } else {
                releaseVisualizerLocked(clearSession = false)
                clearSpectrumLocked()
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            captureRequested = false
            releaseVisualizerLocked(clearSession = true)
            clearSpectrumLocked()
        }
    }

    private fun startVisualizerLocked() {
        if (!captureRequested || visualizer != null || sessionId <= 0) return

        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _spectrum.value = AudioSpectrumState(
                sessionId = sessionId,
                errorMessage = "Live spectrum permission is required",
            )
            return
        }

        runCatching {
            Visualizer(sessionId).apply {
                enabled = false
                captureSize = Visualizer.getCaptureSizeRange()[1]
                scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
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
                            if (fft != null) publishFft(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    false,
                    true,
                )
                enabled = true
            }
        }.onSuccess { activeVisualizer ->
            visualizer = activeVisualizer
            _spectrum.value = AudioSpectrumState(
                bands = smoothedBands.toList(),
                sessionId = sessionId,
                isCapturing = true,
            )
        }.onFailure { error ->
            releaseVisualizerLocked(clearSession = false)
            _spectrum.value = AudioSpectrumState(
                sessionId = sessionId,
                errorMessage = error.message ?: "Unable to capture playback spectrum",
            )
        }
    }

    private fun publishFft(fft: ByteArray) {
        if (fft.size < 4) return

        synchronized(lock) {
            if (!captureRequested || visualizer == null) return

            val availableBins = fft.size / 2
            val maxBin = (availableBins - 1).coerceAtLeast(1)
            val maxByteMagnitude = hypot(128.0, 128.0)
            val normalizer = ln1p(maxByteMagnitude)

            for (band in 0 until AUDIO_SPECTRUM_BAND_COUNT) {
                // Power-distributed boundaries give low/mid frequencies more visual resolution.
                val startFraction = band.toDouble() / AUDIO_SPECTRUM_BAND_COUNT
                val endFraction = (band + 1).toDouble() / AUDIO_SPECTRUM_BAND_COUNT
                val startBin = (1 + (maxBin - 1) * startFraction.pow(2.15))
                    .toInt()
                    .coerceIn(1, maxBin)
                val endBin = (1 + (maxBin - 1) * endFraction.pow(2.15))
                    .toInt()
                    .coerceIn(startBin, maxBin)

                var peak = 0.0
                for (bin in startBin..endBin) {
                    val realIndex = bin * 2
                    val imaginaryIndex = realIndex + 1
                    if (imaginaryIndex >= fft.size) break
                    val magnitude = hypot(
                        fft[realIndex].toDouble(),
                        fft[imaginaryIndex].toDouble(),
                    )
                    if (magnitude > peak) peak = magnitude
                }

                val normalized = (ln1p(peak) / normalizer)
                    .toFloat()
                    .coerceIn(0f, 1f)
                val previous = smoothedBands[band]
                val response = if (normalized >= previous) 0.64f else 0.24f
                smoothedBands[band] = previous + (normalized - previous) * response
            }

            _spectrum.value = AudioSpectrumState(
                bands = smoothedBands.toList(),
                sessionId = sessionId,
                isCapturing = true,
            )
        }
    }

    private fun releaseVisualizerLocked(clearSession: Boolean) {
        visualizer?.runCatching {
            enabled = false
            release()
        }
        visualizer = null
        smoothedBands.fill(0f)
        if (clearSession) sessionId = 0
    }

    private fun clearSpectrumLocked() {
        smoothedBands.fill(0f)
        _spectrum.value = AudioSpectrumState(sessionId = sessionId)
    }
}
