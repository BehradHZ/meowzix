package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.localmedia.LocalMediaScanner
import dev.behradhz.meowzix.data.localmedia.ScannedLocalTrack
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMusicLibraryRepositoryTest {
    private lateinit var database: MeowzixDatabase
    private lateinit var scanner: FakeLocalMediaScanner
    private lateinit var repository: LocalMusicLibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        scanner = FakeLocalMediaScanner()
        repository = LocalMusicLibraryRepository(
            context,
            database,
            database.libraryDao(),
            database.telegramDao(),
            scanner,
            dev.behradhz.meowzix.test.NoOpListeningHistory(),
            EffectiveTrackSearchIndexer(database, database.libraryDao(), database.libraryToolsDao()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun repeatedRefreshKeepsIdentityAndUpdatesMetadata() = runTest {
        scanner.tracks = listOf(track(1, title = "Old title"))
        val firstResult = repository.refreshLocalMusic()
        val firstTrack = repository.observeTracks().first().single()

        scanner.tracks = listOf(track(1, title = "New title"))
        val secondResult = repository.refreshLocalMusic()
        val secondTrack = repository.observeTracks().first().single()

        assertEquals(1, firstResult.created)
        assertEquals(0, secondResult.created)
        assertEquals(1, secondResult.updated)
        assertEquals(firstTrack.id, secondTrack.id)
        assertEquals("New title", secondTrack.title)
    }

    @Test
    fun removingFromLibraryHidesTrackAcrossScansWithoutDeletingSourceAndReAddRestoresIt() = runTest {
        scanner.tracks = listOf(track(1))
        repository.refreshLocalMusic()
        val song = repository.observeTracks().first().single()
        val sourcesBefore = database.libraryDao().sourcesForTrack(song.id.toString())
        repository.setFavorite(song.id, true)

        repository.removeFromLibrary(song.id)
        assertEquals(emptyList<dev.behradhz.meowzix.core.model.Track>(), repository.observeTracks().first())
        val hidden = database.libraryDao().trackById(song.id.toString())!!
        assertTrue(hidden.hidden)
        assertFalse(hidden.addedToLibrary)
        assertTrue(hidden.favorite)
        assertEquals(sourcesBefore, database.libraryDao().sourcesForTrack(song.id.toString()))

        // Local MediaStore scans must not silently reinsert a user-hidden song.
        repository.refreshLocalMusic()
        assertTrue(database.libraryDao().trackById(song.id.toString())!!.hidden)
        assertEquals(0, repository.observeTracks().first().size)

        // Explicit Add to Library is the inverse of Hide and must preserve identity.
        repository.addToLibrary(song.id)
        assertEquals(song.id, repository.observeTracks().first().single().id)
        assertFalse(database.libraryDao().trackById(song.id.toString())!!.hidden)
        assertTrue(database.libraryDao().trackById(song.id.toString())!!.addedToLibrary)
        val sourcesAfter = database.libraryDao().sourcesForTrack(song.id.toString())
        // Refresh updates lastVerifiedAtEpochMs even though the physical file and
        // canonical source identity remain unchanged.
        assertEquals(sourcesBefore.map { it.id }, sourcesAfter.map { it.id })
        assertEquals(sourcesBefore.map { it.contentUri }, sourcesAfter.map { it.contentUri })
        assertEquals(sourcesBefore.map { it.type }, sourcesAfter.map { it.type })
        assertEquals(sourcesBefore.map { it.availability }, sourcesAfter.map { it.availability })
    }

    @Test
    fun removedSourceIsMarkedMissingWhileLogicalTrackIsRetained() = runTest {
        scanner.tracks = listOf(track(1))
        repository.refreshLocalMusic()
        val trackId = repository.observeTracks().first().single().id.toString()

        scanner.tracks = emptyList()
        val result = repository.refreshLocalMusic()

        assertEquals(1, result.markedMissing)
        assertEquals(SourceAvailability.MISSING, database.libraryDao().allLocalSources().single().availability)
        assertNotNull(database.libraryDao().trackById(trackId))
        assertEquals(0, repository.observeTracks().first().size)
    }

    @Test
    fun unchangedRefreshAvoidsRewritingTracks() = runTest {
        scanner.tracks = (0 until 1_000).map(::track)

        val firstResult = repository.refreshLocalMusic()
        val secondResult = repository.refreshLocalMusic()

        assertEquals(1_000, firstResult.created)
        assertEquals(0, secondResult.created)
        assertEquals(0, secondResult.updated)
        assertEquals(1_000, repository.observeTracks().first().size)
    }

    @Test
    fun successfulRefreshMakesAutomaticRefreshFresh() = runTest {
        assertTrue(repository.shouldRefreshLocalMusic())
        scanner.tracks = listOf(track(1))
        repository.refreshLocalMusic()
        assertFalse(repository.shouldRefreshLocalMusic())
    }

    @Test
    fun localAndRemoteMetadataMatchBecomeOneTrackWithTwoSources() = runTest {
        val now = 1_700_000_000_000L
        database.libraryDao().upsertTrack(
            TrackEntity(
                id = "00000000-0000-0000-0000-000000000001",
                title = "Track 1",
                normalizedTitle = "track 1",
                artist = "Artist 1",
                normalizedArtist = "artist 1",
                album = null,
                durationMs = 180_000L,
                trackNumber = null,
                year = null,
                artworkRef = null,
                favorite = false,
                hidden = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        database.libraryDao().upsertSource(
            TrackSourceEntity(
                id = "00000000-0000-0000-0000-000000000002",
                trackId = "00000000-0000-0000-0000-000000000001",
                type = TrackSourceType.TELEGRAM_REMOTE,
                availability = SourceAvailability.REMOTE_ONLY,
                contentUri = null,
                localPath = null,
                mimeType = "audio/mpeg",
                fileSizeBytes = 5_000_000L,
                contentHashSha256 = null,
                trainingEligible = true,
                createdAtEpochMs = now,
                lastVerifiedAtEpochMs = now,
            ),
        )
        scanner.tracks = listOf(track(1))

        repository.refreshLocalMusic()

        val tracks = repository.observeTracks().first()
        assertEquals(1, tracks.size)
        assertEquals(2, database.libraryDao().sourcesForTrack(tracks.single().id.toString()).size)
        assertEquals(track(1).contentUri, repository.availableLocalTracks().single().contentUri)
    }

    @Test
    fun currentTrackLookupIsScopedAndStillSeesFavoriteAndArtworkRepairs() = runTest {
        scanner.tracks = listOf(track(1), track(2))
        repository.refreshLocalMusic()
        val selected = repository.observeTracks().first().first { it.title == "Track 1" }
        val other = repository.observeTracks().first().first { it.title == "Track 2" }

        assertEquals(selected, repository.observeTrack(selected.id).first())
        assertEquals(selected, repository.trackById(selected.id))
        assertEquals(other, repository.observeTrack(other.id).first())
        assertNull(repository.observeTrack(UUID.randomUUID()).first())

        repository.setFavorite(selected.id, true)
        assertEquals(true, repository.observeTrack(selected.id).first()?.favorite)
        assertEquals(false, repository.observeTrack(other.id).first()?.favorite)

        val repairedArtwork = "file:///meowzix/artwork/repaired.jpg"
        database.libraryDao().setArtworkRef(selected.id.toString(), repairedArtwork, 1_900_000_000_000L)
        assertEquals(repairedArtwork, repository.observeTrack(selected.id).first()?.artworkRef)

        // Adding an unrelated song does not change the selected song's metadata.
        scanner.tracks = listOf(track(1), track(2), track(3))
        repository.refreshLocalMusic()
        assertEquals("Track 1", repository.trackById(selected.id)?.title)
    }

    @Test
    fun shuffleArtistsQueriesOnlyRequestedIds() = runTest {
        scanner.tracks = listOf(track(1), track(2), track(3))
        repository.refreshLocalMusic()
        val songs = repository.observeTracks().first().sortedBy { it.title }
        val selected = listOf(songs[0].id, songs[2].id)
        assertEquals(
            mapOf(songs[0].id to songs[0].artist, songs[2].id to songs[2].artist),
            repository.shuffleArtists(selected),
        )
        assertEquals(emptyMap<java.util.UUID, String?>(), repository.shuffleArtists(emptyList()))
    }


    @Test
    fun queueStatusLookupIsBoundedAcrossChunksAndUpdatesFavorite() = runTest {
        scanner.tracks = (0 until 425).map(::track)
        repository.refreshLocalMusic()
        val all = repository.observeTracks().first().map { it.id }
        val requested = all.take(410)
        val excluded = all.last()
        val missing = UUID.randomUUID()

        val statuses = repository.observeQueueTrackStatuses(requested + missing).first()
        assertEquals(410, statuses.size)
        assertFalse(excluded in statuses)
        assertFalse(missing in statuses)
        assertTrue(statuses.values.all { it.availability ==
            dev.behradhz.meowzix.domain.library.LibraryTrackAvailability.OFFLINE })

        repository.setFavorite(requested.first(), true)
        val updated = repository.observeQueueTrackStatuses(requested).first()
        assertEquals(true, updated[requested.first()]?.favorite)
        assertEquals(false, updated[requested.last()]?.favorite)
        assertTrue(repository.observeQueueTrackStatuses(emptyList()).first().isEmpty())
    }

    @Test
    fun queueStatusPreservesCloudFallbackAndHidesMissingSources() = runTest {
        scanner.tracks = listOf(track(1))
        repository.refreshLocalMusic()
        val songId = repository.observeTracks().first().single().id
        val now = 1_900_000_000_000L
        val remoteId = UUID.randomUUID().toString()
        database.libraryDao().upsertSource(
            TrackSourceEntity(
                id = remoteId, trackId = songId.toString(),
                type = TrackSourceType.TELEGRAM_REMOTE,
                availability = SourceAvailability.REMOTE_ONLY,
                contentUri = null, localPath = null, mimeType = "audio/mpeg",
                fileSizeBytes = 5_000_000L, contentHashSha256 = null,
                trainingEligible = true, createdAtEpochMs = now, lastVerifiedAtEpochMs = now,
            ),
        )
        database.telegramDao().upsertTelegramTrackSource(
            dev.behradhz.meowzix.data.db.TelegramTrackSourceEntity(
                trackSourceId = remoteId,
                accountId = "queue-test",
                chatId = 5L,
                messageId = 9L,
                tdFileId = 123,
                tdPersistentFileId = null,
                fileName = "track.mp3",
                telegramTitle = "Track",
                telegramPerformer = "Artist",
                remoteRevisionKey = null,
            ),
        )
        repository.addToLibrary(songId)
        val flow = repository.observeQueueTrackStatuses(listOf(songId))
        assertEquals(
            dev.behradhz.meowzix.domain.library.LibraryTrackAvailability.OFFLINE,
            flow.first().getValue(songId).availability,
        )

        val local = database.libraryDao().allLocalSources().single()
        database.libraryDao().updateAvailability(local.id, SourceAvailability.MISSING, now)
        assertEquals(
            dev.behradhz.meowzix.domain.library.LibraryTrackAvailability.CLOUD,
            flow.first().getValue(songId).availability,
        )

        database.libraryDao().updateAvailability(remoteId, SourceAvailability.MISSING, now)
        assertTrue(flow.first().isEmpty())
    }

    @Test
    fun favoriteStatePersists() = runTest {
        scanner.tracks = listOf(track(1))
        repository.refreshLocalMusic()
        val original = repository.observeTracks().first().single()

        repository.setFavorite(original.id, true)

        assertEquals(true, repository.observeTracks().first().single().favorite)
    }

    private fun track(index: Int, title: String = "Track $index") = ScannedLocalTrack(
        mediaStoreId = index.toLong(),
        contentUri = "content://media/external/audio/media/$index",
        title = title,
        artist = "Artist ${index % 10}",
        album = "Album ${index % 5}",
        durationMs = 180_000L,
        trackNumber = index,
        year = 2026,
        artworkRef = null,
        mimeType = "audio/mpeg",
        fileSizeBytes = 5_000_000L,
        relativePath = "Music/",
        displayName = "track-$index.mp3",
        dateModifiedSeconds = 1_700_000_000L + index,
    )
}

private class FakeLocalMediaScanner : LocalMediaScanner {
    var tracks: List<ScannedLocalTrack> = emptyList()

    override suspend fun scan(): List<ScannedLocalTrack> = tracks
}
