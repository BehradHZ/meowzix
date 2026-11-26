package dev.behradhz.meowzix.feature.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueSwipeScrollLockTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlineQueueLocksVerticalMotionDuringHorizontalSwipe() = verifySwipeLock(true)

    @Test fun standaloneQueueLocksVerticalMotionDuringHorizontalSwipe() = verifySwipeLock(false)

    private fun verifySwipeLock(inlineMode: Boolean) {
        val items = List(24) { index ->
            QueueItem(
                id = UUID.nameUUIDFromBytes("track-$index".toByteArray()),
                occurrenceId = UUID.nameUUIDFromBytes("occurrence-$index".toByteArray()),
                title = "Song $index",
                artist = "Artist",
                artworkRef = null,
            )
        }
        val playNext = mutableListOf<UUID>()
        val removed = mutableListOf<UUID>()
        val moves = mutableListOf<Int>()
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth().height(600.dp)) {
                    QueueScreen(
                        state = QueueState(items = items, currentIndex = 0),
                        aux = QueueAuxState(),
                        searchQuery = "",
                        onPlay = {},
                        onMove = { from, _, _ -> moves.add(from) },
                        onRemove = { removed.add(it) },
                        onMoveGroup = { _, _ -> },
                        onRemoveGroup = {},
                        onClear = {},
                        onToggleShuffle = {},
                        onPlayNext = { playNext.add(it) },
                        onAddToQueue = {},
                        onPinOffline = {},
                        onRemoveTrack = { _, _, _ -> },
                        onFavorite = {},
                        onAddToPlaylist = { _, _ -> },
                        onGoToArtist = {},
                        inlineMode = inlineMode,
                    )
                }
            }
        }
        val target = items[2]
        val row = compose.onNodeWithTag("queue-item-${target.occurrenceId}")
        val startTop = row.getUnclippedBoundsInRoot().top.value
        val horizontal = with(compose.density) { 110.dp.toPx() }
        val vertical = with(compose.density) { 90.dp.toPx() }

        // Start with only a small motion beyond touch slop, then allow
        // recomposition before continuing. A one-shot 110dp motion could
        // miss a detector that gets canceled after its first event.
        val initialMotion = with(compose.density) { 24.dp.toPx() }
        row.performTouchInput {
            down(0, Offset(width * 0.25f, center.y))
            advanceEventTime(32)
            moveBy(0, Offset(initialMotion, 0f))
        }
        compose.waitForIdle()

        row.performTouchInput {
            advanceEventTime(32)
            moveBy(0, Offset((horizontal - initialMotion) / 2, 0f))
            advanceEventTime(32)
            moveBy(0, Offset(0f, -vertical))
        }
        assertEquals("Queue moved during held swipe", startTop,
            row.getUnclippedBoundsInRoot().top.value, 0.5f)
        compose.runOnIdle { assertTrue(playNext.isEmpty()) }

        row.performTouchInput {
            advanceEventTime(32)
            moveBy(0, Offset((horizontal - initialMotion) / 2, vertical / 2))
            advanceEventTime(100)
            up(0)
        }
        compose.runOnIdle {
            assertEquals(listOf(target.id), playNext)
            assertTrue(removed.isEmpty())
            assertTrue(moves.isEmpty())
        }
        assertEquals("Queue moved on swipe release", startTop,
            row.getUnclippedBoundsInRoot().top.value, 0.5f)

        // Reach the second-stage Remove threshold across multiple events.
        val removeMotion = with(compose.density) { 180.dp.toPx() }
        row.performTouchInput {
            down(0, Offset(width * 0.25f, center.y))
            advanceEventTime(32)
            moveBy(0, Offset(initialMotion, 0f))
        }
        compose.waitForIdle()
        row.performTouchInput {
            repeat(4) {
                advanceEventTime(32)
                moveBy(0, Offset((removeMotion - initialMotion) / 4, 0f))
            }
            advanceEventTime(32)
            moveBy(0, Offset(0f, -vertical))
            up(0)
        }
        compose.runOnIdle {
            assertEquals(listOf(target.occurrenceId), removed)
            assertEquals(listOf(target.id), playNext)
            assertTrue(moves.isEmpty())
        }
        assertEquals("Queue moved during Remove swipe", startTop,
            row.getUnclippedBoundsInRoot().top.value, 0.5f)

        // Cancellation must also release ownership without firing an action.
        row.performTouchInput {
            down(0, Offset(width * 0.25f, center.y))
            moveBy(0, Offset(horizontal, 0f))
            advanceEventTime(32)
            cancel()
        }
        compose.runOnIdle {
            assertEquals(listOf(target.id), playNext)
            assertEquals(listOf(target.occurrenceId), removed)
        }
        val ordinaryScroll = with(compose.density) { 38.dp.toPx() }
        row.performTouchInput {
            swipe(start = center, end = center - Offset(0f, ordinaryScroll), durationMillis = 350)
        }
        assertTrue("Ordinary vertical scrolling must work after the swipe",
            row.getUnclippedBoundsInRoot().top.value < startTop - 2f)
    }
}
