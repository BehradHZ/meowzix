package dev.behradhz.meowzix.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveSongContentTest {
    @Test fun onlyPlayingTrackCanProduceACard() {
        assertNull(liveSongContent(false, false, "Track", "Artist"))
        assertNull(liveSongContent(true, true, "Track", "Artist"))
        assertNull(liveSongContent(true, false, null, "Artist"))
        assertNull(liveSongContent(true, false, " ", "Artist"))
    }

    @Test fun titleAndArtistAreNormalized() {
        assertEquals(LiveSongContent("Track", "Artist"), liveSongContent(true, false, " Track ", " Artist "))
        assertEquals(LiveSongContent("Track", "Unknown artist"), liveSongContent(true, false, "Track", null))
    }
}
