package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.DownloadRecordEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TelegramSelectedSourceEntity
import dev.behradhz.meowzix.data.db.TelegramTrackSourceEntity
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.domain.settings.LibraryGroupMode
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramSourceVisibilityIntegrationTest {
    private lateinit var db: MeowzixDatabase
    private lateinit var library: LibraryQueryRepository
    private lateinit var paged: PagedLibraryTracks
    private val account = "account-1"

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        library = LibraryQueryRepository(
            db.libraryBrowseDao(), db.libraryDao(),
            EffectiveTrackSearchIndexer(db, db.libraryDao(), db.libraryToolsDao()),
        )
        paged = PagedLibraryTracks(db.libraryDao(), db.libraryBrowseDao())
    }

    @After fun tearDown() = db.close()

    @Test fun deselectHidesDownloadedMusicFromLibraryButKeepsItsBytesAndRecord() = runTest {
        val id = UUID.randomUUID()
        selectChat(10)
        addTrack(id, "Deselect exclusive", favorite = true)
        addTelegramSource(id, 10, 100)
        val copyId = "offline-$id"
        db.libraryDao().upsertSource(source(copyId, id, TrackSourceType.APP_OFFLINE_COPY, SourceAvailability.AVAILABLE_LOCAL))
        db.downloadDao().upsert(DownloadRecordEntity(
            id = "download-$id", trackId = id.toString(), trackSourceId = copyId,
            tdFileId = 100, status = "COMPLETE", downloadedBytes = 4096, totalBytes = 4096,
            localPath = "/private/meowzix/$id.mp3", pinned = true, failureReason = null,
            createdAtEpochMs = 1, updatedAtEpochMs = 1,
        ))
        val playlist = db.playlistDao()
        playlist.upsertPlaylist(dev.behradhz.meowzix.data.db.PlaylistEntity(
            id = "playlist-1", title = "Manual", createdAtEpochMs = 1, updatedAtEpochMs = 1,
        ))
        playlist.insertTrack(dev.behradhz.meowzix.data.db.PlaylistTrackEntity("playlist-1", id.toString(), 0, 1))
        assertEquals(listOf(id), library.availableTrackIds())
        assertEquals(listOf(id), orderedIds(LibraryAvailabilityFilter.OFFLINE))
        assertEquals(1, db.libraryBrowseDao().observeFavoriteCount().first())
        assertEquals(1, db.libraryBrowseDao().observeArtistSummaries().first().single().trackCount)
        assertEquals(1, playlist.observeTracks("playlist-1").first().size)

        db.telegramDao().deleteSelectedSource(account, 10)

        assertTrue(library.availableTrackIds().isEmpty())
        assertTrue(orderedIds(LibraryAvailabilityFilter.ALL).isEmpty())
        assertTrue(orderedIds(LibraryAvailabilityFilter.OFFLINE).isEmpty())
        assertTrue(db.libraryBrowseDao().browseTracks(50, 0).isEmpty())
        assertEquals(0, db.libraryBrowseDao().observeFavoriteCount().first())
        assertTrue(db.libraryBrowseDao().observeArtistSummaries().first().isEmpty())
        assertTrue(db.libraryBrowseDao().observeAlbumSummaries().first().isEmpty())
        assertTrue(playlist.observeTracks("playlist-1").first().isEmpty())
        assertNotNull(db.libraryDao().trackById(id.toString()))
        assertEquals(2, db.libraryDao().sourcesForTrack(id.toString()).size)
        assertEquals(true, db.downloadDao().byTrackId(id.toString())?.pinned)
        assertEquals("/private/meowzix/$id.mp3", db.downloadDao().byTrackId(id.toString())?.localPath)

        selectChat(10)
        assertEquals(listOf(id), library.availableTrackIds())
        assertEquals(listOf(id), orderedIds(LibraryAvailabilityFilter.OFFLINE))
        assertEquals(1, playlist.observeTracks("playlist-1").first().size)
    }

    @Test fun otherSelectedChatOrIndependentLocalSourceStillMakesTrackVisible() = runTest {
        val shared = UUID.randomUUID()
        val local = UUID.randomUUID()
        selectChat(10)
        selectChat(20)
        addTrack(shared, "Shared song")
        addTelegramSource(shared, 10, 100)
        addTelegramSource(shared, 20, 200)
        addTrack(local, "Local song")
        addTelegramSource(local, 10, 300)
        db.libraryDao().upsertSource(source("device-$local", local, TrackSourceType.LOCAL_MEDIASTORE, SourceAvailability.AVAILABLE_LOCAL))

        db.telegramDao().deleteSelectedSource(account, 10)
        assertEquals(setOf(shared, local), library.availableTrackIds().toSet())

        db.telegramDao().deleteSelectedSource(account, 20)
        assertEquals(listOf(local), library.availableTrackIds())
        assertEquals(listOf(local), orderedIds(LibraryAvailabilityFilter.ALL))
    }

    private suspend fun orderedIds(filter: LibraryAvailabilityFilter): List<UUID> = paged.orderedTrackIds(
        LibrarySortMode.TITLE_ASC, LibraryGroupMode.NONE, filter,
    )

    private suspend fun selectChat(chatId: Long) {
        db.telegramDao().upsertSelectedSource(TelegramSelectedSourceEntity(
            accountId = account, chatId = chatId, title = "Chat $chatId",
            kind = "CHANNEL", newestMessageId = null, initialScanComplete = true, lastSyncedAtEpochMs = null,
        ))
    }

    private suspend fun addTrack(id: UUID, title: String, favorite: Boolean = false) {
        db.libraryDao().upsertTrack(TrackEntity(
            id = id.toString(), title = title, normalizedTitle = title.lowercase(),
            artist = "Artist", normalizedArtist = "artist", album = "Album",
            durationMs = 100000, trackNumber = null, year = null, artworkRef = null,
            favorite = favorite, hidden = false, createdAtEpochMs = 1, updatedAtEpochMs = 1,
        ))
    }

    private suspend fun addTelegramSource(id: UUID, chatId: Long, tdId: Int) {
        val sourceId = "telegram-$id-$chatId"
        db.libraryDao().upsertSource(source(sourceId, id, TrackSourceType.TELEGRAM_REMOTE, SourceAvailability.REMOTE_ONLY))
        db.telegramDao().upsertTelegramTrackSource(TelegramTrackSourceEntity(
            trackSourceId = sourceId, accountId = account, chatId = chatId,
            messageId = tdId.toLong(), tdFileId = tdId, tdPersistentFileId = null,
            fileName = null, telegramTitle = null, telegramPerformer = null, remoteRevisionKey = null,
        ))
    }

    private fun source(id: String, trackId: UUID, type: TrackSourceType, availability: SourceAvailability) =
        TrackSourceEntity(
            id = id, trackId = trackId.toString(), type = type, availability = availability,
            contentUri = if (type == TrackSourceType.LOCAL_MEDIASTORE) "content://media/$id" else null,
            localPath = if (type == TrackSourceType.APP_OFFLINE_COPY) "/private/meowzix/$id.mp3" else null,
            mimeType = "audio/mpeg", fileSizeBytes = 4096, contentHashSha256 = null,
            trainingEligible = true, createdAtEpochMs = 1, lastVerifiedAtEpochMs = 1,
        )
}
