package dev.behradhz.meowzix.playback

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.playback.EqualizerBand
import dev.behradhz.meowzix.domain.playback.EqualizerPreset
import dev.behradhz.meowzix.domain.playback.EqualizerPresetMapper
import dev.behradhz.meowzix.domain.playback.EqualizerRepository
import dev.behradhz.meowzix.domain.playback.EqualizerState
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private val Context.equalizerDataStore by preferencesDataStore("equalizer_settings")

@Singleton
class AndroidEqualizerRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : EqualizerRepository {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(EqualizerState())
    override val state = _state.asStateFlow()

    private var effect: Equalizer? = null
    private var audioSessionId: Int = NO_AUDIO_SESSION
    private var desired = PersistedEqualizer()

    init {
        scope.launch {
            context.equalizerDataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .collect { preferences ->
                    val preset = preferences[PRESET]
                        ?.let { runCatching { EqualizerPreset.valueOf(it) }.getOrNull() }
                        ?: EqualizerPreset.FLAT
                    desired = PersistedEqualizer(
                        enabled = preferences[ENABLED] ?: false,
                        preset = preset,
                        levelsByFrequencyHz = decodeLevels(preferences[LEVELS]),
                    )
                    synchronized(lock) { applyDesiredLocked() }
                }
        }
    }

    override fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        synchronized(lock) {
            if (this.audioSessionId == audioSessionId && effect != null) {
                applyDesiredLocked()
                return
            }
            releaseEffectLocked()
            this.audioSessionId = audioSessionId
            val created = try {
                Equalizer(0, audioSessionId)
            } catch (_: Throwable) {
                _state.value = EqualizerState(
                    enabled = desired.enabled,
                    supported = false,
                    preset = desired.preset,
                    errorMessage = UNSUPPORTED_MESSAGE,
                )
                return
            }
            effect = created
            applyDesiredLocked()
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        context.equalizerDataStore.edit { it[ENABLED] = enabled }
    }

    override suspend fun setBandLevel(index: Int, levelDb: Float) {
        val current = _state.value
        val band = current.bands.firstOrNull { it.index == index } ?: return
        val safe = EqualizerPresetMapper.clamp(levelDb, band.minLevelDb, band.maxLevelDb)
        val levels = current.bands.associate { row ->
            row.centerFrequencyHz to if (row.index == index) safe else row.levelDb
        }
        context.equalizerDataStore.edit { preferences ->
            preferences[PRESET] = EqualizerPreset.CUSTOM.name
            preferences[LEVELS] = encodeLevels(levels)
        }
    }

    override suspend fun applyPreset(preset: EqualizerPreset) {
        require(preset != EqualizerPreset.CUSTOM) { "CUSTOM is derived from manual band edits" }
        val currentBands = _state.value.bands
        val levels = currentBands.associate { band ->
            band.centerFrequencyHz to EqualizerPresetMapper.clamp(
                EqualizerPresetMapper.targetDb(preset, band.centerFrequencyHz),
                band.minLevelDb,
                band.maxLevelDb,
            )
        }
        context.equalizerDataStore.edit { preferences ->
            preferences[PRESET] = preset.name
            preferences[LEVELS] = encodeLevels(levels)
        }
    }

    override suspend fun resetFlat() = applyPreset(EqualizerPreset.FLAT)

    override fun release() {
        synchronized(lock) {
            releaseEffectLocked()
            audioSessionId = NO_AUDIO_SESSION
            _state.value = EqualizerState(
                enabled = desired.enabled,
                preset = desired.preset,
            )
        }
    }

    private fun applyDesiredLocked() {
        val current = effect
        if (current == null) {
            _state.value = EqualizerState(
                enabled = desired.enabled,
                supported = true,
                preset = desired.preset,
            )
            return
        }
        try {
            val rawRange = current.bandLevelRange
            val deviceMinDb = rawRange.firstOrNull()?.div(100f) ?: -15f
            val deviceMaxDb = rawRange.getOrNull(1)?.div(100f) ?: 15f
            val safeMaxDb = minOf(deviceMaxDb, EqualizerPresetMapper.SAFE_MAX_BOOST_DB)
            val bands = (0 until current.numberOfBands.toInt()).map { index ->
                val shortIndex = index.toShort()
                val frequencyHz = (current.getCenterFreq(shortIndex) / 1_000).coerceAtLeast(1)
                val persisted = nearestPersistedLevel(frequencyHz)
                val target = persisted ?: EqualizerPresetMapper.targetDb(desired.preset, frequencyHz)
                val safe = EqualizerPresetMapper.clamp(target, deviceMinDb, safeMaxDb)
                current.setBandLevel(shortIndex, (safe * 100f).toInt().toShort())
                EqualizerBand(
                    index = index,
                    centerFrequencyHz = frequencyHz,
                    levelDb = safe,
                    minLevelDb = deviceMinDb,
                    maxLevelDb = safeMaxDb,
                )
            }
            current.enabled = desired.enabled
            _state.value = EqualizerState(
                enabled = desired.enabled,
                supported = true,
                preset = desired.preset,
                bands = bands,
            )
        } catch (_: Throwable) {
            releaseEffectLocked()
            _state.value = EqualizerState(
                enabled = desired.enabled,
                supported = false,
                preset = desired.preset,
                errorMessage = UNSUPPORTED_MESSAGE,
            )
        }
    }

    private fun nearestPersistedLevel(frequencyHz: Int): Float? {
        if (desired.levelsByFrequencyHz.isEmpty()) return null
        return desired.levelsByFrequencyHz.minByOrNull { (storedHz, _) -> abs(storedHz - frequencyHz) }?.value
    }

    private fun releaseEffectLocked() {
        val current = effect ?: return
        effect = null
        runCatching { current.enabled = false }
        runCatching { current.release() }
    }

    private data class PersistedEqualizer(
        val enabled: Boolean = false,
        val preset: EqualizerPreset = EqualizerPreset.FLAT,
        val levelsByFrequencyHz: Map<Int, Float> = emptyMap(),
    )

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val PRESET = stringPreferencesKey("preset")
        val LEVELS = stringPreferencesKey("levels")
        const val NO_AUDIO_SESSION = -1
        const val UNSUPPORTED_MESSAGE = "Equalizer is unavailable on this device or audio path"

        fun encodeLevels(levels: Map<Int, Float>): String = levels.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value}" }

        fun decodeLevels(value: String?): Map<Int, Float> = value.orEmpty()
            .split(',')
            .mapNotNull { token ->
                val parts = token.split(':')
                if (parts.size != 2) return@mapNotNull null
                val hz = parts[0].toIntOrNull() ?: return@mapNotNull null
                val db = parts[1].toFloatOrNull()?.takeIf(Float::isFinite) ?: return@mapNotNull null
                hz to db
            }
            .toMap()
    }
}
