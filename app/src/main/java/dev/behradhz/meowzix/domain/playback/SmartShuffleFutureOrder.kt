package dev.behradhz.meowzix.domain.playback

import java.util.UUID

/** Keep the scored Smart Shuffle prefix and every unscored but eligible candidate. */
internal object SmartShuffleFutureOrder {
    fun order(
        requestedFuture: List<UUID>,
        rankedTrackIds: List<UUID>,
        eligibleTrackIds: Set<UUID>,
    ): List<UUID> {
        val eligibleFuture = requestedFuture.distinct().filter { it in eligibleTrackIds }
        val eligibleSet = eligibleFuture.toHashSet()
        val ranked = rankedTrackIds.distinct().filter { it in eligibleSet }
        val rankedSet = ranked.toHashSet()
        return ranked + eligibleFuture.filterNot { it in rankedSet }
    }

    /**
     * An explicit Library playback cycle must eventually cover every distinct selected
     * Library track. Hard-filtered/unscored items remain at the back, where playback
     * source resolution can safely skip ones that have actually become unavailable.
     */
    fun completeLibraryCycle(
        requestedFuture: List<UUID>,
        rankedTrackIds: List<UUID>,
        eligibleTrackIds: Set<UUID>,
    ): List<UUID> {
        val ranked = order(requestedFuture, rankedTrackIds, eligibleTrackIds)
        val included = ranked.toHashSet()
        return ranked + requestedFuture.distinct().filter { included.add(it) }
    }
}
