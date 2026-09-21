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

    /**
     * Builds a compact waveform/spectrum summary from the current media file itself. This performs
     * no microphone or output-mix capture and therefore needs no RECORD_AUDIO permission.
     */
    fun analyze(sourceUri: String?)

    fun release()
}
