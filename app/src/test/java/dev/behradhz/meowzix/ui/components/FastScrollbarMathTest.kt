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


    @Test fun fractionalThumbPositionSeeksWithinAnItem() {
        val target = fastScrollbarTargetForFraction(0.255f, 110, 10, 80f)
        assertEquals(25, target.index)
        assertEquals(40, target.offsetPx)
        val slightlyLater = fastScrollbarTargetForFraction(0.256f, 110, 10, 80f)
        assertEquals(25, slightlyLater.index)
        assertTrue(slightlyLater.offsetPx > target.offsetPx)
    }

    @Test fun thumbEndpointsReachBothEnds() {
        assertEquals(FastScrollbarTarget(0, 0),
            fastScrollbarTargetForFraction(0f, 100, 10, 50f))
        assertEquals(FastScrollbarTarget(99, 0),
            fastScrollbarTargetForFraction(1f, 100, 10, 50f))
    }

    @Test fun continuousProgressWorksForGridRowsToo() {
        val target = fastScrollbarTargetForFraction(0.505f, 50, 5, 120f)
        assertEquals(22, target.index)
        assertEquals(87, target.offsetPx)
    }

    @Test fun thumbAvoidsAndroidBackEdgeAndOtherTrackActions() {
        val geometry = fastScrollbarGeometry(600f, 100, 10, 25, 0, 60,
            12f, 40f, false)!!
        val center = 360f - 42f - 3f
        assertTrue(fastScrollbarThumbHitTest(center, geometry.thumbTopPx + 20f,
            360f, 42f, 6f, 12f, 10f, geometry))
        assertTrue(!fastScrollbarThumbHitTest(358f, geometry.thumbTopPx + 20f,
            360f, 42f, 6f, 12f, 10f, geometry))
        assertTrue(!fastScrollbarThumbHitTest(center, geometry.thumbTopPx - 30f,
            360f, 42f, 6f, 12f, 10f, geometry))
    }

    @Test fun thumbPositionYMapsToContinuousProgress() {
        val geometry = fastScrollbarGeometry(600f, 100, 10, 0, 0, 60,
            12f, 40f, false)!!
        val travel = geometry.trackHeightPx - geometry.thumbHeightPx
        val y = geometry.trackTopPx + geometry.thumbHeightPx / 2f + travel * 0.257f
        assertEquals(0.257f, fastScrollbarFractionForY(y, geometry), 0.0001f)
    }

    @Test fun bottomOfLongListAlignsThumbWithRailEnd() {
        val atEnd = fastScrollbarGeometry(600f, 2000, 8, 1992, 0, 52, 12f, 40f, true)!!
        assertEquals(atEnd.trackTopPx + atEnd.trackHeightPx,
            atEnd.thumbTopPx + atEnd.thumbHeightPx, 0.001f)
        assertEquals(40f, atEnd.thumbHeightPx, 0.001f)
    }
}
