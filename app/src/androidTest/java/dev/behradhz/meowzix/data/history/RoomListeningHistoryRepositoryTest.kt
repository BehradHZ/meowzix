package dev.behradhz.meowzix.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.PlaybackInitiator
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomListeningHistoryRepositoryTest {
    private lateinit var database: MeowzixDatabase
    private lateinit var repository: RoomListeningHistoryRepository
    private val trackId = UUID.randomUUID()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        repository = RoomListeningHistoryRepository(database, database.historyDao())
    }

    @After fun tearDown() = database.close()

    @Test fun manualPlaybackLogsOnceAndClearRemovesHistory() = runTest {
        database.libraryDao().upsertTrack(track())
        val playbackId = repository.startPlayback(trackId, PlaybackInitiator.USER, PlaybackMode.ORDERED)
        repository.finalizePlayback(playbackId, 95_000, 100_000, false)
        repository.finalizePlayback(playbackId, 95_000, 100_000, false)

        val events = repository.observeEvents().first()
        assertEquals(1, events.count { it.type == ListeningEventType.MANUAL_SELECTED })
        assertEquals(1, events.count { it.type == ListeningEventType.PLAY_COMPLETED })
        assertEquals(1, repository.observeTrackStats().first().single().totalCompletions)

        repository.clear()
        assertEquals(emptyList<Any>(), repository.observeEvents().first())
    }

    private fun track() = TrackEntity(
        trackId.toString(), "Track", "track", null, null, null, 100_000,
        null, null, null, false, false, 1, 1,
    )
}
