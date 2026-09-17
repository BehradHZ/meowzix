package dev.behradhz.meowzix.domain.playback

import kotlinx.coroutines.flow.StateFlow

const val AUDIO_SPECTRUM_BAND_COUNT = 32

data class AudioSpectrumState(
    val bands: List<Float> = List(AUDIO_SPECTRUM_BAND_COUNT) { 0f },
    val sessionId: Int = 0,
    val isCapturing: Boolean = false,
    val errorMessage: String? = null,
)

interface AudioVisualizerRepository {
    val spectrum: StateFlow<AudioSpectrumState>

    /**
     * Attaches visualization to Meowzix playback only.
     * Session ID 0/unset is deliberately ignored to avoid global-output capture.
     */
    fun attachToSession(audioSessionId: Int)

    /**
     * Starts/stops FFT capture without affecting playback.
     * The caller is responsible for requesting RECORD_AUDIO first.
     */
    fun setCaptureEnabled(enabled: Boolean)

    fun release()
}
