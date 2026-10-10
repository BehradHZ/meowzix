package dev.behradhz.meowzix.feature.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsedHeroGeometryTest {
    @Test fun noLyricsCenterTheLargerCoverInsteadOfReservingAnEmptyPreview() {
        val noLyrics = collapsedHeroGeometry(360, 510, 64, 118, 66, 12, 0f)
        val lyrics = collapsedHeroGeometry(360, 510, 64, 118, 66, 12, 1f)
        assertTrue(noLyrics.artworkSize > lyrics.artworkSize)
        assertTrue(noLyrics.artworkTop > lyrics.artworkTop)
        assertTrue(noLyrics.identityTop < lyrics.identityTop)
        assertEquals(0, lyrics.artworkTop)
    }

    @Test fun intermediateGeometryMovesSmoothlyBetweenEndStates() {
        val without = collapsedHeroGeometry(320, 430, 64, 118, 66, 12, 0f)
        val half = collapsedHeroGeometry(320, 430, 64, 118, 66, 12, 0.5f)
        val with = collapsedHeroGeometry(320, 430, 64, 118, 66, 12, 1f)
        assertTrue(half.artworkSize in with.artworkSize..without.artworkSize)
        assertTrue(half.artworkTop in with.artworkTop..without.artworkTop)
        assertTrue(half.identityTop in without.identityTop..with.identityTop)
    }
}
