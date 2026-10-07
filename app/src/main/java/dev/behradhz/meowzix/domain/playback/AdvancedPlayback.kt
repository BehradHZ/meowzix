package dev.behradhz.meowzix.domain.playback

import kotlin.math.log10
import kotlin.math.pow

enum class SleepTimerMode { OFF, DURATION, END_OF_TRACK }

data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val deadlineElapsedRealtimeMs: Long? = null,
    val bootIdentity: String? = null,
    val armedMediaId: String? = null,
    val fadeDurationMs: Long = 0L,
    val remainingMs: Long = 0L,
) {
    val active: Boolean get() = mode != SleepTimerMode.OFF

    companion object {
        val Off = SleepTimerState()
    }
}

data class PersistedSleepTimer(
    val mode: SleepTimerMode,
    val deadlineElapsedRealtimeMs: Long?,
    val bootIdentity: String?,
    val armedMediaId: String?,
    val fadeDurationMs: Long,
)

object SleepTimerPolicy {
    const val MAX_DURATION_MS = 24L * 60L * 60L * 1_000L
    const val MAX_FADE_MS = 30_000L

    fun duration(
        durationMs: Long,
        nowElapsedRealtimeMs: Long,
        bootIdentity: String,
        fadeDurationMs: Long,
    ): PersistedSleepTimer {
        val safeDuration = durationMs.coerceIn(1_000L, MAX_DURATION_MS)
        return PersistedSleepTimer(
            mode = SleepTimerMode.DURATION,
            deadlineElapsedRealtimeMs = nowElapsedRealtimeMs + safeDuration,
            bootIdentity = bootIdentity,
            armedMediaId = null,
            fadeDurationMs = fadeDurationMs.coerceIn(0L, MAX_FADE_MS),
        )
    }

    fun endOfTrack(
        mediaId: String?,
        bootIdentity: String,
        fadeDurationMs: Long,
    ) = PersistedSleepTimer(
        mode = SleepTimerMode.END_OF_TRACK,
        deadlineElapsedRealtimeMs = null,
        bootIdentity = bootIdentity,
        armedMediaId = mediaId,
        fadeDurationMs = fadeDurationMs.coerceIn(0L, MAX_FADE_MS),
    )

    fun restore(
        persisted: PersistedSleepTimer?,
        nowElapsedRealtimeMs: Long,
        currentBootIdentity: String,
    ): SleepTimerState {
        if (persisted == null || persisted.mode == SleepTimerMode.OFF) return SleepTimerState.Off
        if (persisted.bootIdentity != currentBootIdentity) return SleepTimerState.Off
        return when (persisted.mode) {
            SleepTimerMode.OFF -> SleepTimerState.Off
            SleepTimerMode.END_OF_TRACK -> SleepTimerState(
                mode = SleepTimerMode.END_OF_TRACK,
                bootIdentity = currentBootIdentity,
                armedMediaId = persisted.armedMediaId,
                fadeDurationMs = persisted.fadeDurationMs.coerceIn(0L, MAX_FADE_MS),
            )
            SleepTimerMode.DURATION -> {
                val deadline = persisted.deadlineElapsedRealtimeMs ?: return SleepTimerState.Off
                val remaining = deadline - nowElapsedRealtimeMs
                if (remaining <= 0L) SleepTimerState.Off else SleepTimerState(
                    mode = SleepTimerMode.DURATION,
                    deadlineElapsedRealtimeMs = deadline,
                    bootIdentity = currentBootIdentity,
                    fadeDurationMs = persisted.fadeDurationMs.coerceIn(0L, MAX_FADE_MS),
                    remainingMs = remaining,
                )
            }
        }
    }

    fun refresh(
        state: SleepTimerState,
        nowElapsedRealtimeMs: Long,
    ): SleepTimerState = when (state.mode) {
        SleepTimerMode.DURATION -> {
            val deadline = state.deadlineElapsedRealtimeMs ?: return SleepTimerState.Off
            val remaining = deadline - nowElapsedRealtimeMs
            if (remaining <= 0L) state.copy(remainingMs = 0L) else state.copy(remainingMs = remaining)
        }
        SleepTimerMode.END_OF_TRACK, SleepTimerMode.OFF -> state
    }

    fun extend(
        state: SleepTimerState,
        additionalMs: Long,
        nowElapsedRealtimeMs: Long,
    ): SleepTimerState {
        if (state.mode != SleepTimerMode.DURATION || additionalMs <= 0L) return state
        val currentRemaining = (state.deadlineElapsedRealtimeMs ?: nowElapsedRealtimeMs) - nowElapsedRealtimeMs
        val newRemaining = (currentRemaining.coerceAtLeast(0L) + additionalMs).coerceAtMost(MAX_DURATION_MS)
        return state.copy(
            deadlineElapsedRealtimeMs = nowElapsedRealtimeMs + newRemaining,
            remainingMs = newRemaining,
        )
    }

    fun fadeGain(state: SleepTimerState): Float {
        if (state.mode != SleepTimerMode.DURATION || state.fadeDurationMs <= 0L) return 1f
        if (state.remainingMs >= state.fadeDurationMs) return 1f
        return (state.remainingMs.toDouble() / state.fadeDurationMs).coerceIn(0.0, 1.0).toFloat()
    }

    fun endOfTrackFadeGain(
        state: SleepTimerState,
        currentMediaId: String?,
        positionMs: Long,
        durationMs: Long,
    ): Float {
        if (state.mode != SleepTimerMode.END_OF_TRACK || state.fadeDurationMs <= 0L) return 1f
        if (state.armedMediaId != null && state.armedMediaId != currentMediaId) return 1f
        if (durationMs <= 0L) return 1f
        val remaining = (durationMs - positionMs).coerceAtLeast(0L)
        if (remaining >= state.fadeDurationMs) return 1f
        return (remaining.toDouble() / state.fadeDurationMs).coerceIn(0.0, 1.0).toFloat()
    }
}

enum class SleepTimerTerminationReason {
    DURATION_EXPIRED,
    END_OF_TRACK_REACHED,
    END_OF_TRACK_PLAYBACK_ERROR,
}

data class SleepTimerTermination(
    val reason: SleepTimerTerminationReason,
    val mediaId: String?,
    val positionMs: Long,
    val durationMs: Long,
)

data class PlaybackGainState(
    val userBaseVolume: Float = 1f,
    val normalizationGainDb: Float = 0f,
    val equalizerPeakBoostDb: Float = 0f,
    val sleepTimerGain: Float = 1f,
    val crossfadeGain: Float = 1f,
)

data class PlaybackGainResult(
    val playerVolume: Float,
    val normalizationLinear: Float,
    val safetyHeadroomLinear: Float,
)

object PlaybackGainCoordinator {
    const val MIN_NORMALIZATION_DB = -12f
    const val MAX_NORMALIZATION_DB = 6f
    const val MAX_EQ_BOOST_DB = 12f

    fun resolve(state: PlaybackGainState): PlaybackGainResult {
        val base = state.userBaseVolume.coerceIn(0f, 1f)
        val normalizationDb = state.normalizationGainDb.coerceIn(MIN_NORMALIZATION_DB, MAX_NORMALIZATION_DB)
        val eqBoost = state.equalizerPeakBoostDb.coerceIn(0f, MAX_EQ_BOOST_DB)
        // Reserve enough digital headroom that the largest positive normalization + EQ boost
        // cannot exceed unity before the device/system volume stage. This deliberately prefers
        // clipping safety over preserving positive gain when no limiter is available.
        val safetyHeadroomDb = (eqBoost + normalizationDb.coerceAtLeast(0f)).coerceAtLeast(0f)
        val normalizationLinear = dbToLinear(normalizationDb)
        val headroomLinear = dbToLinear(-safetyHeadroomDb)
        val timer = state.sleepTimerGain.coerceIn(0f, 1f)
        val crossfade = state.crossfadeGain.coerceIn(0f, 1f)
        return PlaybackGainResult(
            playerVolume = (base * normalizationLinear * headroomLinear * timer * crossfade).coerceIn(0f, 1f),
            normalizationLinear = normalizationLinear,
            safetyHeadroomLinear = headroomLinear,
        )
    }

    fun dbToLinear(db: Float): Float = 10.0.pow(db.toDouble() / 20.0).toFloat()
}

enum class LoudnessAlgorithm {
    REPLAY_GAIN_TRACK,
    REPLAY_GAIN_ALBUM,
    RMS_DBFS_APPROX_V1,
    NEUTRAL,
}

data class LoudnessAnalysis(
    val measuredDb: Double?,
    val suggestedGainDb: Float,
    val algorithm: LoudnessAlgorithm,
    val algorithmVersion: String,
    val confidence: Float,
)

object ReplayGainParser {
    private val number = Regex("""[-+]?\d+(?:\.\d+)?""")

    fun parseDb(raw: String?): Float? {
        val value = raw?.let { number.find(it)?.value }?.toFloatOrNull() ?: return null
        return value.takeIf { it.isFinite() && it in -30f..30f }
    }
}

object RmsLoudnessEstimator {
    const val VERSION = "rms-dbfs-approx-v1"
    const val TARGET_DBFS = -18.0

    /**
     * Conservative fallback only. RMS dBFS is not LUFS and must never be presented as
     * standards-compliant integrated loudness.
     */
    fun estimate(normalizedRms: Double, confidence: Float = 0.55f): LoudnessAnalysis {
        if (!normalizedRms.isFinite() || normalizedRms <= 0.0 || normalizedRms > 1.0) {
            return LoudnessAnalysis(null, 0f, LoudnessAlgorithm.NEUTRAL, VERSION, 0f)
        }
        val measured = 20.0 * log10(normalizedRms.coerceAtLeast(1e-6))
        val gain = (TARGET_DBFS - measured)
            .coerceIn(
                PlaybackGainCoordinator.MIN_NORMALIZATION_DB.toDouble(),
                PlaybackGainCoordinator.MAX_NORMALIZATION_DB.toDouble(),
            )
            .toFloat()
        return LoudnessAnalysis(
            measuredDb = measured,
            suggestedGainDb = gain,
            algorithm = LoudnessAlgorithm.RMS_DBFS_APPROX_V1,
            algorithmVersion = VERSION,
            confidence = confidence.coerceIn(0f, 1f),
        )
    }
}

data class GaplessCapability(
    val eligible: Boolean,
    val reason: String,
)

object GaplessPolicy {
    private val knownLocalMimeTypes = setOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/aac",
        "audio/flac",
        "audio/ogg",
        "audio/opus",
        "audio/wav",
        "audio/x-wav",
    )

    fun capability(isLocallyReadable: Boolean, mimeType: String?): GaplessCapability {
        if (!isLocallyReadable) {
            return GaplessCapability(false, "Progressive/remote playback cannot guarantee a seamless boundary.")
        }
        if (mimeType != null && mimeType.lowercase() !in knownLocalMimeTypes) {
            return GaplessCapability(false, "Codec/container has not been verified for gapless playback.")
        }
        return GaplessCapability(
            true,
            "Media3 seamless playlist transition is used; encoder delay/padding is honored when present in supported metadata.",
        )
    }
}

interface LoudnessNormalizationRepository {
    suspend fun fallbackAnalysis(trackId: java.util.UUID): LoudnessAnalysis
    suspend fun persistReplayGain(trackId: java.util.UUID, analysis: LoudnessAnalysis): LoudnessAnalysis
}

data class CrossfadeCapability(
    val supported: Boolean,
    val reason: String,
) {
    companion object {
        val CurrentArchitecture = CrossfadeCapability(
            supported = false,
            reason = "Media3 1.11.1 does not provide real overlapping crossfade for ExoPlayer playlists; Meowzix falls back to the ordinary seamless transition.",
        )
    }
}
