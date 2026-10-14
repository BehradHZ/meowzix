package dev.behradhz.meowzix.data.telegram

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramSourceHistoryStoreTest {
    @Test fun selectionHistorySurvivesStoreRecreationAndIsAccountScoped() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accountA = "test-${UUID.randomUUID()}"
        val accountB = "test-${UUID.randomUUID()}"
        val store = TelegramSourceHistoryStore(context)

        assertTrue(store.previouslySelected(accountA).isEmpty())
        store.recordSelection(accountA, 101L)
        store.recordSelection(accountA, 101L)
        store.recordSelection(accountA, 102L)

        val reopened = TelegramSourceHistoryStore(context)
        assertEquals(setOf(101L, 102L), reopened.previouslySelected(accountA))
        assertTrue(reopened.previouslySelected(accountB).isEmpty())
    }
}
