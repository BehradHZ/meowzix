package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.playback.PureShuffleEngine
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class RecommendationPolicyTest {
    private val now = Instant.parse("2026-10-02T20:00:00Z")
    private val context = RecommendationContext(23, 5, false, TimeBucket.NIGHT, now)
    private fun candidate(id: Long, stats: PreferenceSnapshot = PreferenceSnapshot(), last: Instant? = null, artist: String = "artist$id"): RankedRecommendationCandidate {
        val c = SmartCandidate(UUID(0, id), artist, false, false, true, true, stats, lastPlayedAt = last)
        val (score, breakdown) = RecommendationRanking.score(c, context, ModelTrackScore(c.trackId, 0.4, 0.5), 200)
        return RankedRecommendationCandidate(c, RecommendationItem(c.trackId, score), SimilarityProfile(artist, "album", null), "Song $id", breakdown = breakdown)
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
    @Test fun rediscoverRequiresPositiveEvidenceAndAge() {
        val liked = candidate(1, PreferenceSnapshot(starts = 10, completions = 8), now.minusSeconds(30 * 86400L))
        val oldRejected = candidate(2, PreferenceSnapshot(starts = 10, earlySkips = 10), now.minusSeconds(60 * 86400L))
        val neverPlayed = candidate(3)
        val section = RecommendationSectionGenerator().sections(listOf(liked, oldRejected, neverPlayed), context, 42, emptyList()).first { it.kind == RecommendationSectionKind.REDISCOVER }
        assertEquals(listOf(liked.candidate.trackId), section.items.map { it.trackId })
    }
    @Test fun tryAgainAllowsOneAccidentalSkipButRejectsConsistentNegativeEvidence() {
        val ambiguous = candidate(1, PreferenceSnapshot(starts = 1, earlySkips = 1), now.minusSeconds(86400L))
        val rejected = candidate(2, PreferenceSnapshot(starts = 4, earlySkips = 4), now.minusSeconds(86400L))
        val section = RecommendationSectionGenerator().sections(listOf(ambiguous, rejected), context, 42, emptyList()).first { it.kind == RecommendationSectionKind.TRY_AGAIN }
        assertEquals(listOf(ambiguous.candidate.trackId), section.items.map { it.trackId })
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
}
