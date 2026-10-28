package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardFolderUpdatesTest {
    @Test
    fun cachedFoldersAreReturnedImmediately() = runBlocking {
        val updates = MutableStateFlow(listOf("Friends", "Music"))
        assertEquals(listOf("Friends", "Music"), awaitTelegramFolderSnapshot(updates, 200L))
    }

    @Test
    fun foldersPublishedAfterPickerOpensAreNotLost() = runBlocking {
        val updates = MutableStateFlow(emptyList<String>())
        val updateJob = launch {
            yield()
            updates.value = listOf("Friends")
        }
        assertEquals(listOf("Friends"), awaitTelegramFolderSnapshot(updates, 500L))
        updateJob.join()
    }

    @Test
    fun genuinelyEmptySnapshotTimesOutWithoutBlockingForever() = runBlocking {
        val updates = MutableStateFlow(emptyList<String>())
        assertEquals(emptyList<String>(), awaitTelegramFolderSnapshot(updates, 20L))
    }
}
