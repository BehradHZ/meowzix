package dev.behradhz.meowzix.feature.queue

import kotlin.math.abs

/** Two intentional thresholds: queue actions first, destructive removal farther away. */
internal enum class QueueSwipeAction { NONE, PLAY_NEXT, ADD_TO_QUEUE, REMOVE }

internal fun queueSwipeActionForOffset(
    offsetPx: Float,
    actionThresholdPx: Float,
    removeThresholdPx: Float,
): QueueSwipeAction {
    require(actionThresholdPx > 0f && removeThresholdPx > actionThresholdPx)
    return when {
        abs(offsetPx) >= removeThresholdPx -> QueueSwipeAction.REMOVE
        offsetPx >= actionThresholdPx -> QueueSwipeAction.PLAY_NEXT
        offsetPx <= -actionThresholdPx -> QueueSwipeAction.ADD_TO_QUEUE
        else -> QueueSwipeAction.NONE
    }
}
