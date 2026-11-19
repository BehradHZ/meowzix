package dev.behradhz.meowzix.data.telegram

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForwardDestinationSnapshotStoreTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private val store by lazy { ForwardDestinationSnapshotStore(context) }

    @Before fun setUp() { store.clearAll() }
    @After fun tearDown() { store.clearAll() }

    @Test fun completedIndexSurvivesRecreationAndHasSevenDayTtl() {
        val chat = TelegramChatSummary(100L, "Music", TelegramChatKind.PRIVATE, false)
        val created = System.currentTimeMillis()
        store.saveCompleteFolderChats("a", 10, listOf(chat), created)
        val restored = ForwardDestinationSnapshotStore(context)
        assertEquals(listOf(100L), restored.completeFolderChats("a", 10)?.map { it.chatId })
        assertEquals(true, restored.folderChatSnapshot("a", 10, created + FORWARD_FOLDER_INDEX_TTL_MS - 1)?.complete)
        assertNull(restored.completeFolderChats("b", 10))
    }

    @Test fun previewNeverOverridesCompletedIndex() {
        val chat = TelegramChatSummary(100L, "Music", TelegramChatKind.PRIVATE, false)
        val created = System.currentTimeMillis()
        store.saveFolderChats("a", 10, listOf(chat), created)
        assertNull(store.completeFolderChats("a", 10))
        store.saveCompleteFolderChats("a", 10, listOf(chat), created)
        store.saveFolderChats("a", 10, emptyList(), created + 1)
        assertEquals(100L, store.completeFolderChats("a", 10)?.single()?.chatId)
    }

    @Test fun expiredIndexIsEvictedAndCanBeRebuilt() {
        val chat = TelegramChatSummary(100L, "Old", TelegramChatKind.PRIVATE, false)
        store.saveCompleteFolderChats("a", 10, listOf(chat), System.currentTimeMillis() - FORWARD_FOLDER_INDEX_TTL_MS)
        assertNull(store.folderChatSnapshot("a", 10))
        assertNull(ForwardDestinationSnapshotStore(context).completeFolderChats("a", 10))
        store.saveCompleteFolderChats("a", 10, listOf(chat.copy(title = "New")))
        assertEquals("New", store.completeFolderChats("a", 10)?.single()?.title)
    }

    @Test fun completedEmptyFolderIsPersistent() {
        store.saveCompleteFolderChats("a", 10, emptyList())
        assertEquals(emptyList<TelegramChatSummary>(),
            ForwardDestinationSnapshotStore(context).completeFolderChats("a", 10))
    }

    @Test fun snapshotsSurviveRecreationAndAreAccountScoped() {
        val chat = TelegramChatSummary(100L, "Music", TelegramChatKind.PRIVATE, true)
        store.saveFolders("a", listOf(TelegramForwardFolder(10, "Favorites", emptyList())))
        store.saveRecentChats("a", listOf(chat))
        store.saveFolderChats("a", 10, listOf(chat))
        val restored = ForwardDestinationSnapshotStore(context)
        assertEquals("Favorites", restored.folders("a")?.single()?.title)
        assertEquals(100L, restored.recentChats("a")?.single()?.chatId)
        assertEquals(100L, restored.folderChats("a", 10)?.single()?.chatId)
        assertNull(restored.folders("b"))
        assertNull(restored.folderChats("b", 10))
        restored.clear("a")
        assertNull(restored.recentChats("a"))
        assertNull(restored.folderChats("a", 10))
    }
}
