package dev.behradhz.meowzix.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test fun progressAndTimeAreBasedOnTheActualTrackDuration() {
        val oneMinute = liveSongProgress(62_750, 200_000)
        assertEquals("1:02 / 3:20", oneMinute.label)
        assertEquals(310, oneMinute.progressValue)

        val clamped = liveSongProgress(350_000, 200_000)
        assertEquals("3:20 / 3:20", clamped.label)
        assertEquals(1_000, clamped.progressValue)

        val unknown = liveSongProgress(14_820, null)
        assertEquals("0:14 / —:—", unknown.label)
        assertEquals(0, unknown.progressValue)
        assertEquals(null, unknown.durationMs)

        assertEquals("1:02:03", formatLiveSongTime(3_723_000))
        assertEquals("0:00", formatLiveSongTime(-55))
    }

    @Test fun favoriteAndProgressArePartOfTheNotificationSnapshot() {
        val track = LiveSongContent("Track", "Artist")
        val progress = liveSongProgress(9_000, 100_000)
        val original = LiveSongCardSnapshot(track, emptyList(), false, progress)
        assertNotEquals(original, original.copy(isFavorite = true))
        assertNotEquals(original, original.copy(progress = liveSongProgress(12_000, 100_000)))
    }

    @Test fun timeIsInTheNotificationBodyWithTheArtist() {
        val progress = liveSongProgress(92_400, 220_000)
        assertEquals("Artist · 1:32 / 3:40", liveSongDetailLine("Artist", progress))
        assertEquals("Artist · 0:14 / —:—", liveSongDetailLine("Artist", liveSongProgress(14_820, null)))
    }

    @Test fun titleAndArtistAreNormalized() {
        assertEquals(LiveSongContent("Track", "Artist"), liveSongContent(true, false, " Track ", " Artist "))
        assertEquals(LiveSongContent("Track", "Unknown artist"), liveSongContent(true, false, "Track", null))
    }
}
