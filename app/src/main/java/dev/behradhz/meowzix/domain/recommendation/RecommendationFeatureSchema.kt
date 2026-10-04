package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/** Numeric contract: all inputs [0,1] except cyclic inputs/interactions [-1,1]. */
enum class RecommendationFeature(val min: Double = 0.0, val max: Double = 1.0) {
    BIAS, COMPLETION_RATE, EARLY_SKIP_RATE, LATE_SKIP_RATE, MANUAL_RATE, REPLAY_RATE,
    FAVORITE, FAVORITE_AVAILABLE, PLAY_COUNT, COMPLETION_COUNT, DAYS_SINCE_PLAY, LAST_PLAY_AVAILABLE,
    GLOBAL_AFFINITY, ARTIST_AFFINITY, ARTIST_AVAILABLE, ARTIST_STATS_AVAILABLE, ALBUM_AFFINITY, ALBUM_AVAILABLE,
    TIME_AFFINITY, TIME_EVIDENCE,
    HOUR_SIN(-1.0), HOUR_COS(-1.0), DAY_SIN(-1.0), DAY_COS(-1.0), WEEKEND,
    EARLY_MORNING, MORNING, AFTERNOON, EVENING, NIGHT, LATE_NIGHT,
    AFFINITY_EARLY_MORNING, AFFINITY_MORNING, AFFINITY_AFTERNOON, AFFINITY_EVENING, AFFINITY_NIGHT, AFFINITY_LATE_NIGHT,
    SESSION_AVAILABLE, SESSION_POSITION, SKIP_STREAK, SAME_PREVIOUS_ARTIST, PREVIOUS_ARTIST_AVAILABLE, ARTIST_DISTANCE, RECENTLY_PLAYED,
    DURATION, DURATION_AVAILABLE, OUTPUT_AVAILABLE, OUTPUT_SPEAKER, OUTPUT_WIRED, OUTPUT_BLUETOOTH,
    AUDIO_AVAILABLE, AUDIO_RMS, AUDIO_ZCR, AUDIO_SILENCE, AUDIO_DYNAMIC_RANGE, AUDIO_CENTROID, AUDIO_ROLLOFF,
}

object RecommendationFeatureSchemaV2 {
    const val VERSION = 2
    val names: List<String> = RecommendationFeature.entries.map { it.name.lowercase(java.util.Locale.ROOT) }
    val size: Int get() = names.size
    fun validate(values: DoubleArray) {
        require(values.size == size) { "Incompatible feature dimension" }
        RecommendationFeature.entries.forEach { feature ->
            val value = values[feature.ordinal]
            require(value.isFinite() && value >= feature.min && value <= feature.max) { "Invalid ${feature.name}" }
        }
    }
}

/** No Track/artist hash buckets: learned parameters generalize across meaningful statistics. */
object PersonalizationFeatureVectorizer {
    const val SCHEMA_VERSION = RecommendationFeatureSchemaV2.VERSION
    val FEATURE_COUNT: Int get() = RecommendationFeatureSchemaV2.size

    fun affinity(stats: PreferenceSnapshot, favorite: Boolean = false): Double {
        val n = stats.starts.coerceAtLeast(0).toDouble()
        return (0.35 * smoothed(stats.completions, n, 2.0) -
            0.30 * smoothed(stats.earlySkips, n, 1.0) +
            0.20 * smoothed(stats.manualSelections, n, 0.0) +
            0.10 * smoothed(stats.replays, n, 0.0) + if (favorite) 0.05 else 0.0).coerceIn(0.0, 1.0)
    }

    fun timeAffinity(track: TrackPersonalizationFeatures): Double {
        val confidence = track.time.starts.coerceAtLeast(0) / (track.time.starts.coerceAtLeast(0) + 5.0)
        return confidence * affinity(track.time, track.favorite) + (1.0 - confidence) * affinity(track.global, track.favorite)
    }

    fun vectorize(context: RecommendationContext, track: TrackPersonalizationFeatures): DoubleArray {
        val values = DoubleArray(FEATURE_COUNT)
        fun put(feature: RecommendationFeature, value: Double) {
            values[feature.ordinal] = value.coerceIn(feature.min, feature.max)
        }
        fun flag(feature: RecommendationFeature, value: Boolean) = put(feature, if (value) 1.0 else 0.0)
        val stats = track.global
        val n = stats.starts.coerceAtLeast(0).toDouble()
        put(RecommendationFeature.BIAS, 1.0)
        put(RecommendationFeature.COMPLETION_RATE, smoothed(stats.completions, n, 2.0))
        put(RecommendationFeature.EARLY_SKIP_RATE, smoothed(stats.earlySkips, n, 1.0))
        put(RecommendationFeature.LATE_SKIP_RATE, smoothed(stats.lateSkips, n, 0.0))
        put(RecommendationFeature.MANUAL_RATE, smoothed(stats.manualSelections, n, 0.0))
        put(RecommendationFeature.REPLAY_RATE, smoothed(stats.replays, n, 0.0))
        flag(RecommendationFeature.FAVORITE, track.favorite)
        flag(RecommendationFeature.FAVORITE_AVAILABLE, track.favoriteKnown)
        put(RecommendationFeature.PLAY_COUNT, normalizedCount(stats.starts))
        put(RecommendationFeature.COMPLETION_COUNT, normalizedCount(stats.completions))
        flag(RecommendationFeature.LAST_PLAY_AVAILABLE, track.lastPlayedAt != null)
        track.lastPlayedAt?.let { last ->
            put(RecommendationFeature.DAYS_SINCE_PLAY, (context.timestamp.toEpochMilli() - last.toEpochMilli()).coerceAtLeast(0L) / (90.0 * 86_400_000.0))
        }
        put(RecommendationFeature.GLOBAL_AFFINITY, affinity(stats, track.favorite))
        flag(RecommendationFeature.ARTIST_AVAILABLE, !track.normalizedArtist.isNullOrBlank())
        flag(RecommendationFeature.ARTIST_STATS_AVAILABLE, track.artistStats != null)
        track.artistStats?.let { put(RecommendationFeature.ARTIST_AFFINITY, affinity(it)) }
        flag(RecommendationFeature.ALBUM_AVAILABLE, track.albumStats != null)
        track.albumStats?.let { put(RecommendationFeature.ALBUM_AFFINITY, affinity(it)) }
        val timeAffinity = timeAffinity(track)
        put(RecommendationFeature.TIME_AFFINITY, timeAffinity)
        put(RecommendationFeature.TIME_EVIDENCE, track.time.starts.coerceAtLeast(0) / (track.time.starts.coerceAtLeast(0) + 5.0))
        val hourAngle = 2.0 * PI * context.localHour.coerceIn(0, 23) / 24.0
        val dayAngle = 2.0 * PI * (context.dayOfWeek.coerceIn(1, 7) - 1) / 7.0
        put(RecommendationFeature.HOUR_SIN, sin(hourAngle))
        put(RecommendationFeature.HOUR_COS, cos(hourAngle))
        put(RecommendationFeature.DAY_SIN, sin(dayAngle))
        put(RecommendationFeature.DAY_COS, cos(dayAngle))
        flag(RecommendationFeature.WEEKEND, context.isWeekend)
        bucketFeatures.forEach { (bucket, feature) -> flag(feature, context.timeBucket == bucket) }
        affinityBucketFeatures.forEach { (bucket, feature) -> put(feature, if (context.timeBucket == bucket) timeAffinity else 0.0) }
        flag(RecommendationFeature.SESSION_AVAILABLE, context.sessionId != null)
        put(RecommendationFeature.SESSION_POSITION, context.sessionPosition.coerceAtLeast(0) / 50.0)
        put(RecommendationFeature.SKIP_STREAK, context.recentSkipStreak.coerceAtLeast(0) / 5.0)
        val artist = track.normalizedArtist?.takeIf(String::isNotBlank)
        flag(RecommendationFeature.PREVIOUS_ARTIST_AVAILABLE, !context.recentArtistIds.firstOrNull().isNullOrBlank())
        flag(RecommendationFeature.SAME_PREVIOUS_ARTIST, artist != null && context.recentArtistIds.firstOrNull() == artist)
        put(RecommendationFeature.ARTIST_DISTANCE, artist?.let { context.recentArtistIds.indexOf(it).takeIf { d -> d >= 0 } }?.let { it / 10.0 } ?: 1.0)
        flag(RecommendationFeature.RECENTLY_PLAYED, track.trackId in context.recentTrackIds)
        flag(RecommendationFeature.DURATION_AVAILABLE, track.durationMs > 0L)
        put(RecommendationFeature.DURATION, track.durationMs.coerceAtLeast(0L) / 1_200_000.0)
        flag(RecommendationFeature.OUTPUT_AVAILABLE, context.outputClass != null)
        flag(RecommendationFeature.OUTPUT_SPEAKER, context.outputClass == OutputClass.SPEAKER)
        flag(RecommendationFeature.OUTPUT_WIRED, context.outputClass == OutputClass.WIRED)
        flag(RecommendationFeature.OUTPUT_BLUETOOTH, context.outputClass == OutputClass.BLUETOOTH)
        // Recommendation schema v2 intentionally consumes the original six acoustic descriptors.
        // Extractor schema v3 appends tempo/confidence, so accept both legacy/core six-value vectors
        // and newer vectors with additional descriptors while keeping the learned model dimension
        // immutable. Cache/storage compatibility remains governed separately by AudioFeatureSchema.
        track.audioFeatures?.takeIf(::hasModelCompatibleAudio)?.let { audio ->
            flag(RecommendationFeature.AUDIO_AVAILABLE, true)
            audioFeatures.forEachIndexed { index, feature -> put(feature, audio[index]) }
        }
        RecommendationFeatureSchemaV2.validate(values)
        return values
    }

    private fun smoothed(count: Int, starts: Double, prior: Double): Double =
        ((count.coerceAtLeast(0).toDouble().coerceAtMost(starts) + prior) / (starts + 4.0)).coerceIn(0.0, 1.0)
    private fun normalizedCount(count: Int): Double = ln(1.0 + count.coerceIn(0, 1_000)) / ln(1_001.0)
    private val bucketFeatures = listOf(
        TimeBucket.EARLY_MORNING to RecommendationFeature.EARLY_MORNING,
        TimeBucket.MORNING to RecommendationFeature.MORNING,
        TimeBucket.AFTERNOON to RecommendationFeature.AFTERNOON,
        TimeBucket.EVENING to RecommendationFeature.EVENING,
        TimeBucket.NIGHT to RecommendationFeature.NIGHT,
        TimeBucket.LATE_NIGHT to RecommendationFeature.LATE_NIGHT,
    )
    private val affinityBucketFeatures = listOf(
        TimeBucket.EARLY_MORNING to RecommendationFeature.AFFINITY_EARLY_MORNING,
        TimeBucket.MORNING to RecommendationFeature.AFFINITY_MORNING,
        TimeBucket.AFTERNOON to RecommendationFeature.AFFINITY_AFTERNOON,
        TimeBucket.EVENING to RecommendationFeature.AFFINITY_EVENING,
        TimeBucket.NIGHT to RecommendationFeature.AFFINITY_NIGHT,
        TimeBucket.LATE_NIGHT to RecommendationFeature.AFFINITY_LATE_NIGHT,
    )
    private val audioFeatures = listOf(RecommendationFeature.AUDIO_RMS, RecommendationFeature.AUDIO_ZCR,
        RecommendationFeature.AUDIO_SILENCE, RecommendationFeature.AUDIO_DYNAMIC_RANGE,
        RecommendationFeature.AUDIO_CENTROID, RecommendationFeature.AUDIO_ROLLOFF)

    private fun hasModelCompatibleAudio(values: DoubleArray): Boolean =
        values.size >= audioFeatures.size &&
            values.take(audioFeatures.size).all { it.isFinite() && it in 0.0..1.0 }
}
