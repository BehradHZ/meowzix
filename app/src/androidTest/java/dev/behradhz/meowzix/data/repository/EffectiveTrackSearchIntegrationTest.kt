package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.MeowzixDatabase
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
        db = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        indexer = EffectiveTrackSearchIndexer(db, db.libraryDao(), db.libraryToolsDao())
        repository = LibraryQueryRepository(db.libraryBrowseDao(), db.libraryDao(), indexer)
    }

    @After
    fun tearDown() = db.close()

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
