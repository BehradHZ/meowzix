package dev.behradhz.meowzix.data.lyrics

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedLyricsPersistenceTest {
    private lateinit var db: MeowzixDatabase
    private val trackId = UUID.randomUUID()

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MeowzixDatabase::class.java,
        ).build()
        db.libraryDao().upsertTrack(TrackEntity(
            id = trackId.toString(), title = "Test", normalizedTitle = "test",
            artist = null, normalizedArtist = null, album = null, durationMs = 30_000L,
            trackNumber = null, year = null, artworkRef = null, favorite = false, hidden = false,
            createdAtEpochMs = 1L, updatedAtEpochMs = 1L,
        ))
    }

    @After fun tearDown() { db.close() }

    @Test fun removeAllSuppressesRedetectionUntilExplicitlyRestored() = runBlocking {
        val repo = RoomLyricsRepository(db, db.lyricsDao())
        val label = "Detected from source-1"
        val automatic = repo.saveEmbedded(trackId, "[00:01.00]Auto", label)
        val manual = repo.savePlainText(trackId, "My lyrics")
        assertEquals(2, repo.observe(trackId).first().versions.size)

        repo.removeAllFromTrack(trackId)
        val removed = repo.observe(trackId).first()
        assertTrue(removed.versions.isEmpty())
        assertEquals(null, removed.selected)
        assertTrue(removed.automaticDiscoveryDisabled)
        assertEquals(null, repo.saveEmbedded(trackId, "[00:02.00]Auto again", label))
        assertTrue(repo.observe(trackId).first().versions.isEmpty())
        assertEquals(1, db.lyricsDao().allDetachedTracks().size)

        // A new user-provided lyric may be added without re-enabling auto-detection.
        val newManual = repo.importLrc(trackId, "[00:01.00]New manual")
        assertEquals(newManual, repo.observe(trackId).first().selected?.id)
        assertEquals(null, repo.saveEmbedded(trackId, "[00:02.00]Still suppressed", label))

        repo.removeAllFromTrack(trackId)
        repo.restoreAutomaticDiscovery(trackId)
        assertTrue(!repo.observe(trackId).first().automaticDiscoveryDisabled)
        val detected = repo.saveEmbedded(trackId, "[00:02.00]Restored", label)
        assertEquals(detected, repo.observe(trackId).first().selected?.id)
        assertEquals(1, repo.observe(trackId).first().versions.size)
    }

    @Test fun repeatedDetectionDoesNotDuplicateAndManualSelectionAlwaysWins() = runBlocking {
        val repo = RoomLyricsRepository(db, db.lyricsDao())
        val label = "Detected from source-1"
        val first = repo.saveEmbedded(trackId, "[00:01.00]Automatic", label)
        val duplicate = repo.saveEmbedded(trackId, "[00:01.00]Automatic", label)
        assertEquals(first, duplicate)
        assertEquals(1, db.lyricsDao().versionsForTrack(trackId.toString()).size)
        assertEquals(first, repo.observe(trackId).first().selected?.id)

        val manual = repo.savePlainText(trackId, "My own lyrics")
        assertEquals(manual, repo.observe(trackId).first().selected?.id)

        // A new readable copy or updated tag must not replace a user's chosen version.
        repo.saveEmbedded(trackId, "[00:02.00]Updated", label)
        val versions = db.lyricsDao().versionsForTrack(trackId.toString())
        assertEquals(2, versions.size)
        assertEquals(manual, repo.observe(trackId).first().selected?.id)
        assertTrue(versions.any { it.sourceType == LyricsSourceType.EMBEDDED.name &&
            it.rawText == "[00:02.00]Updated" })
    }
}
