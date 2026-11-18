package dev.behradhz.meowzix.feature.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDragPreviewOffsetTest {
    @Test fun finitePreviewCoordinateIsCenteredAbovePointer() {
        assertEquals(62, queueDragPreviewTopOffset(100f, 38f))
        assertEquals(-20, queueDragPreviewTopOffset(18f, 38f))
    }

    @Test fun clearedDragCoordinatesNeverCrashLayout() {
        assertEquals(0, queueDragPreviewTopOffset(Float.NaN, 38f))
        assertEquals(0, queueDragPreviewTopOffset(Float.POSITIVE_INFINITY, 38f))
        assertEquals(0, queueDragPreviewTopOffset(Float.NEGATIVE_INFINITY, 38f))
        assertEquals(0, queueDragPreviewTopOffset(100f, Float.NaN))
        assertEquals(0, queueDragPreviewTopOffset(100f, Float.POSITIVE_INFINITY))
    }
}
