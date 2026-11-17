package dev.behradhz.meowzix.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollbarMathTest {
    @Test fun noIndicatorWhenContentFits() {
        assertNull(fastScrollbarGeometry(600f, 4, 4, 0, 0, 50, 12f, 40f, false))
        assertNull(fastScrollbarGeometry(0f, 100, 10, 0, 0, 50, 12f, 40f, false))
    }

    @Test fun endpointsAreReachableWithoutScrollingPastTheEnd() {
        assertEquals(0, fastScrollbarIndexForY(0f, 600f, 100, 10, 12f, 40f))
        assertEquals(90, fastScrollbarIndexForY(600f, 600f, 100, 10, 12f, 40f))
    }

    @Test fun thumbFollowsIndexAndPixelOffset() {
        val start = fastScrollbarGeometry(600f, 100, 10, 0, 0, 50, 12f, 40f, false)
        val halfway = fastScrollbarGeometry(600f, 100, 10, 45, 0, 50, 12f, 40f, false)
        val partial = fastScrollbarGeometry(600f, 100, 10, 45, 25, 50, 12f, 40f, false)
        assertNotNull(start)
        assertNotNull(halfway)
        assertNotNull(partial)
        assertTrue(halfway!!.thumbTopPx > start!!.thumbTopPx)
        assertTrue(partial!!.thumbTopPx > halfway.thumbTopPx)
    }

    @Test fun bottomOfLongListAlignsThumbWithRailEnd() {
        val atEnd = fastScrollbarGeometry(600f, 2000, 8, 1992, 0, 52, 12f, 40f, true)!!
        assertEquals(atEnd.trackTopPx + atEnd.trackHeightPx,
            atEnd.thumbTopPx + atEnd.thumbHeightPx, 0.001f)
        assertEquals(40f, atEnd.thumbHeightPx, 0.001f)
    }
}
