package dev.behradhz.meowzix.feature.queue

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.navigation.ContextualSelectionController
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueSelectionTest {
    @get:Rule val compose = createComposeRule()

    private val trackId = UUID.randomUUID()
    private val occurrences = List(3) { UUID.randomUUID() }
    private val controller = ContextualSelectionController()
    private val items = occurrences.map {
        QueueItem(trackId, "Repeated song", "Artist", null, occurrenceId = it)
    }

    private fun showQueue(onBulk: (LibrarySelectionAction, List<QueueItem>) -> Unit = { _, _ -> }) {
        compose.setContent {
            MeowzixTheme {
                QueueScreen(
                    state = QueueState(items = items, currentIndex = 0),
                    aux = QueueAuxState(),
                    searchQuery = "",
                    onPlay = {},
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
                    selectionController = controller,
                    onBulkAction = onBulk,
                )
            }
        }
    }

    @Test fun selectUsesQueueOccurrenceIdentityForRepeatedTracks() {
        var selected = emptyList<UUID>()
        showQueue { action, rows ->
            assertEquals(LibrarySelectionAction.DELETE, action)
            selected = rows.map(QueueItem::occurrenceId)
        }
        compose.onNodeWithTag("queue-item-${occurrences[1]}").performTouchInput { longClick() }
        compose.onNodeWithTag("queue-item-${occurrences[2]}").performClick()
        compose.onNodeWithText("Selected 2").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(controller.active)
            controller.onAction(LibrarySelectionAction.DELETE)
            assertEquals(occurrences.drop(1), selected)
        }
        compose.runOnIdle { assertFalse(controller.active) }
    }

    @Test fun selectAllTogglesVisibleOccurrencesAndRestoresHeader() {
        showQueue()
        compose.onNodeWithContentDescription("Select tracks").performClick()
        compose.onNodeWithContentDescription("Select all").performClick()
        compose.onNodeWithText("Selected 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Select all").performClick()
        compose.onNodeWithContentDescription("Select tracks").assertIsDisplayed()
        compose.runOnIdle { assertFalse(controller.active) }
    }
}
