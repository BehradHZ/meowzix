package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapePlayerGeometryTest {
    @Test fun artworkFitsUsableLandscapeHeightAfterHeader() {
        assertEquals(202.dp, landscapeArtworkSize(640.dp, 260.dp))
    }

    @Test fun artworkLeavesSpaceForControlsInShortWideWindow() {
        val width = 520.dp
        val artwork = landscapeArtworkSize(width, 360.dp)
        assertEquals(200.dp, artwork)
        assertTrue(width - artwork - 20.dp >= 300.dp)
    }

    @Test fun largeTabletArtworkHasReasonableSizeCap() {
        assertEquals(430.dp, landscapeArtworkSize(1400.dp, 900.dp))
    }

    @Test fun narrowWindowDoesNotProduceNegativeArtworkDimensions() {
        assertEquals(0.dp, landscapeArtworkSize(290.dp, 180.dp))
    }
}
