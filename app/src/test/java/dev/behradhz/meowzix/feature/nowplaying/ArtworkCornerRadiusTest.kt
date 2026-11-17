package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkCornerRadiusTest {
    @Test fun compactCoverKeepsRoundedSquareShape() {
        val radius = artworkCornerRadiusForSize(58.dp, 28.dp)
        assertTrue("A compact cover must not become a circle", radius < 12.dp)
        assertEquals(9.28f, radius.value, 0.01f)
    }

    @Test fun fullSizeCoverKeepsOriginalMaximumRadius() {
        assertEquals(28.dp, artworkCornerRadiusForSize(320.dp, 28.dp))
        assertEquals(22.dp, artworkCornerRadiusForSize(320.dp, 22.dp))
    }
}
