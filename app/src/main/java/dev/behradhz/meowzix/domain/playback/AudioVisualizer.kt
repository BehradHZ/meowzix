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

    /** Selects the current media source and resets the spectrum for the new track. */
    fun analyze(sourceUri: String?)

    /** Attaches live FFT capture to the player's non-zero audio session. */
    fun attachToAudioSession(audioSessionId: Int)

    /** Retries live capture, primarily after RECORD_AUDIO permission is granted. */
    fun refresh()

    fun release()
}
