package dev.behradhz.meowzix.feature.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsedHeroGeometryTest {
    @Test fun noLyricsCenterTheLargerCoverInsteadOfReservingAnEmptyPreview() {
        val noLyrics = collapsedHeroGeometry(360, 510, 64, 118, 66, 48, 12, 0f)
        val lyrics = collapsedHeroGeometry(360, 510, 64, 118, 66, 48, 12, 1f)
        assertTrue(noLyrics.artworkSize > lyrics.artworkSize)
        assertTrue(noLyrics.artworkTop > lyrics.artworkTop)
        assertEquals(462, noLyrics.identityTop)
        assertEquals(noLyrics.identityTop, lyrics.identityTop)
        assertEquals(0, lyrics.artworkTop)
    }

    @Test fun intermediateGeometryMovesSmoothlyBetweenEndStates() {
        val without = collapsedHeroGeometry(320, 430, 64, 118, 66, 48, 12, 0f)
        val half = collapsedHeroGeometry(320, 430, 64, 118, 66, 48, 12, 0.5f)
        val with = collapsedHeroGeometry(320, 430, 64, 118, 66, 48, 12, 1f)
        assertTrue(half.artworkSize in with.artworkSize..without.artworkSize)
        assertTrue(half.artworkTop in with.artworkTop..without.artworkTop)
        assertEquals(without.identityTop, half.identityTop)
        assertEquals(half.identityTop, with.identityTop)
    }

    @Test fun titleRemainsBottomAlignedEvenWhenCoverIsCappedByScreenWidth() {
        for (heroHeight in listOf(430, 510, 680, 900)) {
            for (lyricSpace in listOf(0f, 0.25f, 0.5f, 1f)) {
                val geometry = collapsedHeroGeometry(
                    width = 360,
                    height = heroHeight,
                    compactArt = 64,
                    previewHeight = 118,
                    identityReserve = 66,
                    identityHeight = 48,
                    margin = 12,
                    lyricSpace = lyricSpace,
                )
                assertEquals(heroHeight, geometry.identityTop + 48)
                assertTrue(geometry.artworkTop >= 0)
                assertTrue(geometry.artworkTop + geometry.artworkSize <= geometry.identityTop)
            }
        }
    }

    @Test fun measuredIdentityHeightSupportsAccessibilityText() {
        val geometry = collapsedHeroGeometry(360, 540, 64, 118, 88, 88, 12, 1f)
        assertEquals(452, geometry.identityTop)
        assertTrue(geometry.artworkTop + geometry.artworkSize + 118 <= geometry.identityTop)
    }
}
