package dev.behradhz.meowzix.feature.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSwipeActionTest {
    private val actionThreshold = 75f
    private val removeThreshold = 190f

    private fun released(offset: Float) =
        queueSwipeActionForOffset(offset, actionThreshold, removeThreshold)

    @Test fun firstStageKeepsBothQueueActionsDistinct() {
        assertEquals(QueueSwipeAction.NONE, released(0f))
        assertEquals(QueueSwipeAction.NONE, released(-74.9f))
        assertEquals(QueueSwipeAction.PLAY_NEXT, released(75f))
        assertEquals(QueueSwipeAction.ADD_TO_QUEUE, released(-75f))
        assertEquals(QueueSwipeAction.PLAY_NEXT, released(189f))
        assertEquals(QueueSwipeAction.ADD_TO_QUEUE, released(-189f))
    }

    @Test fun deeperPullRemovesOnEitherSideOnlyAfterFullVisualTransition() {
        assertEquals(QueueSwipeAction.REMOVE, released(190f))
        assertEquals(QueueSwipeAction.REMOVE, released(-190f))
        assertEquals(QueueSwipeAction.REMOVE, released(300f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun destructiveThresholdMustBeGreaterThanQueueActionThreshold() {
        queueSwipeActionForOffset(80f, 75f, 75f)
    }
}
