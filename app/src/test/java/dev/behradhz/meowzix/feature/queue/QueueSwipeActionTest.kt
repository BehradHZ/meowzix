package dev.behradhz.meowzix.feature.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSwipeActionTest {
    private val actionThreshold = 100f
    private val removeThreshold = 176f

    private fun released(offset: Float) =
        queueSwipeActionForOffset(offset, actionThreshold, removeThreshold)

    @Test fun firstStageKeepsBothQueueActionsDistinct() {
        assertEquals(QueueSwipeAction.NONE, released(0f))
        assertEquals(QueueSwipeAction.NONE, released(-99.9f))
        assertEquals(QueueSwipeAction.PLAY_NEXT, released(100f))
        assertEquals(QueueSwipeAction.ADD_TO_QUEUE, released(-100f))
        assertEquals(QueueSwipeAction.PLAY_NEXT, released(175f))
        assertEquals(QueueSwipeAction.ADD_TO_QUEUE, released(-175f))
    }

    @Test fun deeperPullRemovesOnEitherSideOnlyAfterFullVisualTransition() {
        assertEquals(QueueSwipeAction.REMOVE, released(176f))
        assertEquals(QueueSwipeAction.REMOVE, released(-176f))
        assertEquals(QueueSwipeAction.REMOVE, released(300f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun destructiveThresholdMustBeGreaterThanQueueActionThreshold() {
        queueSwipeActionForOffset(110f, 100f, 100f)
    }
}
