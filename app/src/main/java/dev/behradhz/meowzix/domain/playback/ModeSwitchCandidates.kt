package dev.behradhz.meowzix.domain.playback

import java.util.UUID

/** Plan the new generated Next using only canonical IDs. */
internal object ModeSwitchCandidates {
    fun eligible(snapshot: ProgressiveQueueSnapshot, sourceIds: List<UUID>): List<UUID> {
        val excluded = snapshot.tracks.indices
            .filter { it <= snapshot.currentIndex ||
                (it > snapshot.currentIndex && snapshot.origins[it] == QueueItemOrigin.MANUAL) }
            .mapTo(hashSetOf()) { snapshot.tracks[it].id }
        return sourceIds.distinct().filterNot { it in excluded }
    }

    /** Keep original Playlist/Library sorting when switching back to Ordered. */
    fun orderedAfterCurrent(
        sourceIds: List<UUID>,
        currentId: UUID,
        eligibleIds: List<UUID>,
    ): List<UUID> {
        val pivot = sourceIds.indexOf(currentId)
        val rotated = if (pivot < 0) sourceIds else
            sourceIds.drop(pivot + 1) + sourceIds.take(pivot + 1)
        val eligible = eligibleIds.toHashSet()
        return rotated.distinct().filter { it in eligible }
    }
}
