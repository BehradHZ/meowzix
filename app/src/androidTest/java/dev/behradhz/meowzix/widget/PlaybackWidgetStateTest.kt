package dev.behradhz.meowzix.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackWidgetStateTest {
    @Test
    fun emptyStateHasDeterministicFallback() {
        val model = PlaybackWidgetState.Empty.toRenderModel()
        assertEquals("Nothing playing", model.title)
        assertEquals("Open Meowzix to choose a track", model.artist)
        assertEquals(R.drawable.widget_play, model.playPauseIcon)
        assertEquals("Play", model.playPauseDescription)
        assertEquals("Meowzix", model.artworkDescription)
    }

    @Test
    fun playingTrackRendersRealMetadataAndPauseAction() {
        val model = PlaybackWidgetState(
            title = "Numb",
            artist = "Linkin Park",
            artworkRef = "content://artwork/1",
            isPlaying = true,
        ).toRenderModel()

        assertEquals("Numb", model.title)
        assertEquals("Linkin Park", model.artist)
        assertEquals(R.drawable.widget_pause, model.playPauseIcon)
        assertEquals("Pause", model.playPauseDescription)
        assertEquals("Numb cover art", model.artworkDescription)
    }

    @Test
    fun missingArtistIsExplainedInsteadOfInvented() {
        val model = PlaybackWidgetState(
            title = "Untitled recording",
            artist = null,
            artworkRef = null,
            isPlaying = false,
        ).toRenderModel()
        assertEquals("Unknown artist", model.artist)
    }
}
