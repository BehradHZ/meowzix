package dev.behradhz.meowzix.feature.home

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecommendationRefreshPolicyTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test fun exactlyFiveSlotsEveryNormalDay() {
        val date = "2026-11-19"
        val instants = listOf("00:30", "02:00", "06:59", "07:00", "11:59",
            "12:00", "16:59", "17:00", "20:59", "21:00", "23:59")
        val hours = listOf(21, 2, 2, 7, 7, 12, 12, 17, 17, 21, 21)
        val dates = listOf("2026-11-18") + List(instants.size - 1) { date }
        instants.forEachIndexed { index, time ->
            val instant = java.time.LocalDateTime.parse("$date"+"T"+time).atZone(berlin).toInstant()
            val slot = HomeRecommendationRefreshPolicy.slot(instant, berlin)
            assertEquals(hours[index], slot.hour)
            assertEquals(dates[index], slot.date.toString())
        }
    }

    @Test fun listeningActivityAndMetadataNeverChangeTheSlot() {
        val now = Instant.parse("2026-11-19T08:30:00Z")
        val slot = HomeRecommendationRefreshPolicy.slot(now, berlin)
        assertFalse(HomeRecommendationRefreshPolicy.needsRefresh(slot.key, slot))
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(null, slot))
        val later = HomeRecommendationRefreshPolicy.slot(now.plusSeconds(1800), berlin)
        assertEquals(slot.key, later.key)
        assertEquals(HomeRecommendationRefreshPolicy.seed(slot), HomeRecommendationRefreshPolicy.seed(later))
    }

    @Test fun timeBoundaryAndNewDayRequireRefresh() {
        val before = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-19T10:59:00Z"), berlin)
        val after = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-19T11:00:00Z"), berlin)
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(before.key, after))
        val nextMorning = HomeRecommendationRefreshPolicy.slot(Instant.parse("2026-11-20T01:00:00Z"), berlin)
        assertTrue(HomeRecommendationRefreshPolicy.needsRefresh(after.key, nextMorning))
    }

    @Test fun timerWaitsForCorrectLocalHour() {
        // Berlin winter: 11:55 -> 12:00
        assertEquals(300_100L, HomeRecommendationRefreshPolicy.millisUntilNextSlot(
            Instant.parse("2026-11-19T10:55:00Z"), berlin))
        // Berlin winter: 23:00 -> 02:00 next day
        assertEquals(10_800_100L, HomeRecommendationRefreshPolicy.millisUntilNextSlot(
            Instant.parse("2026-11-19T22:00:00Z"), berlin))
    }

    @Test fun springDstGapUsesTheFirstValidInstantAtTwoAm() {
        // Berlin skips local 02:00 on this day; the 02:00 scheduled slot starts at 03:00.
        val before = Instant.parse("2027-03-28T00:59:00Z")
        val after = Instant.parse("2027-03-28T01:00:00Z")
        assertEquals(21, HomeRecommendationRefreshPolicy.slot(before, berlin).hour)
        assertEquals(2, HomeRecommendationRefreshPolicy.slot(after, berlin).hour)
        assertEquals(60_100L, HomeRecommendationRefreshPolicy.millisUntilNextSlot(before, berlin))
    }
}
