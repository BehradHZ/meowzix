package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationFeatureVectorizerTest {
    private val track = TrackPersonalizationFeatures(
        trackId = UUID.nameUUIDFromBytes("canonical-track".toByteArray()),
        normalizedArtist = "artist",
        favorite = false,
        durationMs = 240_000,
    )

    @Test
    fun sameCanonicalTrackGetsDifferentTimeInteractionFeatures() {
        val morning = PersonalizationFeatureVectorizer.vectorize(
            RecommendationContext(9, 2, false, TimeBucket.MORNING),
            track,
        )
        val night = PersonalizationFeatureVectorizer.vectorize(
            RecommendationContext(23, 2, false, TimeBucket.NIGHT),
            track,
        )

        assertEquals(PersonalizationFeatureVectorizer.FEATURE_COUNT, morning.size)
        assertEquals(PersonalizationFeatureVectorizer.FEATURE_COUNT, night.size)
        assertFalse(morning.contentEquals(night))
    }

    @Test
    fun manualCompletionProducesStrongPositiveReward() {
        val reward = PersonalizationRewardBuilder.reward(setOf("MANUAL_SELECTED", "PLAY_COMPLETED"))
        assertTrue(reward > 0.8)
    }

    @Test
    fun earlySkipProducesNegativeRewardEvenAfterManualSelection() {
        val reward = PersonalizationRewardBuilder.reward(setOf("MANUAL_SELECTED", "SKIPPED_EARLY"))
        assertTrue(reward < 0.0)
    }

    @Test
    fun autoplayAloneDoesNotCreatePositiveTrainingReward() {
        assertEquals(0.0, PersonalizationRewardBuilder.reward(setOf("AUTO_SELECTED")), 0.0)
    }

    @Test
    fun learnedPreferenceNudgesButDoesNotReplaceSafetyScore() {
        val candidate = SmartCandidate(
            trackId = track.trackId,
            artist = "artist",
            favorite = false,
            hidden = false,
            usable = true,
            offline = true,
            global = PreferenceSnapshot(starts = 10, completions = 6),
            sessionEarlySkips = 2,
        )
        val scorer = AdaptiveScorer()
        val low = scorer.score(candidate, java.time.Instant.EPOCH, learnedPreference = 0.1)
        val high = scorer.score(candidate, java.time.Instant.EPOCH, learnedPreference = 0.9)

        assertTrue(high.total > low.total)
        assertEquals(low.sessionSkipPenalty, high.sessionSkipPenalty, 0.0)
    }
}
