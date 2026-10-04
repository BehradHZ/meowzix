package dev.behradhz.meowzix.domain.playback

import kotlinx.coroutines.flow.StateFlow

const val AUDIO_SPECTRUM_BAND_COUNT = 48

data class AudioSpectrumState(
    val bands: FloatArray = FloatArray(AUDIO_SPECTRUM_BAND_COUNT),
    val sourceUri: String? = null,
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
)

interface AudioVisualizerRepository {
    val spectrum: StateFlow<AudioSpectrumState>

    /** Selects the current media source and resets measured spectrum state for the new track. */
    fun analyze(sourceUri: String?)

    /** Attaches measured FFT capture and playback-owned effects to the player's non-zero audio session. */
    fun attachToAudioSession(audioSessionId: Int)

    /** Retries capture after an audio-session/output capability change. */
    fun refresh()

    fun release()
}
