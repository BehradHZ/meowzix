package dev.behradhz.meowzix.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWidgetQueueRangeTest {
    @Test fun emptyAndInvalidWindowsStayEmpty() {
        assertTrue(widgetQueueRange(0, -1).isEmpty())
        assertTrue(widgetQueueRange(5, 1, 0).isEmpty())
    }

    @Test fun exposesCurrentAndUpcomingWithoutOverAllocating() {
        assertEquals((0..9).toList(), widgetQueueRange(10, 0).toList())
        assertEquals((17..20).toList(), widgetQueueRange(21, 20).toList())
        assertEquals((47..118).toList(), widgetQueueRange(1000, 50).toList())
    }

    @Test fun windowIsBoundedAndKeepsIndicesStable() {
        val range = widgetQueueRange(10000, 5000)
        assertEquals(72, range.count())
        assertEquals(4997, range.first)
        assertEquals(5068, range.last)
    }
}
