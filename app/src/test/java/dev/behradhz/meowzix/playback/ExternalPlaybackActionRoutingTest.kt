package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.widget.VinylZone
import dev.behradhz.meowzix.widget.WidgetAction
import dev.behradhz.meowzix.widget.toExternalPlaybackAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalPlaybackActionRoutingTest {
    @Test fun liveUpdateButtonsAlwaysRouteEvenWhenPlaybackIsPaused() {
        assertEquals(ExternalPlaybackAction.Pause, LiveSongAction.PAUSE.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Previous, LiveSongAction.PREVIOUS.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Next, LiveSongAction.NEXT.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Favorite, LiveSongAction.FAVORITE.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Shuffle, LiveSongAction.SHUFFLE.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Repeat, LiveSongAction.REPEAT.toExternalPlaybackAction())
    }

    @Test fun invisibleVinylHotspotsMapToTheirOwnControls() {
        assertEquals(ExternalPlaybackAction.TogglePlayPause, VinylZone.CENTER.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Previous, VinylZone.LEFT.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Next, VinylZone.RIGHT.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Favorite, VinylZone.TOP.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Forward, VinylZone.BOTTOM.toExternalPlaybackAction())
    }

    @Test fun rectangularWidgetHasTransportAndOverflowRoutesButLocalMenuOnly() {
        assertEquals(ExternalPlaybackAction.TogglePlayPause, WidgetAction.PLAY_PAUSE.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Previous, WidgetAction.PREVIOUS.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Next, WidgetAction.NEXT.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Favorite, WidgetAction.FAVORITE.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Forward, WidgetAction.FORWARD.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Shuffle, WidgetAction.SHUFFLE.toExternalPlaybackAction())
        assertEquals(ExternalPlaybackAction.Repeat, WidgetAction.REPEAT.toExternalPlaybackAction())
        assertNull(WidgetAction.MENU.toExternalPlaybackAction())
    }

    @Test fun queueSelectionKeepsTheExpectedMediaIdentity() {
        assertEquals(
            ExternalPlaybackAction.QueueSelection(42, "track-42"),
            ExternalPlaybackAction.QueueSelection(42, "track-42"),
        )
    }
}
