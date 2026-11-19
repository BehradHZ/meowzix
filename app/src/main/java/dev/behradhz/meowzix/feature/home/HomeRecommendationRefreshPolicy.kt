package dev.behradhz.meowzix.feature.home

import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
import dev.behradhz.meowzix.domain.history.TimeBucket
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class HomeRecommendationSignals(
    val finalizedOutcomes: Long,
    val favoriteChanges: Long,
)

internal data class HomeRecommendationSlot(
    val date: LocalDate,
    val bucket: TimeBucket,
)

/** Stable Home picks within a local listening-time slot, with batched preference updates. */
internal object HomeRecommendationRefreshPolicy {
    const val MEANINGFUL_OUTCOME_BATCH = 5L
    private val slotStartHours = listOf(1, 5, 8, 12, 17, 21)

    fun slot(now: Instant, zone: ZoneId): HomeRecommendationSlot {
        val local = now.atZone(zone)
        // NIGHT crosses midnight; 00:xx is still part of the previous day's night.
        val date = if (local.hour == 0) local.toLocalDate().minusDays(1) else local.toLocalDate()
        return HomeRecommendationSlot(date, ListeningEventSemantics.timeContext(now, zone).bucket)
    }

    fun needsRefresh(
        cachedSlot: HomeRecommendationSlot?,
        cachedSignals: HomeRecommendationSignals?,
        currentSlot: HomeRecommendationSlot,
        currentSignals: HomeRecommendationSignals,
        hasRecommendations: Boolean,
    ): Boolean {
        if (!hasRecommendations || cachedSlot != currentSlot || cachedSignals == null) return true
        if (currentSignals.favoriteChanges != cachedSignals.favoriteChanges) return true
        // Clearing listening history must also invalidate the previous selection.
        if (currentSignals.finalizedOutcomes < cachedSignals.finalizedOutcomes) return true
        return currentSignals.finalizedOutcomes - cachedSignals.finalizedOutcomes >= MEANINGFUL_OUTCOME_BATCH
    }

    fun millisUntilNextSlot(now: Instant, zone: ZoneId): Long {
        val today = now.atZone(zone).toLocalDate()
        val next = (slotStartHours.map { today.atTime(it, 0).atZone(zone).toInstant() } +
            today.plusDays(1).atTime(slotStartHours.first(), 0).atZone(zone).toInstant())
            .first { it > now }
        // Avoid sampling just before the OS clock reaches the boundary.
        return (Duration.between(now, next).toMillis() + 100L).coerceAtLeast(1L)
    }

    fun seed(slot: HomeRecommendationSlot, signals: HomeRecommendationSignals): Long =
        slot.date.toEpochDay() * 1_000_003L +
            slot.bucket.ordinal.toLong() * 2_654_435_761L +
            signals.finalizedOutcomes * 127L + signals.favoriteChanges
}
