package dev.behradhz.meowzix.feature.home

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecommendationRefreshPolicyTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val morning = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-19T08:00:00Z"), zone)
    private val baseline = HomeRecommendationSignals(40, 2)

    @Test fun minorListeningChangesAndArtworkEditsPreservePicks() {
        assertFalse(HomeRecommendationRefreshPolicy.needsRefresh(morning, baseline, morning, baseline, true))
        assertFalse(HomeRecommendationRefreshPolicy.needsRefresh(
            morning, baseline, morning, baseline.copy(finalizedOutcomes = 44), true,
        ))
    }

    @Test fun fiveFinalizedOutcomesCauseRecalculation() {
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(
            morning, baseline, morning, baseline.copy(finalizedOutcomes = 45), true,
        ))
    }

    @Test fun favoritesAndClearedHistoryCauseRecalculation() {
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(
            morning, baseline, morning, baseline.copy(favoriteChanges = 3), true,
        ))
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(
            morning, baseline, morning, HomeRecommendationSignals(0, 0), true,
        ))
    }

    @Test fun changedTimeSlotOrNewDayCausesRecalculation() {
        val evening = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-19T18:00:00Z"), zone)
        val nextDayMorning = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-20T08:00:00Z"), zone)
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(morning, baseline, evening, baseline, true))
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(morning, baseline, nextDayMorning, baseline, true))
    }

    @Test fun nightSlotCrossesMidnightAndRollsAtOneAm() {
        val before = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-19T22:30:00Z"), zone)
        val after = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-19T23:30:00Z"), zone)
        val lateNight = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-20T00:00:00Z"), zone)
        assertEquals(before, after)
        assertTrue(lateNight != after)
    }

    @Test fun timerTargetsNextSlotBoundaryAndStableSeed() {
        // Berlin winter: 11:55 local -> 12:00 local.
        val now = Instant.parse("2026-11-19T10:55:00Z")
        assertEquals(300_100L, HomeRecommendationRefreshPolicy.millisUntilNextSlot(now, zone))
        assertEquals(HomeRecommendationRefreshPolicy.seed(morning, baseline),
            HomeRecommendationRefreshPolicy.seed(morning, baseline))
        assertTrue(HomeRecommendationRefreshPolicy.seed(morning, baseline) !=
            HomeRecommendationRefreshPolicy.seed(morning, baseline.copy(finalizedOutcomes = 45)))
    }

    @Test fun emptyFeedAlwaysRetries() {
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(morning, baseline, morning, baseline, false))
    }
}
