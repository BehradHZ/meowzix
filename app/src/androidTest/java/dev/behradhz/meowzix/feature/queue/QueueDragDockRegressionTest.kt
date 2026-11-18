package dev.behradhz.meowzix.feature.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.navigation.FloatingControlsTouchBounds
import dev.behradhz.meowzix.navigation.LocalFloatingControlsTouchBounds
import dev.behradhz.meowzix.navigation.LocalQueueReorderGestureState
import dev.behradhz.meowzix.navigation.QueueReorderGestureState
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueDragDockRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dragAcrossDockSafeZoneKeepsReorderAliveWithIndependentScroll() {
        val items = List(20) { index ->
            QueueItem(
                id = UUID.nameUUIDFromBytes("track-$index".toByteArray()),
                occurrenceId = UUID.nameUUIDFromBytes("occurrence-$index".toByteArray()),
                title = "Song $index",
                artist = "Artist",
                artworkRef = null,
                origin = if (index in 1..5) QueueItemOrigin.MANUAL else QueueItemOrigin.GENERATED,
            )
        }
        val controls = FloatingControlsTouchBounds().apply {
            dockTopY = Float.POSITIVE_INFINITY
            miniPlayerTopY = Float.POSITIVE_INFINITY
        }
        val moves = mutableListOf<Triple<Int, Int, QueueItemOrigin>>()
        val reorderSession = QueueReorderGestureState()

        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                CompositionLocalProvider(
                    LocalFloatingControlsTouchBounds provides controls,
                    LocalQueueReorderGestureState provides reorderSession,
                ) {
                    Box(Modifier.fillMaxWidth().height(600.dp)) {
                        QueueScreen(
                            state = QueueState(items = items, currentIndex = 0),
                            aux = QueueAuxState(),
                            searchQuery = "",
                            onPlay = {},
                            onMove = { from, to, origin -> moves.add(Triple(from, to, origin)) },
                            onRemove = {},
                            onMoveGroup = { _, _ -> },
                            onRemoveGroup = {},
                            onClear = {},
                            onToggleShuffle = {},
                            onPlayNext = {},
                            onAddToQueue = {},
                            onPinOffline = {},
                            onRemoveTrack = { _, _, _ -> },
                            onFavorite = {},
                            onAddToPlaylist = { _, _ -> },
                            onGoToArtist = {},
                        )
                    }
                }
            }
        }

        val dragHandle = compose.onAllNodesWithContentDescription("Drag to reorder").onFirst()
        // The gesture is intentionally split: Compose batches events within a
        // performTouchInput block. Safe-zone changes must occur only after the
        // initial drag has actually been recognized.
        dragHandle.performTouchInput {
            down(0, center)
            moveBy(0, Offset(0f, 90f))
            advanceEventTime(80L)
        }
        compose.runOnUiThread {
            assertTrue("The first phase must start a real Queue drag", reorderSession.active)
            controls.dockTopY = 0f
        }
        dragHandle.performTouchInput {
            moveBy(0, Offset(0f, 65f))
            // A separate pointer scrolls the Queue while the first remains held.
            down(1, center + Offset(70f, 130f))
            moveBy(1, Offset(0f, -240f))
            advanceEventTime(100L)
            up(1)
            moveBy(0, Offset(0f, 25f))
            up(0)
        }

        compose.waitForIdle()
        compose.onNodeWithTag("queue-drag-preview").assertDoesNotExist()
        assertEquals("Reorder should commit exactly once", 1, moves.size)
        assertEquals(1, moves.single().first)
        assertTrue("The held song should move to a later slot", moves.single().second > 1)
    }
}
