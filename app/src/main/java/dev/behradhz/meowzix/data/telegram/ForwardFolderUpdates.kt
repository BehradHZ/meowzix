package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * TDLib sends UpdateChatFolders asynchronously after authorization. Reading only
 * StateFlow.value during the first picker frame can permanently hide folders.
 */
internal suspend fun <T> awaitTelegramFolderSnapshot(
    snapshots: StateFlow<List<T>>,
    timeoutMs: Long = 5_000L,
): List<T> = snapshots.value.ifEmpty {
    withTimeoutOrNull(timeoutMs) { snapshots.first { it.isNotEmpty() } }.orEmpty()
}
