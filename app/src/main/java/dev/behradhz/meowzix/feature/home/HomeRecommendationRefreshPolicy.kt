package dev.behradhz.meowzix.feature.home

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One recommendation snapshot for each of the five fixed local-time refresh windows. */
internal data class HomeRecommendationSlot(
    val date: LocalDate,
    val hour: Int,
    val zoneId: String,
) {
    val key: String get() = "$date|$hour|$zoneId"
}

internal object HomeRecommendationRefreshPolicy {
    private val scheduledHours = listOf(2, 7, 12, 17, 21)

    fun slot(now: Instant, zone: ZoneId): HomeRecommendationSlot {
        val local = now.atZone(zone)
        val hour = scheduledHours.lastOrNull { it <= local.hour }
        return if (hour == null) {
            HomeRecommendationSlot(local.toLocalDate().minusDays(1), scheduledHours.last(), zone.id)
        } else {
            HomeRecommendationSlot(local.toLocalDate(), hour, zone.id)
        }
    }

    /** Listening events, favorites, and library metadata never invalidate an existing slot. */
    fun needsRefresh(cachedSlotKey: String?, currentSlot: HomeRecommendationSlot): Boolean =
        cachedSlotKey != currentSlot.key

    /** Reuse the same selection after any process restart within the same local time slot. */
    fun seed(slot: HomeRecommendationSlot): Long =
        slot.date.toEpochDay() * 1_000_003L + slot.hour.toLong() * 2_654_435_761L +
            slot.zoneId.hashCode().toLong()

    /** No periodic polling or background job: wake only at the next schedule boundary. */
    fun millisUntilNextSlot(now: Instant, zone: ZoneId): Long {
        val today = now.atZone(zone).toLocalDate()
        val times = listOf(today, today.plusDays(1)).flatMap { day ->
            scheduledHours.map { hour -> day.atTime(hour, 0).atZone(zone).toInstant() }
        }
        val next = times.first { it > now }
        return (Duration.between(now, next).toMillis() + 100L).coerceAtLeast(1L)
    }
}
