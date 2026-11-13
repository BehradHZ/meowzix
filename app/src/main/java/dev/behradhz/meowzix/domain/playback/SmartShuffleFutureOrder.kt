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
}
