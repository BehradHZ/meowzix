package dev.behradhz.meowzix.feature.nowplaying

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineQueuePullSettleTest {
    private val dragRangePx = 260f

    @Test fun shortSlowPullKeepsQueueExpanded() {
        // A 40 px pull is not enough to dismiss a fully expanded 260 px sheet.
        assertFalse(shouldCollapseInlineQueue(220f / dragRangePx, 0f, dragRangePx))
    }

    @Test fun deliberateDownwardPullCollapsesQueue() {
        // An 85 px pull passes the dismiss threshold without requiring a fling.
        assertTrue(shouldCollapseInlineQueue(175f / dragRangePx, 0f, dragRangePx))
    }

    @Test fun quickDownwardFlickCollapsesQueue() {
        // A fast downward release dismisses even after a short drag.
        assertTrue(shouldCollapseInlineQueue(0.9f, 700f, dragRangePx))
    }

    @Test fun upwardReleaseDoesNotAccidentallyDismissQueue() {
        assertFalse(shouldCollapseInlineQueue(0.9f, -700f, dragRangePx))
    }
}
