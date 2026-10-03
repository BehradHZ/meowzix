package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import java.time.Instant
import java.util.UUID

enum class OutputClass { SPEAKER, WIRED, BLUETOOTH }

/** Source provenance deliberately does not enter this context or the feature vector. */
data class RecommendationContext(
    val localHour: Int,
    val dayOfWeek: Int,
    val isWeekend: Boolean,
    val timeBucket: TimeBucket,
    val timestamp: Instant = Instant.now(),
    val sessionId: UUID? = null,
    val sessionPosition: Int = 0,
    val recentTrackIds: List<UUID> = emptyList(),
    val recentArtistIds: List<String> = emptyList(),
    val recentSkipStreak: Int = 0,
    val currentTrackId: UUID? = null,
    val anchorTrackId: UUID? = null,
    val outputClass: OutputClass? = null,
    val offlineOnly: Boolean = false,
)

data class TrackPersonalizationFeatures(
    val trackId: UUID,
    val normalizedArtist: String?,
    val favorite: Boolean,
    val durationMs: Long,
    val audioFeatures: DoubleArray? = null,
    val global: PreferenceSnapshot = PreferenceSnapshot(),
    val time: PreferenceSnapshot = PreferenceSnapshot(),
    val lastPlayedAt: Instant? = null,
    val artistStats: PreferenceSnapshot? = null,
    val albumStats: PreferenceSnapshot? = null,
    val favoriteKnown: Boolean = true,
)

data class TrackFeatures(val trackId: UUID, val values: DoubleArray, val schemaVersion: Int = PersonalizationFeatureVectorizer.SCHEMA_VERSION)
data class FeatureContribution(val featureName: String, val value: Double)
data class ModelTrackScore(
    val trackId: UUID,
    val expectedReward: Double,
    val uncertainty: Double,
    val contributions: List<FeatureContribution> = emptyList(),
)

data class TrainingSample(
    val trackId: UUID,
    val features: DoubleArray,
    val reward: Double,
    val weight: Double = 1.0,
    /** Durable event sequence, not a timestamp: two outcomes in one millisecond remain distinct. */
    val dataVersion: Long,
    val playbackInstanceId: UUID = UUID.nameUUIDFromBytes("$trackId:$dataVersion".toByteArray()),
    val eventIds: List<UUID> = emptyList(),
    val context: RecommendationContext? = null,
    val sourceIdUsedForAudio: UUID? = null,
    val audioExtractorVersion: String? = null,
    val featureSchemaVersion: Int = PersonalizationFeatureVectorizer.SCHEMA_VERSION,
    val rewardSchemaVersion: Int = PersonalizationRewardBuilder.VERSION,
)

data class TrainingDataset(val samples: List<TrainingSample>)

data class PersonalizationModelState(
    val modelType: String = "shared-linucb",
    val modelVersion: String = RecommendationConfig.MODEL_VERSION,
    val featureSchemaVersion: Int = PersonalizationFeatureVectorizer.SCHEMA_VERSION,
    val rewardSchemaVersion: Int = PersonalizationRewardBuilder.VERSION,
    val trainingDataVersion: Long = 0L,
    val historyFloorVersion: Long = 0L,
    val trainedAtEpochMs: Long = 0L,
    val sampleCount: Long = 0L,
    val active: Boolean = false,
    val artifactChecksum: String? = null,
    val requiresRebuild: Boolean = false,
)

interface PersonalizationModel {
    suspend fun state(): PersonalizationModelState
    suspend fun score(context: RecommendationContext, candidates: List<TrackFeatures>): List<ModelTrackScore>
    /** Compatibility adapter for existing diagnostics. Values use the existing [0,1] convention. */
    suspend fun scoreBatch(features: Map<UUID, DoubleArray>): Map<UUID, Double>
    suspend fun update(samples: List<TrainingSample>)
    suspend fun rebuild(samples: List<TrainingSample>)
    suspend fun rebuild(dataset: TrainingDataset) = rebuild(dataset.samples)
    suspend fun reset(trainingDataVersion: Long = 0L)
}

object RecommendationConfig {
    const val MODEL_VERSION = "2"
    const val MIN_TRAINING_SAMPLES = 75
    const val DOMINANT_TRAINING_SAMPLES = 200
    const val UPDATE_BATCH = 10
    const val MAX_REBUILD_OUTCOMES = 20_000
    const val EVENT_PAGE_SIZE = 2_000
    const val REGULARIZATION = 1.0
    const val ALPHA = 0.15
    const val MAX_EXPLORATION_BONUS = 0.15
    const val REDISCOVER_DAYS = 14L
    const val CANDIDATE_LIMIT = 800
    const val SECTION_SIZE = 15
    const val MODEL_ARTIFACT_LIMIT_BYTES = 1_000_000

    fun learnedWeight(sampleCount: Long): Double = when {
        sampleCount < MIN_TRAINING_SAMPLES -> 0.0
        else -> (0.05 + 0.80 * (sampleCount - MIN_TRAINING_SAMPLES) /
            (DOMINANT_TRAINING_SAMPLES - MIN_TRAINING_SAMPLES).toDouble()).coerceAtMost(0.85)
    }
}

/** One outcome per playback instance; duplicate actions never accumulate multiple rewards. */
data class PlaybackOutcome(
    val playbackInstanceId: UUID,
    val eventTypes: Set<String>,
    val completionRatio: Double? = null,
    val lastFavoriteAction: Boolean? = null,
)

object PersonalizationRewardBuilder {
    const val VERSION = 2
    const val MANUAL = 0.35
    const val COMPLETION = 0.60
    const val REPLAY = 0.40
    const val FAVORITE = 0.50
    const val PARTIAL = 0.20
    const val LATE_SKIP = -0.15
    const val EARLY_SKIP = -0.70
    const val QUEUE_REMOVAL = -0.20

    fun reward(eventTypes: Set<String>): Double = reward(PlaybackOutcome(UUID(0, 0), eventTypes))

    fun reward(outcome: PlaybackOutcome): Double {
        val types = outcome.eventTypes
        var value = 0.0
        if ("MANUAL_SELECTED" in types) value += MANUAL
        if ("REPLAYED" in types) value += REPLAY
        if (outcome.lastFavoriteAction ?: ("FAVORITED" in types && "UNFAVORITED" !in types)) value += FAVORITE
        // Completion classification is authoritative (including the spec's <=15 seconds rule).
        when {
            "PLAY_COMPLETED" in types -> value += COMPLETION
            "SKIPPED_EARLY" in types -> value += EARLY_SKIP
            else -> {
                val ratio = outcome.completionRatio
                if (ratio != null && ratio.isFinite() && ratio >= 0.60 && ratio < 0.90) value += PARTIAL
                if ("SKIPPED_LATE" in types) value += LATE_SKIP
            }
        }
        if ("QUEUE_REMOVED" in types && "PLAY_STARTED" !in types) value += QUEUE_REMOVAL
        return value.coerceIn(-1.0, 1.0)
    }
}
