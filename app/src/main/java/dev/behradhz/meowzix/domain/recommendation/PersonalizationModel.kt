package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Context intentionally contains no provider/source identity. */
data class RecommendationContext(
    val localHour: Int,
    val dayOfWeek: Int,
    val isWeekend: Boolean,
    val timeBucket: TimeBucket,
)

/** Canonical Track-level features. Physical TrackSource identity never enters the model. */
data class TrackPersonalizationFeatures(
    val trackId: UUID,
    val normalizedArtist: String?,
    val favorite: Boolean,
    val durationMs: Long,
    val audioFeatures: DoubleArray? = null,
)

data class TrainingSample(
    val trackId: UUID,
    val features: DoubleArray,
    val reward: Double,
    val weight: Double = 1.0,
    /** Monotonic-ish source-data version derived from the finalized outcome timestamp. */
    val dataVersion: Long,
)

data class PersonalizationModelState(
    val modelType: String = "local-linear-contextual-ranker",
    val modelVersion: String = "1",
    val featureSchemaVersion: Int = PersonalizationFeatureVectorizer.SCHEMA_VERSION,
    val trainingDataVersion: Long = 0L,
    val trainedAtEpochMs: Long = 0L,
    val sampleCount: Long = 0L,
    val active: Boolean = false,
)

interface PersonalizationModel {
    suspend fun state(): PersonalizationModelState
    suspend fun scoreBatch(features: Map<UUID, DoubleArray>): Map<UUID, Double>
    suspend fun update(samples: List<TrainingSample>)
    suspend fun rebuild(samples: List<TrainingSample>)

    /**
     * Clears learned weights. [trainingDataVersion] can watermark existing history so a privacy
     * reset keeps raw history for the user without immediately relearning from those old events.
     */
    suspend fun reset(trainingDataVersion: Long = 0L)
}

/**
 * Stable, compact source-agnostic feature layout for a small personal linear model.
 *
 * Hash buckets avoid an unbounded one-hot vocabulary while preserving per-track/per-artist signal.
 * The explicit track×time bucket interaction is what lets one song learn different suitability at
 * different times instead of merely learning a global song bias plus a global evening bias.
 */
object PersonalizationFeatureVectorizer {
    const val SCHEMA_VERSION = 1
    const val TRACK_BUCKETS = 128
    const val ARTIST_BUCKETS = 64
    const val TRACK_TIME_BUCKETS = 64
    const val AUDIO_DIMENSIONS = 16

    private const val TIME_START = 8
    private const val TRACK_START = TIME_START + 6
    private const val ARTIST_START = TRACK_START + TRACK_BUCKETS
    private const val TRACK_TIME_START = ARTIST_START + ARTIST_BUCKETS
    private const val AUDIO_MASK = TRACK_TIME_START + TRACK_TIME_BUCKETS
    private const val AUDIO_START = AUDIO_MASK + 1
    const val FEATURE_COUNT = AUDIO_START + AUDIO_DIMENSIONS

    fun vectorize(context: RecommendationContext, track: TrackPersonalizationFeatures): DoubleArray {
        val vector = DoubleArray(FEATURE_COUNT)
        vector[0] = 1.0

        val hourAngle = 2.0 * PI * context.localHour.coerceIn(0, 23) / 24.0
        vector[1] = sin(hourAngle)
        vector[2] = cos(hourAngle)

        val day = context.dayOfWeek.coerceIn(1, 7)
        val dayAngle = 2.0 * PI * (day - 1) / 7.0
        vector[3] = sin(dayAngle)
        vector[4] = cos(dayAngle)
        vector[5] = if (context.isWeekend) 1.0 else 0.0
        vector[6] = if (track.favorite) 1.0 else 0.0
        vector[7] = (track.durationMs.coerceIn(0L, 20 * 60_000L) / (20.0 * 60_000.0)).coerceIn(0.0, 1.0)

        vector[TIME_START + context.timeBucket.ordinal] = 1.0
        vector[TRACK_START + bucket(track.trackId.toString(), TRACK_BUCKETS)] = 1.0
        track.normalizedArtist?.takeIf { it.isNotBlank() }?.let {
            vector[ARTIST_START + bucket(it, ARTIST_BUCKETS)] = 1.0
        }
        vector[TRACK_TIME_START + bucket("${track.trackId}:${context.timeBucket.name}", TRACK_TIME_BUCKETS)] = 1.0

        track.audioFeatures?.let { audio ->
            vector[AUDIO_MASK] = 1.0
            for (index in 0 until minOf(audio.size, AUDIO_DIMENSIONS)) {
                vector[AUDIO_START + index] = audio[index].coerceIn(-4.0, 4.0)
            }
        }
        return vector
    }

    private fun bucket(value: String, size: Int): Int =
        ((value.hashCode().toLong() and 0x7fffffffL) % size).toInt()
}

/** Versioned reward semantics. Autoplay itself intentionally contributes no positive reward. */
object PersonalizationRewardBuilder {
    const val VERSION = 1

    fun reward(eventTypes: Set<String>): Double {
        var reward = 0.0
        if ("MANUAL_SELECTED" in eventTypes) reward += 0.35
        if ("PLAY_COMPLETED" in eventTypes) reward += 0.60
        if ("REPLAYED" in eventTypes) reward += 0.40
        if ("FAVORITED" in eventTypes) reward += 0.50
        if ("SKIPPED_LATE" in eventTypes) reward -= 0.15
        if ("SKIPPED_EARLY" in eventTypes) reward -= 0.70
        return reward.coerceIn(-1.0, 1.0)
    }
}
