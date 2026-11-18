package dev.behradhz.meowzix.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDockReselectTest {
    @Test fun firstTapExitsCurrentPlaylistAndSecondScrollsGrid() {
        assertEquals(
            PlaylistDockReselectAction.CLOSE_DETAIL,
            playlistDockReselectAction(showingDetails = true),
        )
        assertEquals(
            PlaylistDockReselectAction.SCROLL_GRID_TOP,
            playlistDockReselectAction(showingDetails = false),
        )
    }
}
