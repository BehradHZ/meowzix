package dev.behradhz.meowzix.feature.nowplaying

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingPanelStateTest {
    @Test fun lyricsQueueLyricsThenCollapseLyricsWithSuccessiveDownwardActions() {
        val panels = NowPlayingPanelState()
        panels.expandLyrics()
        assertTrue(panels.lyricsExpanded)

        panels.setQueueExpanded(true)
        assertTrue(panels.queueExpanded)
        assertFalse(panels.lyricsExpanded)

        panels.setQueueExpanded(false)
        assertFalse(panels.queueExpanded)
        assertTrue(panels.lyricsExpanded)

        assertTrue(panels.collapseForeground())
        assertFalse(panels.lyricsExpanded)
        assertFalse(panels.collapseForeground())
    }

    @Test fun queueOpenedWithoutLyricsReturnsToDefaultPlayer() {
        val panels = NowPlayingPanelState()
        panels.toggleQueue()
        assertTrue(panels.queueExpanded)
        assertTrue(panels.collapseForeground())
        assertFalse(panels.queueExpanded)
        assertFalse(panels.lyricsExpanded)
    }

    @Test fun switchingTracksDoesNotResurrectOldLyricsAfterQueue() {
        val panels = NowPlayingPanelState()
        panels.expandLyrics()
        panels.setQueueExpanded(true)
        panels.onTrackChanged()
        panels.setQueueExpanded(false)
        assertFalse(panels.lyricsExpanded)
    }

    @Test fun lyricsButtonSwitchesFromQueueToLyrics() {
        val panels = NowPlayingPanelState()
        panels.setQueueExpanded(true)
        panels.toggleLyrics()
        assertTrue(panels.lyricsExpanded)
        assertFalse(panels.queueExpanded)
        panels.setQueueExpanded(true)
        panels.setQueueExpanded(false)
        assertTrue(panels.lyricsExpanded)
    }
}
