package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.PlaylistEntity
import dev.behradhz.meowzix.data.db.PlaylistTrackEntity
import dev.behradhz.meowzix.data.db.TelegramSelectedSourceEntity
import dev.behradhz.meowzix.data.db.TelegramTrackSourceEntity
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramMultiSourceIntegrationTest {
    private lateinit var db: MeowzixDatabase
    private val account = "shared-account"
    private val trackId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), MeowzixDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test fun oneSongInThreeChatsHasOneCanonicalIdentityAndThreePlaylistMemberships() = runTest {
        seedTrack()
        listOf(10L, 20L, 30L).forEach { addOccurrence(it) }

        val telegram = db.telegramDao()
        assertEquals(listOf(trackId), telegram.trackIdsForPersistentTelegramFile(account, "same-file"))
        assertEquals(3, telegram.telegramSourcesForTrack(account, trackId).size)
        assertEquals(setOf(10L, 20L, 30L), telegram.availableChatIdsForTrack(account, trackId).toSet())
        assertEquals(1, db.libraryDao().observeAvailableTracks().first().size)
        listOf(10L, 20L, 30L).forEach { chat ->
            assertEquals(listOf(trackId), telegram.activeTrackIdsForChat(account, chat))
            assertEquals(listOf(trackId), db.playlistDao().entries("chat-$chat").map { it.trackId })
        }

        // A repeated sync of an existing message is an upsert, not a new occurrence.
        val sameMessage = telegram.telegramSourceForMessage(account, 20L, 120L)!!
        telegram.upsertTelegramTrackSource(sameMessage)
        assertEquals(3, telegram.allTelegramSourcesForTrack(account, trackId).size)
        assertTrue(db.libraryDao().trackById(trackId)!!.favorite)
    }

    @Test fun deletingOneChatMessageLeavesOtherChatSourcesAvailable() = runTest {
        seedTrack()
        addOccurrence(10L)
        addOccurrence(20L)
        addOccurrence(30L)
        val telegram = db.telegramDao()
        db.libraryDao().updateAvailability("remote-10", SourceAvailability.MISSING, 10L)
        assertEquals(2, telegram.telegramSourcesForTrack(account, trackId).size)
        assertEquals(emptyList<String>(), telegram.activeTrackIdsForChat(account, 10L))
        assertEquals(listOf(trackId), telegram.activeTrackIdsForChat(account, 20L))
        assertEquals(3, telegram.allTelegramSourcesForTrack(account, trackId).size)
        assertEquals(1, db.libraryDao().observeAvailableTracks().first().size)

        db.libraryDao().updateAvailability("remote-20", SourceAvailability.MISSING, 11L)
        db.libraryDao().updateAvailability("remote-30", SourceAvailability.MISSING, 12L)
        assertTrue(telegram.telegramSourcesForTrack(account, trackId).isEmpty())
        assertEquals(3, telegram.allTelegramSourcesForTrack(account, trackId).size)
        assertFalse(db.libraryDao().trackById(trackId) == null)
        assertTrue(db.libraryDao().observeAvailableTracks().first().isEmpty())
    }

    private suspend fun seedTrack() {
        db.libraryDao().upsertTrack(TrackEntity(
            id = trackId, title = "Numb", normalizedTitle = "numb",
            artist = "Linkin Park", normalizedArtist = "linkin park", album = null,
            durationMs = 185_000L, trackNumber = null, year = null, artworkRef = null,
            favorite = true, hidden = false, createdAtEpochMs = 1L, updatedAtEpochMs = 1L,
        ))
    }

    private suspend fun addOccurrence(chat: Long) {
        db.telegramDao().upsertSelectedSource(TelegramSelectedSourceEntity(
            accountId = account, chatId = chat, title = "Chat $chat", kind = "CHANNEL",
            includeInLibrary = true, newestMessageId = null, initialScanComplete = true,
            lastSyncedAtEpochMs = null,
        ))
        db.libraryDao().upsertSource(TrackSourceEntity(
            id = "remote-$chat", trackId = trackId, type = TrackSourceType.TELEGRAM_REMOTE,
            availability = SourceAvailability.REMOTE_ONLY, contentUri = null, localPath = null,
            mimeType = "audio/mpeg", fileSizeBytes = 1234L, contentHashSha256 = null,
            trainingEligible = true, createdAtEpochMs = 1L, lastVerifiedAtEpochMs = 1L,
        ))
        db.telegramDao().upsertTelegramTrackSource(TelegramTrackSourceEntity(
            trackSourceId = "remote-$chat", accountId = account, chatId = chat,
            messageId = chat + 100L, tdFileId = chat.toInt(),
            tdPersistentFileId = "same-file", fileName = "numb.mp3",
            telegramTitle = "Numb", telegramPerformer = "Linkin Park",
            remoteRevisionKey = null,
        ))
        val playlistId = "chat-$chat"
        db.playlistDao().upsertPlaylist(PlaylistEntity(
            id = playlistId, title = "Chat $chat", telegramManaged = true,
            createdAtEpochMs = 1L, updatedAtEpochMs = 1L,
        ))
        db.playlistDao().insertTrack(PlaylistTrackEntity(playlistId, trackId, 0, 1L))
    }
}
