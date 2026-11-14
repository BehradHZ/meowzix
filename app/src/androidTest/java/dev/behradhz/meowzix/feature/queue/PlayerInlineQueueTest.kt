package dev.behradhz.meowzix.feature.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
class PlayerInlineQueueTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlineQueueSharesOccurrenceRowsReorderHandlesAndHidesOverflow() {
        val items = List(3) { index ->
            QueueItem(
                id = UUID.nameUUIDFromBytes("track-$index".toByteArray()),
                occurrenceId = UUID.nameUUIDFromBytes("occurrence-$index".toByteArray()),
                title = "Song $index",
                artist = "Artist $index",
                artworkRef = null,
            )
        }
        val played = mutableStateListOf<Int>()
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                MaterialTheme {
                    Box(Modifier.fillMaxWidth().height(420.dp)) {
                        QueueScreen(
                            state = QueueState(items = items, currentIndex = 0),
                            aux = QueueAuxState(),
                            searchQuery = "",
                            onPlay = { played.add(it) },
                            onMove = { _, _ -> },
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
                            inlineMode = true,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("queue-item-${items[1].occurrenceId}").assertExists()
        compose.onNodeWithContentDescription("Track actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Group actions").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Drag to reorder").assertCountEquals(2)
        compose.onNodeWithTag("queue-item-${items[1].occurrenceId}").performClick()
        compose.runOnIdle { assertEquals(listOf(1), played.toList()) }
    }
}
