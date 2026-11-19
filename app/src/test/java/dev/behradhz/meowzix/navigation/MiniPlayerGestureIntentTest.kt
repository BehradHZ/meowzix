package dev.behradhz.meowzix.navigation

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerGestureIntentTest {
    @Test
    fun verticalExpansionGestureCannotTriggerTrackChange() {
        assertEquals(PlayerDragAxis.VERTICAL, playerDragAxis(Offset(75f, -250f), 8f))
        assertFalse(shouldCommitMiniTrackSwipe(Offset(160f, -350f), 400f))
        assertFalse(shouldCommitMiniTrackSwipe(Offset(-160f, 350f), 400f))
    }

    @Test
    fun horizontalSwipeStillChangesTracksWhenThresholdIsReached() {
        assertEquals(PlayerDragAxis.HORIZONTAL, playerDragAxis(Offset(-170f, 24f), 8f))
        assertTrue(shouldCommitMiniTrackSwipe(Offset(-170f, 24f), 400f))
        assertTrue(shouldCommitMiniTrackSwipe(Offset(170f, -24f), 400f))
        assertFalse(shouldCommitMiniTrackSwipe(Offset(100f, 0f), 400f))
    }

    @Test
    fun ambiguousOrShortGesturesRemainUnclaimed() {
        assertNull(playerDragAxis(Offset(5f, 5f), 8f))
        assertNull(playerDragAxis(Offset(50f, 48f), 8f))
        assertFalse(shouldCommitMiniTrackSwipe(Offset(0f, 0f), 400f))
        assertFalse(shouldCommitMiniTrackSwipe(Offset(160f, 160f), 400f))
    }
}
