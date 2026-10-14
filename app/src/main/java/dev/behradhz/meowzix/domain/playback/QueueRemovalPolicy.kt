package dev.behradhz.meowzix.domain.playback

/**
 * Only removing the active occurrence requires selecting/preparing another MediaItem.
 * A non-current occurrence in the bounded Media3 window is removed in place;
 * a non-materialized occurrence is removed from the logical queue only.
 */
internal sealed interface QueueRemovalPlan {
    data object Current : QueueRemovalPlan
    data class Materialized(val mediaItemIndex: Int) : QueueRemovalPlan
    data object LogicalOnly : QueueRemovalPlan
}

internal fun planQueueRemoval(snapshot: ProgressiveQueueSnapshot, index: Int): QueueRemovalPlan? {
    if (index !in snapshot.tracks.indices) return null
    return when {
        index == snapshot.currentIndex -> QueueRemovalPlan.Current
        index in snapshot.materializedStartIndex until snapshot.materializedEndExclusive ->
            QueueRemovalPlan.Materialized(index - snapshot.materializedStartIndex)
        else -> QueueRemovalPlan.LogicalOnly
    }
}
