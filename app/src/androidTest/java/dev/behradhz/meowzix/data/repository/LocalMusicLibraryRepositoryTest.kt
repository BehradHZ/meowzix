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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
