package dev.behradhz.meowzix.feature.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsedHeroGeometryTest {

    @Test fun firstFrameReservesMeasuredTransportHeight() {
        val space = portraitPlayerSpace(
            availableHeight = 900,
            footerHeight = 200,
            queueProgress = 0f,
            compactHeroHeight = 76,
        )
        assertEquals(700, space.fullHeroHeight)
        assertEquals(700, space.heroHeight)
        assertEquals(0, space.queueHeight)
        assertEquals(900, space.heroHeight + 200 + space.queueHeight)
    }

    @Test fun expandingQueueKeepsTransportBetweenHeroAndQueue() {
        val expanded = portraitPlayerSpace(900, 200, 1f, 76)
        assertEquals(76, expanded.heroHeight)
        assertEquals(624, expanded.queueHeight)
        assertEquals(900, expanded.heroHeight + 200 + expanded.queueHeight)

        val halfway = portraitPlayerSpace(900, 200, 0.5f, 76)
        assertEquals(388, halfway.heroHeight)
        assertEquals(312, halfway.queueHeight)
    }

    @Test fun constrainedWindowNeverAllocatesNegativeHeroOrQueueSpace() {
        val cramped = portraitPlayerSpace(150, 200, 0f, 76)
        assertEquals(0, cramped.fullHeroHeight)
        assertEquals(0, cramped.heroHeight)
        assertEquals(0, cramped.queueHeight)
    }

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
