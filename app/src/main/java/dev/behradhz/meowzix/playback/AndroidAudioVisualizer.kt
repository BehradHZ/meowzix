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
    private var cachedFftSize = 0
    private val bandRanges = IntArray(AUDIO_SPECTRUM_BAND_COUNT * 2)

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
                bands = smoothedBands.copyOf(),
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
            ensureBandRanges(fft.size)

            for (band in 0 until AUDIO_SPECTRUM_BAND_COUNT) {
                val rangeIndex = band * 2
                val startBin = bandRanges[rangeIndex]
                val endBin = bandRanges[rangeIndex + 1]

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

                val normalized = (ln1p(peak) / MAX_FFT_NORMALIZER)
                    .toFloat()
                    .coerceIn(0f, 1f)
                val previous = smoothedBands[band]
                val response = if (normalized >= previous) ATTACK else DECAY
                smoothedBands[band] = previous + (normalized - previous) * response
            }

            // One primitive-array copy per visual frame avoids boxing 32 Float objects
            // while still giving StateFlow an immutable snapshot for Compose.
            _spectrum.value = AudioSpectrumState(
                bands = smoothedBands.copyOf(),
                sessionId = sessionId,
                isCapturing = true,
            )
        }
    }

    private fun ensureBandRanges(fftSize: Int) {
        if (cachedFftSize == fftSize) return
        cachedFftSize = fftSize

        val availableBins = fftSize / 2
        val maxBin = (availableBins - 1).coerceAtLeast(1)
        for (band in 0 until AUDIO_SPECTRUM_BAND_COUNT) {
            val startFraction = band.toDouble() / AUDIO_SPECTRUM_BAND_COUNT
            val endFraction = (band + 1).toDouble() / AUDIO_SPECTRUM_BAND_COUNT
            val startBin = (1 + (maxBin - 1) * startFraction.pow(FREQUENCY_DISTRIBUTION_POWER))
                .toInt()
                .coerceIn(1, maxBin)
            val endBin = (1 + (maxBin - 1) * endFraction.pow(FREQUENCY_DISTRIBUTION_POWER))
                .toInt()
                .coerceIn(startBin, maxBin)
            val rangeIndex = band * 2
            bandRanges[rangeIndex] = startBin
            bandRanges[rangeIndex + 1] = endBin
        }
    }

    private fun releaseVisualizerLocked(clearSession: Boolean) {
        visualizer?.runCatching {
            enabled = false
            release()
        }
        visualizer = null
        smoothedBands.fill(0f)
        cachedFftSize = 0
        if (clearSession) sessionId = 0
    }

    private fun clearSpectrumLocked() {
        smoothedBands.fill(0f)
        _spectrum.value = AudioSpectrumState(sessionId = sessionId)
    }

    private companion object {
        const val ATTACK = 0.64f
        const val DECAY = 0.24f
        const val FREQUENCY_DISTRIBUTION_POWER = 2.15
        val MAX_FFT_NORMALIZER: Double = ln1p(hypot(128.0, 128.0))
    }
}
