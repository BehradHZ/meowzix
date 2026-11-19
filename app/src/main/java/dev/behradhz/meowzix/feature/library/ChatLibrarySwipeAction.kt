package dev.behradhz.meowzix.feature.library

import kotlin.math.abs

/** The second swipe stage applies only to tracks in Telegram chat playlists. */
internal enum class ChatLibrarySwipeAction {
    NONE,
    PLAY_NEXT,
    ADD_TO_QUEUE,
    ADD_TO_LIBRARY,
    REMOVE_FROM_LIBRARY,
}

internal fun chatLibrarySwipeActionForOffset(
    offsetPx: Float,
    actionThresholdPx: Float,
    libraryThresholdPx: Float,
    chatLibraryMembership: Boolean?,
    libraryToggleAvailable: Boolean,
): ChatLibrarySwipeAction {
    require(actionThresholdPx > 0f && libraryThresholdPx > actionThresholdPx)
    return when {
        chatLibraryMembership != null && libraryToggleAvailable &&
            abs(offsetPx) >= libraryThresholdPx ->
            if (chatLibraryMembership) ChatLibrarySwipeAction.REMOVE_FROM_LIBRARY
            else ChatLibrarySwipeAction.ADD_TO_LIBRARY
        offsetPx >= actionThresholdPx -> ChatLibrarySwipeAction.PLAY_NEXT
        offsetPx <= -actionThresholdPx -> ChatLibrarySwipeAction.ADD_TO_QUEUE
        else -> ChatLibrarySwipeAction.NONE
    }
}

internal fun chatLibrarySwipeLabel(inLibrary: Boolean): String =
    if (inLibrary) "Remove from Library" else "Add to Library"
