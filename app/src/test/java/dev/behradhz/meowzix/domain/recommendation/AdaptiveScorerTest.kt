package dev.behradhz.meowzix.domain.recommendation

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveScorerTest {
    private val now = Instant.parse("2026-09-21T18:00:00Z")

    @Test
    fun fixedSeedProducesDeterministicQueue() {
        val scores = (1..20).associate { index ->
            UUID.nameUUIDFromBytes("track-$index".toByteArray()) to emptyBreakdown(index.toDouble())
        }

        assertEquals(SmartSelector.order(scores, 42), SmartSelector.order(scores, 42))
    }

    @Test
    fun repeatedEarlySkipsReduceScore() {
        val scorer = AdaptiveScorer()
        val neutral = candidate(global = PreferenceSnapshot(starts = 8, completions = 5))
        val skipped = neutral.copy(
            global = PreferenceSnapshot(starts = 8, completions = 5, earlySkips = 5),
            sessionEarlySkips = 3,
        )

        assertTrue(scorer.score(skipped, now).total < scorer.score(neutral, now).total)
    }

    @Test
    fun establishedEveningPreferenceRaisesTimeAffinity() {
        val scorer = AdaptiveScorer()
        val neutral = candidate(global = PreferenceSnapshot(starts = 20, completions = 10))
        val eveningPositive = neutral.copy(
            time = PreferenceSnapshot(starts = 20, completions = 19, manualSelections = 15),
        )

        assertTrue(scorer.score(eveningPositive, now).timeAffinity > scorer.score(neutral, now).timeAffinity)
    }

    @Test
    fun unseenTrackGetsExplorationBonus() {
        val scorer = AdaptiveScorer()

        assertTrue(
            scorer.score(candidate(), now).exploration >
                scorer.score(candidate(global = PreferenceSnapshot(starts = 20)), now).exploration,
        )
    }

    @Test
    fun candidateGeneratorKeepsSmallLibrariesUsable() {
        val only = candidate()

        assertEquals(listOf(only), CandidateGenerator.generate(listOf(only), only.trackId, offlineOnly = false))
    }

    @Test
    fun candidateGeneratorHonorsAvailabilityAndOfflineMode() {
        val current = candidate()
        val remote = candidate().copy(trackId = UUID.randomUUID(), offline = false)
        val hidden = candidate().copy(trackId = UUID.randomUUID(), hidden = true)

        assertEquals(
            listOf(current),
            CandidateGenerator.generate(listOf(current, remote, hidden), currentTrackId = null, offlineOnly = true),
        )
    }

    private fun candidate(global: PreferenceSnapshot = PreferenceSnapshot()) = SmartCandidate(
        trackId = UUID.randomUUID(),
        artist = "Artist",
        favorite = false,
        hidden = false,
        usable = true,
        offline = true,
        global = global,
    )

    private fun emptyBreakdown(total: Double) = ScoreBreakdown(total, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
}
