package dev.behradhz.meowzix.feature.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchMatcherTest {
    @Test fun findsPlaylistNameAndDescription() {
        assertTrue(matchesGlobalSearch("night drive", "Night Drive", "Road-trip playlist"))
        assertTrue(matchesGlobalSearch("road trip", "Night Drive", "Road trip playlist"))
    }

    @Test fun findsArtistsAndAlbumsEvenWithoutMatchingTrackTitle() {
        assertTrue(matchesGlobalSearch("radiohead", "Radiohead"))
        assertTrue(matchesGlobalSearch("abbey beatles", "Abbey Road", "The Beatles"))
        assertFalse(matchesGlobalSearch("coldplay", "Radiohead"))
    }

    @Test fun blankAndNonmatchingQueriesDoNotProduceResults() {
        assertFalse(matchesGlobalSearch("", "Library"))
        assertFalse(matchesGlobalSearch("  ", "Playlist"))
        assertFalse(matchesGlobalSearch("unknown song", "Night Drive", "Road trip"))
    }
}
