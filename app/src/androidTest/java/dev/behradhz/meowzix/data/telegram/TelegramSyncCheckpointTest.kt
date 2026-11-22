package dev.behradhz.meowzix.data.telegram

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TelegramSelectedSourceEntity
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramSyncCheckpointTest {
    private lateinit var db: MeowzixDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), MeowzixDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test fun laterOldScanCannotEraseNewerMessageOrCompletedInitialImport() = runTest {
        val dao = db.telegramDao()
        dao.upsertSelectedSource(
            TelegramSelectedSourceEntity(
                accountId = "account", chatId = 42L, title = "Music",
                kind = TelegramChatKind.SUPERGROUP_OR_CHANNEL.name, includeInLibrary = false,
                newestMessageId = 100L, initialScanComplete = false, lastSyncedAtEpochMs = null,
            ),
        )
        dao.updateSyncCheckpoint("account", 42L, 150L, true, 2L)
        dao.updateSyncCheckpoint("account", 42L, 130L, false, 3L)
        dao.updateSyncCheckpoint("account", 42L, null, false, 4L)
        val persisted = dao.selectedSource("account", 42L)!!
        assertEquals(150L, persisted.newestMessageId)
        assertTrue(persisted.initialScanComplete)
        assertEquals(4L, persisted.lastSyncedAtEpochMs)
    }
}
