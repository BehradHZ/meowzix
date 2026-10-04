package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import java.util.UUID
import kotlin.math.exp
import kotlin.random.Random

enum class RecommendationReason {
    STRONG_CURRENT_TIME_AFFINITY, HIGH_GLOBAL_AFFINITY, RECENT_MANUAL_SELECTION, RECENT_REPLAY,
    FAVORITE, NOT_PLAYED_RECENTLY, SIMILAR_ARTIST, SIMILAR_ALBUM, SIMILAR_AUDIO_PROFILE,
    UNDER_EXPLORED, HIGH_MODEL_CONFIDENCE, DISCOVERY_PICK, AVOIDED_RECENT_REPETITION,
    EXPLICIT_MORE_LIKE, INSUFFICIENT_EVIDENCE,
}
data class TrackScore(val trackId: UUID, val predictedReward: Double, val uncertainty: Double,
    val finalScore: Double, val reasons: List<RecommendationReason>)
data class RecommendationItem(val trackId: UUID, val score: TrackScore)
enum class RecommendationSectionKind { FOR_YOU_NOW, TIME_MIX, REDISCOVER, HIDDEN_GEMS, BECAUSE_YOU_LISTEN_TO, ON_REPEAT, TRY_AGAIN, CONTINUE_THE_VIBE }
data class RecommendationSection(
    val kind: RecommendationSectionKind,
    val items: List<RecommendationItem>,
    val timeBucket: TimeBucket? = null,
    val anchorTrackId: UUID? = null,
    val anchorTitle: String? = null,
)
data class RecentPositiveEvidence(val manual: Double = 0.0, val replay: Double = 0.0, val completion: Double = 0.0) {
    val strength: Double get() = (0.35 * manual + 0.40 * replay + 0.60 * completion).coerceAtMost(5.0) / 5.0
    val repeatedMeaningful: Boolean get() = replay >= 0.75 || manual + completion >= 1.5 || completion >= 1.75
}
data class RankedRecommendationCandidate(
    val candidate: SmartCandidate,
    val item: RecommendationItem,
    val profile: SimilarityProfile,
    val title: String,
    val recent: RecentPositiveEvidence = RecentPositiveEvidence(),
    val artistAffinity: Double = 0.0,
    val breakdown: ScoreBreakdown,
)

/** Single post-model ranking contract for recommendations and Smart Shuffle. */
object RecommendationRanking {
    private const val MORE_LIKE_BONUS = 0.25
    private const val SUGGEST_LESS_PENALTY = 0.35

    fun score(
        candidate: SmartCandidate,
        context: RecommendationContext,
        model: ModelTrackScore?,
        sampleCount: Long,
        feedback: RecommendationFeedbackEffect = RecommendationFeedbackEffect(),
    ): Pair<TrackScore, ScoreBreakdown> {
        val base = AdaptiveScorer().score(candidate, context.timestamp)
        val blend = if (model == null) 0.0 else RecommendationConfig.learnedWeight(sampleCount)
        val heuristicPreference = (0.4 * base.globalAffinity + 0.3 * base.timeAffinity) / 0.7
        val learnedPreference = model?.let { (it.expectedReward + 1.0) / 2.0 } ?: heuristicPreference
        val underObserved = 1.0 / (1.0 + candidate.global.starts.coerceAtLeast(0))
        val uncertainty = model?.uncertainty ?: underObserved
        val exploration = ((1.0 - blend) * 0.10 * underObserved + blend * RecommendationConfig.ALPHA * uncertainty / (1.0 + uncertainty))
            .coerceIn(0.0, RecommendationConfig.MAX_EXPLORATION_BONUS)
        val explicit = (if (feedback.moreLikeThis) MORE_LIKE_BONUS else 0.0) -
            (if (feedback.suggestLess) SUGGEST_LESS_PENALTY else 0.0)
        val total = base.total - 0.10 * base.exploration + exploration +
            0.70 * blend * (learnedPreference - heuristicPreference) + explicit
        val reasons = buildList {
            if (feedback.moreLikeThis) add(RecommendationReason.EXPLICIT_MORE_LIKE)
            if (candidate.time.starts >= 3 && base.timeAffinity > base.globalAffinity + 0.03) add(RecommendationReason.STRONG_CURRENT_TIME_AFFINITY)
            if (candidate.global.starts >= 3 && candidate.global.completions.toDouble() / candidate.global.starts >= 0.60) add(RecommendationReason.HIGH_GLOBAL_AFFINITY)
            if (candidate.favorite) add(RecommendationReason.FAVORITE)
            if (candidate.lastPlayedAt != null && base.recencyPenalty == 0.0) add(RecommendationReason.NOT_PLAYED_RECENTLY)
            if (candidate.artist != null && candidate.recentArtistDistance == null && context.recentArtistIds.any(String::isNotBlank)) add(RecommendationReason.AVOIDED_RECENT_REPETITION)
            if (model != null && blend > 0.0 && model.uncertainty < 0.5 && model.expectedReward > 0.0 && model.contributions.isNotEmpty()) add(RecommendationReason.HIGH_MODEL_CONFIDENCE)
            if (candidate.global.starts <= 3 && exploration > 0.025) add(RecommendationReason.DISCOVERY_PICK)
            if (isEmpty()) add(if (candidate.global.starts <= 3) RecommendationReason.UNDER_EXPLORED else RecommendationReason.INSUFFICIENT_EVIDENCE)
        }.take(4)
        return TrackScore(candidate.trackId, model?.expectedReward ?: (heuristicPreference * 2.0 - 1.0), uncertainty, total, reasons) to
            base.copy(total = total, learnedPreference = model?.let { learnedPreference })
    }
}

/** Section policies select from one shared ranked pool but apply policy-specific evidence. */
class RecommendationSectionGenerator(private val similarity: TrackSimilarityCalculator = LocalTrackSimilarityCalculator()) {
    fun sections(pool: List<RankedRecommendationCandidate>, context: RecommendationContext, seed: Long,
        anchors: List<RankedRecommendationCandidate>, limit: Int = RecommendationConfig.SECTION_SIZE): List<RecommendationSection> {
        val timeEvidence = pool.count { it.candidate.time.starts >= MIN_TIME_BUCKET_STARTS }
        val timePool = if (timeEvidence >= MIN_TIME_BUCKET_CANDIDATES) {
            pool.map(::timeMixAdjusted)
        } else {
            // Honest sparse-data fallback: the same eligible pool is used without pretending weak
            // bucket evidence is stronger than global preference.
            pool
        }
        val result = arrayListOf(
            section(RecommendationSectionKind.FOR_YOU_NOW, pool, context, seed, limit),
            section(RecommendationSectionKind.TIME_MIX, timePool, context, seed + 1, limit),
        )
        val positive = pool.filter { previouslyLiked(it) && !stronglyRejected(it) }
        val horizon = context.timestamp.minusSeconds(RecommendationConfig.REDISCOVER_DAYS * 86_400)
        val rediscover = positive.filter { it.candidate.lastPlayedAt?.isBefore(horizon) == true }
            .ifEmpty { positive.filter { it.candidate.lastPlayedAt?.isBefore(context.timestamp.minusSeconds(2 * 86_400)) == true } }
        result += section(RecommendationSectionKind.REDISCOVER, rediscover, context, seed + 2, limit)
        val gems = pool.filter {
            it.candidate.global.starts <= 3 && !stronglyRejected(it) &&
                (it.item.score.predictedReward > 0.0 || it.artistAffinity > 0.15 || it.candidate.favorite ||
                    anchors.any { anchor -> similarity.similarity(anchor.profile, it.profile).score >= 0.30 })
        }
        result += section(RecommendationSectionKind.HIDDEN_GEMS, gems, context, seed + 3, limit)
        anchors.filter { it.recent.strength > 0.05 || previouslyLiked(it) }.distinctBy { it.candidate.trackId }.take(2).forEachIndexed { index, anchor ->
            val related = related(pool, anchor)
            if (related.isNotEmpty()) result += section(RecommendationSectionKind.BECAUSE_YOU_LISTEN_TO, related,
                context, seed + 10 + index, limit, anchor)
        }
        if (result.none { it.kind == RecommendationSectionKind.BECAUSE_YOU_LISTEN_TO }) result += RecommendationSection(RecommendationSectionKind.BECAUSE_YOU_LISTEN_TO, emptyList())
        val repeated = pool.filter { it.recent.repeatedMeaningful && !stronglyRejected(it) }.map {
            val reasons = buildList {
                if (it.recent.replay > 0.25) add(RecommendationReason.RECENT_REPLAY)
                if (it.recent.manual > 0.25) add(RecommendationReason.RECENT_MANUAL_SELECTION)
                addAll(it.item.score.reasons)
            }.distinct().take(4)
            it.withScore(it.item.score.finalScore + 0.25 * it.recent.strength, reasons)
        }
        result += section(RecommendationSectionKind.ON_REPEAT, repeated, context, seed + 4, limit)
        val ambiguous = pool.filter {
            val stats = it.candidate.global
            stats.starts in 1..4 && stats.earlySkips == 1 &&
                !stronglyRejected(it) && it.item.score.finalScore > -0.10 && it.item.score.uncertainty > 0.05
        }
        result += section(RecommendationSectionKind.TRY_AGAIN, ambiguous, context, seed + 5, limit)
        anchors.firstOrNull()?.let { anchor -> result += section(RecommendationSectionKind.CONTINUE_THE_VIBE,
            related(pool, anchor), context, seed + 6, limit, anchor) }
        if (result.none { it.kind == RecommendationSectionKind.CONTINUE_THE_VIBE }) result += RecommendationSection(RecommendationSectionKind.CONTINUE_THE_VIBE, emptyList())
        return result
    }

    fun continueTheVibe(pool: List<RankedRecommendationCandidate>, anchor: RankedRecommendationCandidate,
        context: RecommendationContext, seed: Long, limit: Int): List<RecommendationItem> =
        section(RecommendationSectionKind.CONTINUE_THE_VIBE, related(pool, anchor), context, seed, limit, anchor).items

    private fun timeMixAdjusted(candidate: RankedRecommendationCandidate): RankedRecommendationCandidate {
        val starts = candidate.candidate.time.starts.coerceAtLeast(0)
        val confidence = starts.toDouble() / (starts + TIME_CONFIDENCE_K)
        val affinityLift = (candidate.breakdown.timeAffinity - candidate.breakdown.globalAffinity).coerceIn(-0.30, 0.30)
        val boost = confidence * (TIME_MIX_WEIGHT * affinityLift + TIME_EVIDENCE_BONUS)
        val reasons = buildList {
            if (boost > 0.02) add(RecommendationReason.STRONG_CURRENT_TIME_AFFINITY)
            addAll(candidate.item.score.reasons)
        }.distinct().take(4)
        return candidate.withScore(candidate.item.score.finalScore + boost, reasons)
    }

    private fun related(pool: List<RankedRecommendationCandidate>, anchor: RankedRecommendationCandidate): List<RankedRecommendationCandidate> =
        pool.filter { it.candidate.trackId != anchor.candidate.trackId && !stronglyRejected(it) }.mapNotNull { candidate ->
            val evidence = similarity.similarity(anchor.profile, candidate.profile)
            if (evidence.score < 0.30 && candidate.artistAffinity <= 0.20) return@mapNotNull null
            val reasons = buildList {
                if (evidence.sameArtist) add(RecommendationReason.SIMILAR_ARTIST)
                if (evidence.sameAlbum) add(RecommendationReason.SIMILAR_ALBUM)
                if ((evidence.audioSimilarity ?: 0.0) >= 0.75) add(RecommendationReason.SIMILAR_AUDIO_PROFILE)
                addAll(candidate.item.score.reasons)
            }.distinct().take(4)
            candidate.withScore(candidate.item.score.finalScore + 0.25 * evidence.score, reasons)
        }

    private fun section(kind: RecommendationSectionKind, candidates: List<RankedRecommendationCandidate>,
        context: RecommendationContext, seed: Long, limit: Int, anchor: RankedRecommendationCandidate? = null): RecommendationSection {
        val items = diverseOrder(candidates, seed, limit.coerceIn(0, 50))
        return RecommendationSection(kind, items, if (kind == RecommendationSectionKind.TIME_MIX) context.timeBucket else null,
            anchor?.candidate?.trackId, anchor?.title)
    }

    /** Sampling a top window + soft sequential artist penalty. Small one-artist libraries stay usable. */
    fun diverseOrder(pool: List<RankedRecommendationCandidate>, seed: Long, limit: Int): List<RecommendationItem> {
        val random = Random(seed)
        val remaining = pool.distinctBy { it.candidate.trackId }.sortedWith(compareByDescending<RankedRecommendationCandidate> { it.item.score.finalScore }.thenBy { it.candidate.trackId }).toMutableList()
        val output = ArrayList<RecommendationItem>()
        val artists = ArrayList<String?>()
        while (remaining.isNotEmpty() && output.size < limit) {
            val window = remaining.take(10)
            val scores = window.map { candidate ->
                val distance = candidate.profile.artist?.let { artists.indexOf(it) } ?: -1
                candidate.item.score.finalScore - if (distance >= 0) 0.20 / (distance + 1) else 0.0
            }
            val max = scores.maxOrNull() ?: 0.0
            val weights = scores.map { exp((it - max) / 0.15) }
            var roll = random.nextDouble() * weights.sum()
            var index = window.lastIndex
            for (i in window.indices) { roll -= weights[i]; if (roll <= 0.0) { index = i; break } }
            val selected = window[index]
            val variety = selected.profile.artist != null && artists.any { it != null } && selected.profile.artist !in artists
            val reasons = (selected.item.score.reasons + if (variety) listOf(RecommendationReason.AVOIDED_RECENT_REPETITION) else emptyList()).distinct().take(4)
            output += selected.item.copy(score = selected.item.score.copy(finalScore = scores[index], reasons = reasons))
            artists.add(0, selected.profile.artist)
            if (artists.size > 5) artists.removeAt(artists.lastIndex)
            remaining.remove(selected)
        }
        return output
    }

    private fun previouslyLiked(candidate: RankedRecommendationCandidate): Boolean = candidate.candidate.favorite ||
        (candidate.candidate.global.starts >= 2 && (candidate.candidate.global.completions >= 1 || candidate.candidate.global.manualSelections >= 2))
    fun stronglyRejected(candidate: RankedRecommendationCandidate): Boolean = candidate.candidate.global.let {
        it.earlySkips >= 3 && it.earlySkips.toDouble() / it.starts.coerceAtLeast(1) >= 0.70 && it.completions == 0 && it.replays == 0
    }
    private fun RankedRecommendationCandidate.withScore(score: Double, reasons: List<RecommendationReason>) =
        copy(item = item.copy(score = item.score.copy(finalScore = score, reasons = reasons)))

    private companion object {
        const val MIN_TIME_BUCKET_STARTS = 3
        const val MIN_TIME_BUCKET_CANDIDATES = 3
        const val TIME_CONFIDENCE_K = 5.0
        const val TIME_MIX_WEIGHT = 0.90
        const val TIME_EVIDENCE_BONUS = 0.08
    }
}
