package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPlaylistRepositoryTest {
    private lateinit var database: MeowzixDatabase
    private lateinit var repository: RoomPlaylistRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        repository = RoomPlaylistRepository(database, database.playlistDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun playlistOrderPersistsAndCanBeReordered() = runTest {
        val trackIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        trackIds.forEachIndexed { index, id -> database.libraryDao().upsertTrack(track(id, index)) }
        val playlistId = repository.create("Road trip")
        repository.replaceTracks(playlistId, trackIds)

        repository.moveTrack(playlistId, 2, 0)

        assertEquals(
            listOf(trackIds[2], trackIds[0], trackIds[1]),
            repository.observeTracks(playlistId).first().map { it.id },
        )
    }

    @Test
    fun deletingPlaylistRemovesMembershipButPreservesTracksAndOtherPlaylists() = runTest {
        val trackIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        trackIds.forEachIndexed { index, id -> database.libraryDao().upsertTrack(track(id, index)) }
        val deletedId = repository.create("Delete me")
        val retainedId = repository.create("Keep me")
        repository.replaceTracks(deletedId, trackIds)
        repository.replaceTracks(retainedId, listOf(trackIds.first()))

        repository.delete(deletedId)

        assertEquals(emptyList<UUID>(), repository.observeTracks(deletedId).first().map { it.id })
        assertEquals(listOf(retainedId), repository.observePlaylists().first().map { it.id })
        assertEquals(listOf(trackIds.first()), repository.observeTracks(retainedId).first().map { it.id })
        assertEquals(trackIds.toSet(), database.libraryDao().allTracks().map { UUID.fromString(it.id) }.toSet())
    }

    private fun track(id: UUID, index: Int) = TrackEntity(
        id = id.toString(),
        title = "Track $index",
        normalizedTitle = "track $index",
        artist = null,
        normalizedArtist = null,
        album = null,
        durationMs = 1L,
        trackNumber = null,
        year = null,
        artworkRef = null,
        favorite = false,
        hidden = false,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}
