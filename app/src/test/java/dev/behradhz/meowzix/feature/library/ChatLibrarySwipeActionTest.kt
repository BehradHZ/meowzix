package dev.behradhz.meowzix.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatLibrarySwipeActionTest {
    private val firstStage = 100f
    private val deepStage = 176f

    private fun swipe(offset: Float, inLibrary: Boolean?, enabled: Boolean = true) =
        chatLibrarySwipeActionForOffset(
            offsetPx = offset,
            actionThresholdPx = firstStage,
            libraryThresholdPx = deepStage,
            chatLibraryMembership = inLibrary,
            libraryToggleAvailable = enabled,
        )

    @Test fun shortPullsKeepTheirOriginalActionsInBothDirections() {
        for (membership in listOf(null, false, true)) {
            assertEquals(ChatLibrarySwipeAction.NONE, swipe(99f, membership))
            assertEquals(ChatLibrarySwipeAction.NONE, swipe(-99f, membership))
            assertEquals(ChatLibrarySwipeAction.PLAY_NEXT, swipe(100f, membership))
            assertEquals(ChatLibrarySwipeAction.ADD_TO_QUEUE, swipe(-100f, membership))
            assertEquals(ChatLibrarySwipeAction.PLAY_NEXT, swipe(175f, membership))
            assertEquals(ChatLibrarySwipeAction.ADD_TO_QUEUE, swipe(-175f, membership))
        }
    }

    @Test fun deepPullAddsTrackWhenNotInChatLibrary() {
        assertEquals("Add to Library", chatLibrarySwipeLabel(false))
        assertEquals(ChatLibrarySwipeAction.ADD_TO_LIBRARY, swipe(176f, false))
        assertEquals(ChatLibrarySwipeAction.ADD_TO_LIBRARY, swipe(-240f, false))
    }

    @Test fun deepPullRemovesTrackWhenAlreadyInChatLibrary() {
        assertEquals("Remove from Library", chatLibrarySwipeLabel(true))
        assertEquals(ChatLibrarySwipeAction.REMOVE_FROM_LIBRARY, swipe(176f, true))
        assertEquals(ChatLibrarySwipeAction.REMOVE_FROM_LIBRARY, swipe(-240f, true))
    }

    @Test fun nonChatPlaylistsNeverGetASecondSwipeStage() {
        assertEquals(ChatLibrarySwipeAction.PLAY_NEXT, swipe(240f, null))
        assertEquals(ChatLibrarySwipeAction.ADD_TO_QUEUE, swipe(-240f, null))
    }

    @Test fun missingMembershipCallbackDoesNotTriggerAHiddenAction() {
        assertEquals(ChatLibrarySwipeAction.PLAY_NEXT, swipe(200f, true, false))
        assertEquals(ChatLibrarySwipeAction.ADD_TO_QUEUE, swipe(-200f, false, false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun deepThresholdMustRemainBeyondOriginalAction() {
        chatLibrarySwipeActionForOffset(200f, 100f, 100f, false, true)
    }
}
