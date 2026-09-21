package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

data class PreferenceSnapshot(
    val starts: Int = 0,
    val completions: Int = 0,
    val earlySkips: Int = 0,
    val manualSelections: Int = 0,
    val replays: Int = 0,
)

data class SmartCandidate(
    val trackId: UUID,
    val artist: String?,
    val favorite: Boolean,
    val hidden: Boolean,
    val usable: Boolean,
    val offline: Boolean,
    val global: PreferenceSnapshot = PreferenceSnapshot(),
    val time: PreferenceSnapshot = PreferenceSnapshot(),
    val lastPlayedAt: Instant? = null,
    val recentArtistDistance: Int? = null,
    val sessionEarlySkips: Int = 0,
)

data class ScoreBreakdown(
    val total: Double,
    val globalAffinity: Double,
    val timeAffinity: Double,
    val exploration: Double,
    val recencyPenalty: Double,
    val artistPenalty: Double,
    val sessionSkipPenalty: Double,
) {
    fun explanation(): List<String> = buildList {
        if (timeAffinity > globalAffinity + 0.1) add("strong time-of-day preference")
        if (recencyPenalty == 0.0) add("not played recently")
        if (artistPenalty == 0.0) add("artist not repeated")
        if (exploration > 0.5) add("discovery bonus")
    }
}

data class SmartQueue(val trackIds: List<UUID>, val explanations: Map<UUID, ScoreBreakdown>)

interface RecommendationEngine {
    suspend fun generate(
        allowedTrackIds: List<UUID>? = null,
        currentTrackId: UUID? = null,
        timeBucket: TimeBucket,
        seed: Long = System.nanoTime(),
    ): SmartQueue
}

object CandidateGenerator {
    fun generate(candidates: List<SmartCandidate>, currentTrackId: UUID?, offlineOnly: Boolean): List<SmartCandidate> {
        val valid = candidates.filter { !it.hidden && it.usable && (!offlineOnly || it.offline) }
        if (valid.size <= 1) return valid
        return valid.filterNot { it.trackId == currentTrackId }.ifEmpty { valid }
    }
}

data class AdaptiveScoreWeights(
    val global: Double = 0.40,
    val time: Double = 0.30,
    val exploration: Double = 0.10,
    val freshness: Double = 0.10,
    val diversity: Double = 0.10,
    val timeConfidenceK: Double = 5.0,
)

class AdaptiveScorer(private val weights: AdaptiveScoreWeights = AdaptiveScoreWeights()) {
    fun score(candidate: SmartCandidate, now: Instant): ScoreBreakdown {
        val global = affinity(candidate.global, candidate.favorite)
        val observedTime = affinity(candidate.time, candidate.favorite)
        val confidence = candidate.time.starts / (candidate.time.starts + weights.timeConfidenceK)
        val time = confidence * observedTime + (1.0 - confidence) * global
        val exploration = (1.0 / (1.0 + candidate.global.starts)).coerceIn(0.0, 1.0)
        val elapsedMinutes = candidate.lastPlayedAt?.let { (now.toEpochMilli() - it.toEpochMilli()).coerceAtLeast(0) / 60_000.0 }
        val recency = when {
            elapsedMinutes == null -> 0.0
            elapsedMinutes < 30 -> 0.45
            elapsedMinutes < 120 -> 0.25
            elapsedMinutes < 24 * 60 -> 0.10
            else -> 0.0
        }
        val artist = candidate.recentArtistDistance?.let { 0.20 / (it + 1) } ?: 0.0
        val sessionSkip = (candidate.sessionEarlySkips * 0.12).coerceAtMost(0.36)
        val freshness = 1.0 - recency.coerceIn(0.0, 1.0)
        val diversity = 1.0 - (artist * 4).coerceIn(0.0, 1.0)
        val total = weights.global * global + weights.time * time + weights.exploration * exploration +
            weights.freshness * freshness + weights.diversity * diversity - recency - artist - sessionSkip
        return ScoreBreakdown(total, global, time, exploration, recency, artist, sessionSkip)
    }

    private fun affinity(stats: PreferenceSnapshot, favorite: Boolean): Double {
        val starts = max(0, stats.starts)
        val completion = (stats.completions + 2.0) / (starts + 4.0)
        val earlySkip = (stats.earlySkips + 1.0) / (starts + 4.0)
        val manual = stats.manualSelections.toDouble() / max(1, starts)
        val replay = stats.replays.toDouble() / max(1, starts)
        return (0.35 * completion - 0.30 * earlySkip + 0.20 * manual.coerceAtMost(1.0) +
            0.10 * replay.coerceAtMost(1.0) + if (favorite) 0.05 else 0.0).coerceIn(0.0, 1.0)
    }
}

object SmartSelector {
    fun order(scores: Map<UUID, ScoreBreakdown>, seed: Long): List<UUID> {
        val random = Random(seed)
        val remaining = scores.toMutableMap()
        return buildList {
            while (remaining.isNotEmpty()) {
                val window = remaining.entries.sortedByDescending { it.value.total }.take(10)
                val floor = window.minOf { it.value.total }
                val weights = window.map { (it.value.total - floor + 0.05).coerceAtLeast(0.01) }
                var roll = random.nextDouble() * weights.sum()
                var selected = window.last()
                for (index in window.indices) {
                    roll -= weights[index]
                    if (roll <= 0.0) { selected = window[index]; break }
                }
                add(selected.key)
                remaining.remove(selected.key)
            }
        }
    }
}
