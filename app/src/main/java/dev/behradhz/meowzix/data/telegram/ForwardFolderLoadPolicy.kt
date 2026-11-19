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

/** Full folder indexes are reusable for one week, across process restarts. */
internal const val FORWARD_FOLDER_INDEX_TTL_MS = 7L * 24 * 60 * 60 * 1000

internal fun isForwardFolderIndexFresh(savedAtEpochMs: Long, nowEpochMs: Long): Boolean =
    savedAtEpochMs > 0L &&
        nowEpochMs >= savedAtEpochMs &&
        nowEpochMs - savedAtEpochMs < FORWARD_FOLDER_INDEX_TTL_MS

/** Do not cache an empty, failed or incomplete folder as a final snapshot. */
internal fun shouldCacheForwardFolderChats(
    knownChatCount: Int,
    resolvedChatCount: Int,
    reachedEnd: Boolean,
    definitionLoaded: Boolean,
): Boolean = resolvedChatCount > 0 && knownChatCount == resolvedChatCount &&
    reachedEnd && definitionLoaded
