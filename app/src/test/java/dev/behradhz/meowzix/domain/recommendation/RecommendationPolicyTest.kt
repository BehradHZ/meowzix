package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.playback.PureShuffleEngine
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class RecommendationPolicyTest {
    private val now = Instant.parse("2026-10-02T20:00:00Z")
    private val context = RecommendationContext(23, 5, false, TimeBucket.NIGHT, now)

    private fun candidate(
        id: Long,
        stats: PreferenceSnapshot = PreferenceSnapshot(),
        time: PreferenceSnapshot = PreferenceSnapshot(),
        last: Instant? = null,
        artist: String = "artist$id",
        recent: RecentPositiveEvidence = RecentPositiveEvidence(),
    ): RankedRecommendationCandidate {
        val c = SmartCandidate(
            trackId = UUID(0, id),
            artist = artist,
            favorite = false,
            hidden = false,
            usable = true,
            offline = true,
            global = stats,
            time = time,
            lastPlayedAt = last,
        )
        val (score, breakdown) = RecommendationRanking.score(c, context, ModelTrackScore(c.trackId, 0.4, 0.5), 200)
        return RankedRecommendationCandidate(
            candidate = c,
            item = RecommendationItem(c.trackId, score),
            profile = SimilarityProfile(artist, "album", null),
            title = "Song $id",
            recent = recent,
            breakdown = breakdown,
        )
    }

    @Test fun coldStartAndGradualBlendBoundariesAreExplicit() {
        assertEquals(0.0, RecommendationConfig.learnedWeight(74), 0.0)
        assertEquals(0.05, RecommendationConfig.learnedWeight(75), 1e-12)
        assertTrue(RecommendationConfig.learnedWeight(199) < RecommendationConfig.learnedWeight(200))
        assertEquals(0.85, RecommendationConfig.learnedWeight(200), 1e-12)
        assertEquals(0.85, RecommendationConfig.learnedWeight(1000), 1e-12)
    }

    @Test fun modelCannotBypassOfflineHiddenAndCurrentFilters() {
        val current = candidate(1).candidate
        val hidden = candidate(2).candidate.copy(hidden = true)
        val cloud = candidate(3).candidate.copy(offline = false)
        val offline = candidate(4).candidate
        assertEquals(listOf(offline), CandidateGenerator.generate(listOf(current, hidden, cloud, offline), current.trackId, true))
        assertEquals(listOf(current), CandidateGenerator.generate(listOf(current), current.trackId, true))
    }

    @Test fun modelFailureAndZeroHistoryStillProduceForYouAndTimeMix() {
        val pool = (1L..4L).map { candidate(it) }
        val sections = RecommendationSectionGenerator().sections(pool, context, 42, emptyList())
        assertEquals(8, sections.size)
        assertTrue(sections.first().items.isNotEmpty())
        assertTrue(sections[1].items.isNotEmpty())
    }

    @Test fun timeMixStrengthensRealBucketEvidenceWhenEnoughCandidatesExist() {
        val timeStrong = candidate(
            1,
            stats = PreferenceSnapshot(starts = 12, completions = 5),
            time = PreferenceSnapshot(starts = 8, completions = 8, manualSelections = 3),
        )
        val neutral = candidate(
            2,
            stats = PreferenceSnapshot(starts = 12, completions = 8),
            time = PreferenceSnapshot(starts = 8, completions = 4),
        )
        val timeWeak = candidate(
            3,
            stats = PreferenceSnapshot(starts = 12, completions = 9),
            time = PreferenceSnapshot(starts = 8, earlySkips = 7),
        )
        val pool = listOf(timeStrong, neutral, timeWeak, candidate(4))
        val sections = RecommendationSectionGenerator().sections(pool, context, 7, emptyList())
        val forYou = sections.first { it.kind == RecommendationSectionKind.FOR_YOU_NOW }.items.associateBy { it.trackId }
        val timeMix = sections.first { it.kind == RecommendationSectionKind.TIME_MIX }.items.associateBy { it.trackId }
        assertTrue(timeMix.getValue(timeStrong.candidate.trackId).score.finalScore > forYou.getValue(timeStrong.candidate.trackId).score.finalScore)
        assertTrue(timeMix.getValue(timeWeak.candidate.trackId).score.finalScore <= forYou.getValue(timeWeak.candidate.trackId).score.finalScore + 0.08)
    }

    @Test fun onRepeatNeedsRepeatedMeaningfulEvidenceNotOneAutoplayCompletion() {
        val singleCompletion = candidate(1, recent = RecentPositiveEvidence(completion = 1.0))
        val repeated = candidate(2, recent = RecentPositiveEvidence(replay = 1.0, completion = 1.0))
        val section = RecommendationSectionGenerator()
            .sections(listOf(singleCompletion, repeated), context, 42, emptyList())
            .first { it.kind == RecommendationSectionKind.ON_REPEAT }
        assertEquals(listOf(repeated.candidate.trackId), section.items.map { it.trackId })
    }

    @Test fun rediscoverRequiresPositiveEvidenceAndAge() {
        val liked = candidate(1, PreferenceSnapshot(starts = 10, completions = 8), last = now.minusSeconds(30 * 86400L))
        val oldRejected = candidate(2, PreferenceSnapshot(starts = 10, earlySkips = 10), last = now.minusSeconds(60 * 86400L))
        val neverPlayed = candidate(3)
        val section = RecommendationSectionGenerator().sections(listOf(liked, oldRejected, neverPlayed), context, 42, emptyList())
            .first { it.kind == RecommendationSectionKind.REDISCOVER }
        assertEquals(listOf(liked.candidate.trackId), section.items.map { it.trackId })
    }

    @Test fun tryAgainAllowsOneAccidentalSkipButRejectsConsistentNegativeEvidence() {
        val ambiguous = candidate(1, PreferenceSnapshot(starts = 1, earlySkips = 1), last = now.minusSeconds(86400L))
        val rejected = candidate(2, PreferenceSnapshot(starts = 4, earlySkips = 4), last = now.minusSeconds(86400L))
        val section = RecommendationSectionGenerator().sections(listOf(ambiguous, rejected), context, 42, emptyList())
            .first { it.kind == RecommendationSectionKind.TRY_AGAIN }
        assertEquals(listOf(ambiguous.candidate.trackId), section.items.map { it.trackId })
    }

    @Test fun explicitFeedbackChangesRankingAndIsReversibleByConstruction() {
        val c = candidate(1).candidate
        val baseline = RecommendationRanking.score(c, context, null, 0).first.finalScore
        val more = RecommendationRanking.score(c, context, null, 0, RecommendationFeedbackEffect(moreLikeThis = true)).first
        val less = RecommendationRanking.score(c, context, null, 0, RecommendationFeedbackEffect(suggestLess = true)).first
        val reversed = RecommendationRanking.score(c, context, null, 0, RecommendationFeedbackEffect()).first
        assertTrue(more.finalScore > baseline)
        assertTrue(less.finalScore < baseline)
        assertEquals(baseline, reversed.finalScore, 0.0)
        assertTrue(RecommendationReason.EXPLICIT_MORE_LIKE in more.reasons)
    }

    @Test fun feedbackExpiryOnlyExcludesWhileActive() {
        val trackId = UUID(0, 99)
        val active = RecommendationFeedback(trackId, RecommendationFeedbackAction.SNOOZE, now, now.plus(Duration.ofHours(24)))
        val expired = RecommendationFeedback(UUID(0, 100), RecommendationFeedbackAction.SNOOZE, now.minus(Duration.ofHours(48)), now.minus(Duration.ofHours(24)))
        val effects = listOf(active, expired).effects(now)
        assertTrue(effects.getValue(trackId).excluded)
        assertFalse(expired.trackId in effects)
    }

    @Test fun continueTheVibeWorksWithoutAudioAndExcludesAnchor() {
        val anchor = candidate(1, artist = "artist")
        val related = candidate(2, artist = "artist")
        val unrelated = candidate(3, artist = "other")
        val result = RecommendationSectionGenerator().continueTheVibe(listOf(anchor, related, unrelated), anchor, context, 42, 15)
        assertEquals(listOf(related.candidate.trackId), result.map { it.trackId })
        assertTrue(RecommendationReason.SIMILAR_ARTIST in result.single().score.reasons)
        assertFalse(RecommendationReason.SIMILAR_AUDIO_PROFILE in result.single().score.reasons)
    }

    @Test fun homeOnlyRankingIsEquivalentToTheFirstFullSection() {
        val pool = (1L..25L).map { candidate(it) }
        val generator = RecommendationSectionGenerator()
        val seed = 42L
        val full = generator.sections(pool, context, seed, emptyList())
            .first { it.kind == RecommendationSectionKind.FOR_YOU_NOW }
            .items
        val homeOnly = generator.diverseOrder(pool, seed, RecommendationConfig.SECTION_SIZE)
        assertEquals(full, homeOnly)
    }

    @Test fun diversitySamplingIsSeededUniqueAndVariesAcrossSeeds() {
        val pool = (1L..30L).map { candidate(it) }
        val generator = RecommendationSectionGenerator()
        val one = generator.diverseOrder(pool, 42, 20)
        assertEquals(one, generator.diverseOrder(pool, 42, 20))
        assertEquals(one.size, one.map { it.trackId }.distinct().size)
        assertNotEquals(one, generator.diverseOrder(pool, 43, 20))
    }

    @Test fun learnedExplorationIsBoundedEvenWithExtremeUncertainty() {
        val c = candidate(1).candidate
        val low = RecommendationRanking.score(c, context, ModelTrackScore(c.trackId, 0.4, 0.0), 200).first
        val high = RecommendationRanking.score(c, context, ModelTrackScore(c.trackId, 0.4, 100.0), 200).first
        assertTrue(high.finalScore - low.finalScore <= RecommendationConfig.MAX_EXPLORATION_BONUS + 1e-12)
    }

    @Test fun pureShuffleIsIndependentOfEveryPersonalizationChange() {
        val ids = (1L..20L).map { UUID(0, it) }
        val before = PureShuffleEngine.newCycle(ids, seed = 42L)
        val model = SharedLinUcb()
        repeat(100) { model.update(DoubleArray(model.dimension) { 0.5 }, -0.7) }
        assertEquals(before, PureShuffleEngine.newCycle(ids, seed = 42L))
    }

    @Test fun explorationSettingControlsRealDiscoveryWithoutForcingAlternation() {
        fun scored(candidate: RankedRecommendationCandidate, value: Double) =
            candidate.copy(item = candidate.item.copy(score = candidate.item.score.copy(finalScore = value)))
        val known = (1L..50L).map {
            scored(candidate(it, stats = PreferenceSnapshot(starts = 20, completions = 15)), 2.0)
        }
        val unheard = (51L..100L).map { scored(candidate(it), -2.0) }
        val pool = known + unheard
        val generator = RecommendationSectionGenerator()
        val off = generator.diverseOrder(pool, 42L, 20, explorationPercent = 0)
        val half = generator.diverseOrder(pool, 42L, 20, explorationPercent = 50)
        val unheardIds = unheard.map { it.candidate.trackId }.toSet()

        assertTrue(off.none { it.trackId in unheardIds })
        assertTrue(half.any { it.trackId in unheardIds })
        assertEquals(half, generator.diverseOrder(pool, 42L, 20, explorationPercent = 50))
        assertEquals(20, half.map { it.trackId }.distinct().size)
        assertTrue(half.any { RecommendationReason.DISCOVERY_PICK in it.score.reasons })
        assertTrue(half.any { it.trackId !in unheardIds })
    }

    @Test fun exposureRotatesThroughUnplayedTracksAcrossRegeneratedQueues() {
        val generator = RecommendationSectionGenerator()
        val neverPlayed = (1L..50L).map { candidate(it) }
        val known = (51L..100L).map {
            val scored = candidate(it, stats = PreferenceSnapshot(starts = 20, completions = 15))
            scored.copy(item = scored.item.copy(score = scored.item.score.copy(finalScore = 2.0)))
        }
        var pool = neverPlayed + known
        val seen = mutableSetOf<UUID>()
        repeat(20) { round ->
            val result = generator.diverseOrder(pool, round.toLong() + 100L, 10, explorationPercent = 50)
            val newIds = result.map { it.trackId }.filter { it in neverPlayed.map { n -> n.candidate.trackId } }.toSet()
            seen += newIds
            pool = pool.map { item ->
                if (item.candidate.trackId in newIds) item.copy(
                    candidate = item.candidate.copy(global = item.candidate.global.copy(starts = 1)),
                ) else item
            }
        }
        assertEquals(50, seen.size)
    }

    @Test fun explorationBonusRespectsOffAndMaxBounds() {
        val c = candidate(111).candidate
        val off = RecommendationRanking.score(c, context, null, 0, explorationPercent = 0).first
        val normal = RecommendationRanking.score(c, context, null, 0, explorationPercent = 15).first
        val high = RecommendationRanking.score(c, context, null, 0, explorationPercent = 50).first
        assertTrue(off.finalScore < normal.finalScore)
        assertTrue(normal.finalScore <= high.finalScore)
        assertTrue(high.finalScore - off.finalScore <= RecommendationConfig.MAX_EXPLORATION_BONUS + 1e-12)
    }

}
