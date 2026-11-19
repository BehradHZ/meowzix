package dev.behradhz.meowzix.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollbarMathTest {
    private fun geometry(
        offset: Float = 0f,
        content: Float = 1000f,
        viewport: Float = 600f,
    ) = fastScrollbarGeometry(
        viewportPx = 600f,
        metrics = FastScrollMetrics(offset, content, viewport),
        insetPx = 12f,
        minThumbHeightPx = 44f,
        maxThumbHeightPx = 96f,
    )!!

    @Test fun hideScrollbarWhenContentFitsOrViewportIsInvalid() {
        assertNull(fastScrollbarGeometry(600f, FastScrollMetrics(0f, 600f, 600f),
            12f, 44f, 96f))
        assertNull(fastScrollbarGeometry(0f, FastScrollMetrics(0f, 1000f, 600f),
            12f, 44f, 96f))
    }

    @Test fun screenEdgesMapToExactEndpoints() {
        val g = geometry()
        assertEquals(0f, fastScrollbarFractionForY(g.thumbCenterPx, g), 0.0001f)
        assertEquals(1f, fastScrollbarFractionForY(600f, g), 0.0001f)
        assertEquals(0f, fastScrollbarFractionForY(-40f, g), 0.0001f)
        val end = geometry(offset = 400f)
        assertEquals(end.trackTopPx + end.trackHeightPx,
            end.thumbTopPx + end.thumbHeightPx, 0.001f)
    }

    @Test fun shortListsMoveContinuouslyAtSubItemResolution() {
        val first = geometry(offset = 15f, content = 780f)
        val next = geometry(offset = 16f, content = 780f)
        assertTrue(next.thumbTopPx > first.thumbTopPx)
        assertTrue(next.thumbTopPx - first.thumbTopPx < 5f)
        assertEquals(0.5f, fastScrollbarContentDelta(0.5f, 0.5027778f, 180f), 0.001f)
        assertEquals(-0.5f, fastScrollbarContentDelta(0.5027778f, 0.5f, 180f), 0.001f)
    }

    @Test fun draggingCanStopBetweenRowsRatherThanSnappingToIndexes() {
        val g = geometry(offset = 0f, content = 780f)
        val onePixelFraction = fastScrollbarFractionForY(g.thumbCenterPx + 1f, g)
        val twoPixelsFraction = fastScrollbarFractionForY(g.thumbCenterPx + 2f, g)
        val firstDelta = fastScrollbarContentDelta(0f, onePixelFraction, 180f)
        val secondDelta = fastScrollbarContentDelta(onePixelFraction, twoPixelsFraction, 180f)
        assertTrue(firstDelta > 0f)
        assertTrue(secondDelta > 0f)
        assertTrue(firstDelta < 2f)
        assertTrue(secondDelta < 2f)
    }

    @Test fun touchIsAllowedOnlyOnTheRightEdgeThumb() {
        val g = geometry()
        assertTrue(fastScrollbarThumbHitTest(352f, g.thumbCenterPx,
            360f, 4f, 6f, 12f, 8f, g))
        assertFalse(fastScrollbarThumbHitTest(310f, g.thumbCenterPx,
            360f, 4f, 6f, 12f, 8f, g))
        assertFalse(fastScrollbarThumbHitTest(352f, 590f,
            360f, 4f, 6f, 12f, 8f, g))
    }

    @Test fun longAndShortListsUseBoundedGrabHandles() {
        assertEquals(44f, geometry(content = 300_000f).thumbHeightPx, 0.001f)
        assertEquals(96f, geometry(content = 780f).thumbHeightPx, 0.001f)
    }
}
