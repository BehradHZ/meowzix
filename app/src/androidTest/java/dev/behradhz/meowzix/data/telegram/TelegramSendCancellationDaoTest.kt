package dev.behradhz.meowzix.data.telegram

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TelegramSendJobEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramSendCancellationDaoTest {
    private lateinit var db: MeowzixDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), MeowzixDatabase::class.java,
        ).build()
    }

    @After fun close() { db.close() }

    @Test fun dismissingUncertainOrPreviouslyCanceledSendsNeverLeavesCardsOrDedupeLocks() = runTest {
        val dao = db.telegramSendDao()
        val uncertain = job("VERIFYING")
        val canceled = job("CANCELED")
        dao.insert(uncertain)
        dao.insert(canceled)
        assertEquals(listOf(uncertain.id), dao.observeRecent().first().map { it.id })

        assertEquals(1, dao.cancel(uncertain.id, 50L))
        assertEquals(emptyList<String>(), dao.observeRecent().first().map { it.id })
        val updated = dao.jobById(uncertain.id)!!
        assertEquals("CANCELED", updated.state)
        assertNull(updated.activeDedupeKey)
    }

    @Test fun activeUploadCancellationCannotBeOverwrittenByProgressOrSuccess() = runTest {
        val dao = db.telegramSendDao()
        val upload = job("UPLOADING")
        dao.insert(upload)
        assertEquals(1, dao.requestCancelActive(upload.id, 20L))
        dao.updateProgress(upload.id, 95, 25L)
        dao.updateState(upload.id, "SENDING", 95, 1, null, 30L)
        assertEquals("CANCELING", dao.jobById(upload.id)!!.state)
        assertEquals(0, dao.jobById(upload.id)!!.progressPercent)

        assertEquals(1, dao.cancel(upload.id, 40L))
        dao.markSent(upload.id, 333L, 45L)
        assertEquals("CANCELED", dao.jobById(upload.id)!!.state)
        assertEquals(emptyList<String>(), dao.observeRecent().first().map { it.id })
    }

    @Test fun unconfirmedCancellationRemainsReviewableAndCanBeDismissed() = runTest {
        val dao = db.telegramSendDao()
        val job = job("SENDING")
        dao.insert(job)
        assertEquals(1, dao.requestCancelActive(job.id, 20L))
        assertEquals(1, dao.markCancelUncertain(job.id, "Check Telegram", 21L))
        assertEquals("VERIFYING", dao.jobById(job.id)!!.state)
        assertEquals("Check Telegram", dao.jobById(job.id)!!.errorMessage)
        assertEquals(1, dao.cancel(job.id, 30L))
        assertEquals(emptyList<String>(), dao.observeRecent().first().map { it.id })
    }

    private fun job(state: String) = TelegramSendJobEntity(
        id = UUID.randomUUID().toString(),
        accountId = "42",
        trackId = UUID.randomUUID().toString(),
        targetChatId = 99L,
        targetTitle = "Test destination",
        state = state,
        progressPercent = 0,
        attemptCount = 1,
        includeSourceAttribution = true,
        keepCaption = true,
        activeDedupeKey = if (state == "CANCELED") null else UUID.randomUUID().toString(),
        sentMessageId = null,
        errorMessage = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}
