package dev.behradhz.meowzix.data.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.db.DownloadRecordEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDisplayOrderTest {
    private lateinit var db: MeowzixDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), MeowzixDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test fun progressAndParallelUpdatesNeverChangeDownloadPositions() = runTest {
        val ids = listOf("a", "b", "c")
        ids.forEachIndexed { index, id ->
            db.libraryDao().upsertTrack(TrackEntity(
                id = id, title = id, normalizedTitle = id, artist = null, normalizedArtist = null,
                album = null, durationMs = 120_000, trackNumber = null, year = null,
                artworkRef = null, favorite = false, hidden = false,
                createdAtEpochMs = index.toLong(), updatedAtEpochMs = 0,
            ))
            db.downloadDao().upsert(DownloadRecordEntity(
                id = "record-$id", trackId = id, trackSourceId = "source-$id",
                tdFileId = null, status = "DOWNLOADING", downloadedBytes = 0,
                totalBytes = 1000, localPath = null, pinned = true, failureReason = null,
                createdAtEpochMs = 100L + index, updatedAtEpochMs = 100L + index,
            ))
        }
        val dao = db.downloadDao()
        assertEquals(ids.reversed(), dao.observeDisplayRows().first().map { it.trackId })

        dao.upsert(dao.byTrackId("a")!!.copy(downloadedBytes = 400, updatedAtEpochMs = 999))
        dao.upsert(dao.byTrackId("c")!!.copy(downloadedBytes = 900, updatedAtEpochMs = 2000))
        dao.upsert(dao.byTrackId("b")!!.copy(status = "QUEUED", updatedAtEpochMs = 3000))
        assertEquals(ids.reversed(), dao.observeDisplayRows().first().map { it.trackId })

        dao.upsert(dao.byTrackId("b")!!.copy(status = "CANCELED", updatedAtEpochMs = 4000))
        assertEquals(listOf("c", "a"), dao.observeDisplayRows().first().map { it.trackId })
        // Keep the durable canceled status so WorkManager cannot revive this transfer.
        assertEquals("CANCELED", dao.byTrackId("b")!!.status)
    }
}
