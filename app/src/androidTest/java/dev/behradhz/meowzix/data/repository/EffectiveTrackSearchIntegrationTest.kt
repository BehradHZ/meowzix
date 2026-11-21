package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TRACK_SEARCH_DATABASE_CALLBACK
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EffectiveTrackSearchIntegrationTest {
    private lateinit var db: MeowzixDatabase
    private lateinit var indexer: EffectiveTrackSearchIndexer
    private lateinit var repository: LibraryQueryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java)
            .addCallback(TRACK_SEARCH_DATABASE_CALLBACK)
            .build()
        indexer = EffectiveTrackSearchIndexer(db, db.libraryDao(), db.libraryToolsDao())
        repository = LibraryQueryRepository(db.libraryBrowseDao(), db.libraryDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun coldSearchFindsTriggerIndexedTrackWithoutFullRebuild() = runTest {
        insertTrack(
            id = "cccccccc-cccc-cccc-cccc-cccccccccccc",
            title = "Instant Search",
            artist = "Fast Artist",
            album = "Live Index",
        )
        // No indexer.refreshAll() call: production trigger should have indexed this row.
        assertEquals("Instant Search", repository.search("instant").single().title)
    }

    @Test
    fun effectiveMetadataSurvivesRepositoryRecreationWithoutRebuild() = runTest {
        val id = "dddddddd-dddd-dddd-dddd-dddddddddddd"
        insertTrack(id, "Provider song", "Provider artist", "Provider album")
        db.libraryToolsDao().upsertMetadataOverride(
            TrackMetadataOverrideEntity(
                trackId = id,
                title = "Overridden song",
                artist = "Effective artist",
                album = null,
                artworkRef = null,
                updatedAtEpochMs = 10L,
            ),
        )
        indexer.refreshTrack(id)
        val recreated = LibraryQueryRepository(db.libraryBrowseDao(), db.libraryDao())
        assertEquals("Overridden song", recreated.search("effective artist").single().title)
    }

    @Test
    fun mixedPersianEnglishAndCompoundTitleArtistQueriesUseEffectiveNormalizedIndex() = runTest {
        insertTrack(
            id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            title = "شب تهران",
            artist = "Radiohead",
            album = "موسيقي ۲۰۲۶",
        )
        indexer.refreshAll()

        assertEquals(1, repository.search("شب radiohead").size)
        assertEquals(1, repository.search("موسیقی 2026").size)
    }

    @Test
    fun metadataOverrideBecomesSearchableImmediatelyWithoutMutatingProviderMetadata() = runTest {
        val id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        insertTrack(id, "Provider title", "Provider artist", "Provider album")
        indexer.refreshAll()
        db.libraryToolsDao().upsertMetadataOverride(
            TrackMetadataOverrideEntity(
                trackId = id,
                title = "عنوان تازه",
                artist = "هنرمند جدید",
                album = "آلبوم نو",
                artworkRef = null,
                updatedAtEpochMs = 2L,
            ),
        )
        indexer.refreshTrack(id)

        val result = repository.search("عنوان تازه هنرمند").single()
        assertEquals("عنوان تازه", result.title)
        assertEquals("Provider title", db.libraryDao().trackById(id)!!.title)
        assertTrue(repository.search("آلبوم نو").isNotEmpty())
    }

    @Test
    fun artistAndAlbumSearchRunsInSqlAndOnlyMatchesTheirOwnMetadataFields() = runTest {
        insertTrack(
            id = "11111111-1111-1111-1111-111111111111",
            title = "Needle Track",
            artist = "Fast Artist",
            album = "Live Album",
        )
        insertTrack(
            id = "22222222-2222-2222-2222-222222222222",
            title = "Another Track",
            artist = "Fast Artist",
            album = "Live Album",
        )
        insertTrack(
            id = "33333333-3333-3333-3333-333333333333",
            title = "Needle Title Only",
            artist = "Other Artist",
            album = "Different Album",
        )

        val artists = repository.searchArtists("fast")
        assertEquals(1, artists.size)
        assertEquals("Fast Artist", artists.single().name)
        assertEquals(2, artists.single().trackCount)
        assertTrue("A title-only match must not leak into Artists", repository.searchArtists("needle").isEmpty())

        val albums = repository.searchAlbums("live fast")
        assertEquals(1, albums.size)
        assertEquals("Live Album", albums.single().name)
        assertEquals(2, albums.single().trackCount)
        assertTrue("A title-only match must not leak into Albums", repository.searchAlbums("needle").isEmpty())

        assertEquals("Fast Artist", repository.searchArtists("ast").single().name)
        assertEquals("Live Album", repository.searchAlbums("ive alb").single().name)
    }

    @Test
    fun artistAndAlbumSearchUseEffectiveMetadataOverrides() = runTest {
        val id = "44444444-4444-4444-4444-444444444444"
        insertTrack(id, "Provider title", "Provider artist", "Provider album")
        db.libraryToolsDao().upsertMetadataOverride(
            TrackMetadataOverrideEntity(
                trackId = id,
                title = null,
                artist = "Effective Artist",
                album = "Effective Album",
                artworkRef = null,
                updatedAtEpochMs = 20L,
            ),
        )
        indexer.refreshTrack(id)

        assertEquals("Effective Artist", repository.searchArtists("effective").single().name)
        assertEquals("Effective Album", repository.searchAlbums("effective").single().name)
        assertTrue(repository.searchArtists("provider").isEmpty())
        assertTrue(repository.searchAlbums("provider").isEmpty())
    }

    private suspend fun insertTrack(id: String, title: String, artist: String, album: String) {
        db.libraryDao().upsertTrack(
            TrackEntity(
                id = id,
                title = title,
                normalizedTitle = TextNormalizer.normalize(title)!!,
                artist = artist,
                normalizedArtist = TextNormalizer.normalize(artist),
                album = album,
                durationMs = 180_000L,
                trackNumber = null,
                year = null,
                artworkRef = null,
                favorite = false,
                hidden = false,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        db.libraryDao().upsertSource(
            TrackSourceEntity(
                id = "source-$id",
                trackId = id,
                type = TrackSourceType.LOCAL_MEDIASTORE,
                availability = SourceAvailability.AVAILABLE_LOCAL,
                contentUri = "content://search/$id",
                localPath = null,
                mimeType = "audio/mpeg",
                fileSizeBytes = 1000L,
                contentHashSha256 = null,
                trainingEligible = true,
                createdAtEpochMs = 1L,
                lastVerifiedAtEpochMs = 1L,
            ),
        )
    }
}
