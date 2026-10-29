package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerMenuDragHitTest {
    private val rows = mapOf(
        0 to Rect(100f, 200f, 390f, 257f),
        1 to Rect(100f, 258f, 390f, 315f),
        2 to Rect(100f, 316f, 390f, 373f),
        3 to Rect(100f, 374f, 390f, 431f),
    )

    @Test fun draggedFingerSelectsOnlyTheRowItCrosses() {
        assertEquals(0, draggedPlayerAction(Offset(355f, 230f), rows))
        assertEquals(1, draggedPlayerAction(Offset(355f, 288f), rows))
        assertEquals(2, draggedPlayerAction(Offset(355f, 340f), rows))
        assertEquals(3, draggedPlayerAction(Offset(355f, 400f), rows))
    }

    @Test fun releaseOutsideMenuDoesNotActivateAnAction() {
        assertNull(draggedPlayerAction(Offset(355f, 180f), rows))
        assertNull(draggedPlayerAction(Offset(355f, 500f), rows))
        assertNull(draggedPlayerAction(Offset(10f, 300f), rows))
    }

    @Test fun aSmallHorizontalToleranceAllowsEdgeGestures() {
        assertEquals(1, draggedPlayerAction(Offset(405f, 280f), rows))
        assertNull(draggedPlayerAction(Offset(450f, 280f), rows))
    }
}
