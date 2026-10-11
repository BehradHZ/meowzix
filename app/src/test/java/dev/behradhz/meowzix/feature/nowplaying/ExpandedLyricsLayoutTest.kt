package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedLyricsLayoutTest {
    @Test fun preLyricsCompactPreviewStartsAtTheTopInsteadOfTheBottom() {
        assertEquals(listOf(0, 1, 2), compactPreviewLineIndices(-1))
        assertEquals(listOf(0, 1, 2), compactPreviewLineIndices(-5))
        assertEquals(listOf(-1, 0, 1), compactPreviewLineIndices(0))
        assertEquals(listOf(2, 3, 4), compactPreviewLineIndices(3))
    }

    @Test fun synchronizedLineIsTheFourthVisibleRowWheneverThreePreviousLinesExist() {
        assertEquals(0, firstVisibleLyricsIndexForSync(-1))
        assertEquals(0, firstVisibleLyricsIndexForSync(0))
        assertEquals(0, firstVisibleLyricsIndexForSync(1))
        assertEquals(0, firstVisibleLyricsIndexForSync(2))
        assertEquals(0, firstVisibleLyricsIndexForSync(3))
        assertEquals(1, firstVisibleLyricsIndexForSync(4))
        assertEquals(7, firstVisibleLyricsIndexForSync(10))
    }

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

    @Test fun playbackLineRemainsEmphasizedWhileManualBrowsingRevealsTheOthers() {
        // The synced line never changes its opacity/blur because a different line is centered.
        assertEquals(1f, lyricLineOpacity(3, 3, 0f))
        assertEquals(1f, lyricLineOpacity(3, 3, 0.5f))
        assertEquals(1f, lyricLineOpacity(3, 3, 1f))
        assertEquals(0f, lyricLineBlur(3, 3, 1f))

        assertEquals(0.38f, lyricLineOpacity(1, 3, 0f))
        assertEquals(0.69f, lyricLineOpacity(1, 3, 0.5f), 0.0001f)
        assertEquals(1f, lyricLineOpacity(1, 3, 1f))
        assertEquals(1.2f, lyricLineBlur(1, 3, 0f))
        assertEquals(0.6f, lyricLineBlur(1, 3, 0.5f), 0.0001f)
        assertEquals(0f, lyricLineBlur(1, 3, 1f))
    }

    @Test fun buttonSkipNeverChangesBackdropBeforeTheArtworkTransition() {
        val oldCover = "content://cover/old"
        val newCover = "content://cover/new"
        // Playback may report the new track a frame before the artwork pager starts its slide.
        assertEquals(oldCover, backdropSourceArtwork(oldCover, null))
        val transition = ArtworkBackdropTransition(oldCover, newCover, 0f)
        assertEquals(oldCover, backdropSourceArtwork(oldCover, transition))
        assertEquals(oldCover, backdropSourceArtwork(oldCover, transition.copy(progress = 0.5f)))
        assertEquals(newCover, backdropSourceArtwork(newCover, null))
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
