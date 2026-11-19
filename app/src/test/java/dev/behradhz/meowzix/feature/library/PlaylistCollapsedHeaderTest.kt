package dev.behradhz.meowzix.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCollapsedHeaderTest {
    @Test fun coverBeginningToScrollBehindHeaderDoesNotRevealTitleYet() {
        assertFalse(shouldRevealPlaylistCollapsedHeader(0, 390f, 210f))
        assertFalse(shouldRevealPlaylistCollapsedHeader(0, 210.1f, 210f))
    }

    @Test fun revealOnlyWhenTheCompleteHeroSummaryIsHidden() {
        assertTrue(shouldRevealPlaylistCollapsedHeader(0, 210f, 210f))
        assertTrue(shouldRevealPlaylistCollapsedHeader(0, 185f, 210f))
    }

    @Test fun keepStickyTitleWhileHeroIsRecycledAndHideWhenBackAtTop() {
        assertTrue(shouldRevealPlaylistCollapsedHeader(1, Float.POSITIVE_INFINITY, 210f))
        assertFalse(shouldRevealPlaylistCollapsedHeader(0, Float.POSITIVE_INFINITY, 210f))
        assertFalse(shouldRevealPlaylistCollapsedHeader(0, 100f, 0f))
    }
}
