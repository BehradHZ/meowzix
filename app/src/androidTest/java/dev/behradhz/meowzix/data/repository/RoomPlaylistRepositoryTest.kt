package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.RulePlaylistEntity
import dev.behradhz.meowzix.data.db.PlaylistEntity
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
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
        trackIds.forEachIndexed { index, id ->
            database.libraryDao().upsertTrack(track(id, index))
            database.libraryDao().upsertSource(TrackSourceEntity(
                id = "source-$id", trackId = id.toString(), type = TrackSourceType.LOCAL_MEDIASTORE,
                availability = SourceAvailability.AVAILABLE_LOCAL,
                contentUri = "content://media/$id", localPath = null, mimeType = "audio/mpeg",
                fileSizeBytes = 1, contentHashSha256 = null, trainingEligible = true,
                createdAtEpochMs = 1, lastVerifiedAtEpochMs = 1,
            ))
        }
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
        trackIds.forEachIndexed { index, id ->
            database.libraryDao().upsertTrack(track(id, index))
            database.libraryDao().upsertSource(TrackSourceEntity(
                id = "source-$id", trackId = id.toString(), type = TrackSourceType.LOCAL_MEDIASTORE,
                availability = SourceAvailability.AVAILABLE_LOCAL,
                contentUri = "content://media/$id", localPath = null, mimeType = "audio/mpeg",
                fileSizeBytes = 1, contentHashSha256 = null, trainingEligible = true,
                createdAtEpochMs = 1, lastVerifiedAtEpochMs = 1,
            ))
        }
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

    @Test
    fun telegramResyncKeepsCustomPlaylistMetadata() = runTest {
        val playlistId = UUID.randomUUID()
        val initial = PlaylistEntity(
            id = playlistId.toString(),
            title = "My Telegram Channel",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        database.playlistDao().insertPlaylistIfAbsent(initial)
        repository.updateMetadata(playlistId, "Night collection", "Custom notes", "content://cover")
        database.playlistDao().insertPlaylistIfAbsent(initial.copy(title = "Renamed Telegram Chat"))

        val saved = database.playlistDao().allPlaylists().single()
        assertEquals("Night collection", saved.title)
        assertEquals("Custom notes", saved.description)
        assertEquals("content://cover", saved.artworkRef)
        assertEquals(1L, saved.createdAtEpochMs)
    }

    @Test
    fun telegramPlaylistSyncTracksOnlyUnmodifiedFieldsAndCanBeRestored() = runTest {
        val id = UUID.randomUUID()
        val dao = database.playlistDao()
        dao.insertPlaylistIfAbsent(
            dev.behradhz.meowzix.data.db.PlaylistEntity(
                id = id.toString(), title = "Original chat",
                createdAtEpochMs = 1L, updatedAtEpochMs = 1L, telegramManaged = true,
            ),
        )
        // The normal state follows a Telegram chat rename.
        dao.syncTelegramTitle(id.toString(), "Renamed on Telegram", 2L)
        assertEquals("Renamed on Telegram", repository.observePlaylists().first().single().title)

        // Editing only the title must leave the artwork and description on their own paths.
        repository.updateMetadata(id, "My mix", null, null)
        dao.syncTelegramTitle(id.toString(), "New Telegram title", 3L)
        val customized = repository.observePlaylists().first().single()
        assertEquals("My mix", customized.title)
        assertEquals(true, customized.titleOverridden)
        assertEquals(false, customized.descriptionOverridden)
        assertEquals(false, customized.artworkOverridden)
        assertEquals(null, customized.artworkRef)
        assertEquals(true, customized.modified)

        // A description customization does not stop untouched Telegram fields from syncing.
        repository.restoreTelegramMetadata(id, "New Telegram title")
        repository.updateMetadata(id, "New Telegram title", "Notes", null)
        dao.syncTelegramTitle(id.toString(), "Updated again", 4L)
        val descriptionOnly = repository.observePlaylists().first().single()
        assertEquals("Updated again", descriptionOnly.title)
        assertEquals("Notes", descriptionOnly.description)
        assertEquals(false, descriptionOnly.titleOverridden)
        assertEquals(true, descriptionOnly.descriptionOverridden)

        // Cover override is independent; clearing it returns to live Telegram artwork.
        repository.updateMetadata(id, "Updated again", "Notes", "content://local-cover")
        assertEquals(true, repository.observePlaylists().first().single().artworkOverridden)
        repository.updateMetadata(id, "Updated again", "Notes", null)
        assertEquals(false, repository.observePlaylists().first().single().artworkOverridden)

        repository.restoreTelegramMetadata(id, "Current channel title")
        val restored = repository.observePlaylists().first().single()
        assertEquals("Current channel title", restored.title)
        assertEquals(null, restored.description)
        assertEquals(null, restored.artworkRef)
        assertEquals(false, restored.modified)
        assertEquals(true, restored.telegramManaged)
    }

    @Test
    fun archivedSmartPlaylistsRemainHiddenWithoutDeletingUserData() = runTest {
        val manualId = repository.create("Manual")
        val legacyId = repository.create("Old smart playlist")
        database.libraryToolsDao().upsertRulePlaylist(
            RulePlaylistEntity(legacyId.toString(), "ALL", "v1", "TITLE", 1L),
        )

        assertEquals(listOf(manualId), repository.observePlaylists().first().map { it.id })
        assertEquals(listOf(manualId.toString()), database.playlistDao().browsePlaylists(100, 0).map { it.id })
        assertNull(database.playlistDao().browsePlaylist(legacyId.toString()))
        assertEquals(2, database.playlistDao().allPlaylists().size)
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
