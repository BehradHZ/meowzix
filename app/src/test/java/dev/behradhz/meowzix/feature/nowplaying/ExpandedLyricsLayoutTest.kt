package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedLyricsLayoutTest {
    @Test fun morphKeepsOneFullSizeHeroAndEndsInARealCompactHeader() {
        assertEquals(520.dp, lyricsMorphHeroHeight(520.dp, 0f))
        assertEquals(298.dp, lyricsMorphHeroHeight(520.dp, 0.5f))
        assertEquals(76.dp, lyricsMorphHeroHeight(520.dp, 1f))
        assertEquals(76.dp, lyricsMorphHeroHeight(520.dp, 2f))
        assertEquals(520.dp, lyricsMorphHeroHeight(520.dp, -1f))
    }

    @Test fun compactHeaderClampsOnConstrainedScreens() {
        assertEquals(54.dp, lyricsMorphHeroHeight(54.dp, 1f))
        assertEquals(0.dp, lyricsMorphHeroHeight((-4).dp, 1f))
    }

    @Test fun lyricEmphasisFallsOffWithDistanceFromCurrentLine() {
        val opacities = (0..6).map { lyricLineOpacity(it, 3) }
        assertEquals(1f, opacities[3])
        assertEquals(opacities[2], opacities[4])
        assertEquals(opacities[1], opacities[5])
        assertTrue(opacities[3] > opacities[2])
        assertTrue(opacities[2] > opacities[1])
        assertTrue(opacities[1] > opacities[0])
        assertEquals(0.70f, lyricLineOpacity(0, -1))
        assertEquals(0f, lyricLineBlur(3, 3))
        assertEquals(0f, lyricLineBlur(2, 3))
        assertEquals(1.2f, lyricLineBlur(1, 3))
        assertEquals(2f, lyricLineBlur(0, 3))
    }
}
