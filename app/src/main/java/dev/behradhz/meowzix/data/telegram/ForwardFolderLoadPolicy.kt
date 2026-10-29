package dev.behradhz.meowzix.data.telegram

/**
 * TDLib GetChats is a snapshot of currently known positions. It can return an
 * empty or partial list while LoadChats is still retrieving chat-list updates.
 */
internal fun isConfirmedEmptyForwardFolder(
    knownChatCount: Int,
    resolvedChatCount: Int,
    reachedEnd: Boolean,
    definitionLoaded: Boolean,
): Boolean = knownChatCount == 0 && resolvedChatCount == 0 &&
    reachedEnd && definitionLoaded

/** Do not cache an empty, failed or incomplete folder as a final snapshot. */
internal fun shouldCacheForwardFolderChats(
    resolvedChatCount: Int,
    reachedEnd: Boolean,
    definitionLoaded: Boolean,
): Boolean = resolvedChatCount > 0 && reachedEnd && definitionLoaded
