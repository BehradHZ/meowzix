package dev.behradhz.meowzix.feature.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueuePlaybackFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun standaloneQueueReturnsToNowPlayingAfterSelection() = verifyFocus(false)

    @Test fun inlineQueueReturnsToNowPlayingAfterSelection() = verifyFocus(true)

    private fun verifyFocus(inlineMode: Boolean) {
        val items = List(40) { index ->
            QueueItem(
                id = UUID.nameUUIDFromBytes("track-$index".toByteArray()),
                occurrenceId = UUID.nameUUIDFromBytes("occurrence-$index".toByteArray()),
                title = "Song $index",
                artist = "Artist",
                artworkRef = null,
            )
        }
        val queue = mutableStateOf(QueueState(items = items, currentIndex = 0))
        val played = mutableListOf<Int>()
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth().height(600.dp)) {
                    QueueScreen(
                        state = queue.value,
                        aux = QueueAuxState(),
                        searchQuery = "",
                        onPlay = { played.add(it) },
                        onMove = { _, _, _ -> },
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
                        inlineMode = inlineMode,
                    )
                }
            }
        }
        fun tag(index: Int) = "queue-item-${items[index].occurrenceId}"
        val firstTop = compose.onNodeWithTag(tag(0)).assertIsDisplayed()
            .getUnclippedBoundsInRoot().top.value
        val list = compose.onNodeWithTag("queue-list")
        // Leave the selected song above the bottom of the viewport, so that
        // merely preserving its existing scroll anchor cannot pass the test.
        list.performScrollToNode(hasTestTag(tag(14)))
        val target = compose.onNodeWithTag(tag(14)).assertIsDisplayed()
        target.performClick()
        compose.runOnIdle { assertEquals(listOf(14), played) }

        // Playback preparation is asynchronous; only the confirmed queue
        // update should bring the selected occurrence into the top position.
        compose.runOnIdle {
            queue.value = queue.value.copy(currentIndex = 14)
        }
        compose.onNodeWithText("Now Playing").assertIsDisplayed()
        assertEquals(firstTop, target.assertIsDisplayed().getUnclippedBoundsInRoot().top.value, 1f)
        compose.onNodeWithTag(tag(15)).assertIsDisplayed()

        // Automatic advancement while browsing must not force a jump to top.
        list.performScrollToNode(hasTestTag(tag(28)))
        compose.runOnIdle {
            queue.value = queue.value.copy(currentIndex = 15)
        }
        compose.onNodeWithTag(tag(28)).assertIsDisplayed()
    }
}
