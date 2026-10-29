package dev.behradhz.meowzix.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import dev.behradhz.meowzix.domain.settings.LiveSongCardStyle
import dev.behradhz.meowzix.domain.settings.LiveSongSideAction
import org.junit.Test

class LiveSongContentTest {
    @Test fun onlyPlayingTrackCanProduceACard() {
        assertNull(liveSongContent(false, false, "Track", "Artist"))
        assertNull(liveSongContent(true, true, "Track", "Artist"))
        assertNull(liveSongContent(true, false, null, "Artist"))
        assertNull(liveSongContent(true, false, " ", "Artist"))
    }

    @Test fun statusChipUsesSongTitleInsteadOfAnIconOnly() {
        assertEquals("Numb", liveSongChipLabel(" Numb "))
        assertEquals("Wonder…", liveSongChipLabel("Wonderful Life"))
        assertEquals("Song", liveSongChipLabel("Song"))
    }

    @Test fun controlLayoutRespectsThreeActionLimitAndPlayerAvailability() {
        assertEquals(
            emptyList<LiveSongAction>(),
            liveSongControls(LiveSongCardStyle.MINIMAL, LiveSongSideAction.PREVIOUS, LiveSongSideAction.NEXT, true, true),
        )
        assertEquals(
            listOf(LiveSongAction.PREVIOUS, LiveSongAction.PAUSE, LiveSongAction.NEXT),
            liveSongControls(LiveSongCardStyle.CONTROLS, LiveSongSideAction.PREVIOUS, LiveSongSideAction.NEXT, true, true),
        )
        assertEquals(
            listOf(LiveSongAction.PAUSE),
            liveSongControls(LiveSongCardStyle.CONTROLS, LiveSongSideAction.PREVIOUS, LiveSongSideAction.NEXT, false, false),
        )
        assertEquals(
            listOf(LiveSongAction.FAVORITE, LiveSongAction.PAUSE, LiveSongAction.REPEAT),
            liveSongControls(LiveSongCardStyle.CONTROLS, LiveSongSideAction.FAVORITE, LiveSongSideAction.REPEAT, true, true),
        )
        assertEquals(
            listOf(LiveSongAction.FAVORITE, LiveSongAction.PAUSE),
            liveSongControls(LiveSongCardStyle.CONTROLS, LiveSongSideAction.FAVORITE, LiveSongSideAction.FAVORITE, true, true),
        )
    }

    @Test fun titleAndArtistAreNormalized() {
        assertEquals(LiveSongContent("Track", "Artist"), liveSongContent(true, false, " Track ", " Artist "))
        assertEquals(LiveSongContent("Track", "Unknown artist"), liveSongContent(true, false, "Track", null))
    }
}
