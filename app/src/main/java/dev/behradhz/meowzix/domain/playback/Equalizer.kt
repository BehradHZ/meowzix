package dev.behradhz.meowzix.domain.playback

import kotlinx.coroutines.flow.StateFlow

enum class EqualizerPreset {
    FLAT,
    BASS,
    VOCAL,
    BRIGHT,
    CUSTOM,
}

data class EqualizerBand(
    val index: Int,
    val centerFrequencyHz: Int,
    val levelDb: Float,
    val minLevelDb: Float,
    val maxLevelDb: Float,
)

data class EqualizerState(
    val enabled: Boolean = false,
    val supported: Boolean = true,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val bands: List<EqualizerBand> = emptyList(),
    val errorMessage: String? = null,
) {
    val attached: Boolean get() = supported && bands.isNotEmpty()
}

interface EqualizerRepository {
    val state: StateFlow<EqualizerState>

    fun attachToAudioSession(audioSessionId: Int)
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setBandLevel(index: Int, levelDb: Float)
    suspend fun applyPreset(preset: EqualizerPreset)
    suspend fun resetFlat()
    fun release()
}

/** Pure preset mapping kept outside Android audio effects so it can be tested deterministically. */
object EqualizerPresetMapper {
    const val SAFE_MAX_BOOST_DB = 6f

    fun targetDb(preset: EqualizerPreset, centerFrequencyHz: Int): Float = when (preset) {
        EqualizerPreset.FLAT, EqualizerPreset.CUSTOM -> 0f
        EqualizerPreset.BASS -> when {
            centerFrequencyHz <= 120 -> 4.5f
            centerFrequencyHz <= 250 -> 3.5f
            centerFrequencyHz <= 1_000 -> 1.0f
            centerFrequencyHz >= 8_000 -> -1.0f
            else -> 0f
        }
        EqualizerPreset.VOCAL -> when {
            centerFrequencyHz < 120 -> -1.5f
            centerFrequencyHz in 250..4_000 -> 2.5f
            centerFrequencyHz > 10_000 -> -1.0f
            else -> 0.5f
        }
        EqualizerPreset.BRIGHT -> when {
            centerFrequencyHz >= 8_000 -> 4.0f
            centerFrequencyHz >= 4_000 -> 3.0f
            centerFrequencyHz >= 1_000 -> 1.0f
            centerFrequencyHz < 120 -> -0.5f
            else -> 0f
        }
    }

    fun clamp(levelDb: Float, deviceMinDb: Float, deviceMaxDb: Float): Float =
        levelDb.coerceIn(deviceMinDb, minOf(deviceMaxDb, SAFE_MAX_BOOST_DB))
}
