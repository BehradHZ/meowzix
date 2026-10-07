package dev.behradhz.meowzix.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.*
import dev.behradhz.meowzix.data.recommendation.DataStoreRecommendationFeedbackRepository
import dev.behradhz.meowzix.data.recommendation.PersonalizationTrainer
import dev.behradhz.meowzix.data.recommendation.TrainingDatasetBuilder
import dev.behradhz.meowzix.data.repository.EffectiveTrackSearchIndexer
import dev.behradhz.meowzix.data.repository.RulePlaylistCodec
import dev.behradhz.meowzix.data.settings.DataStoreSettingsRepository
import dev.behradhz.meowzix.domain.backup.BackupOptions
import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationBus
import dev.behradhz.meowzix.domain.recommendation.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLocalBackupRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MeowzixDatabase
    private lateinit var feedback: DataStoreRecommendationFeedbackRepository
    private lateinit var repository: RoomLocalBackupRepository
    private val trackId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        val bus = SmartQueueAdaptationBus()
        feedback = DataStoreRecommendationFeedbackRepository(context, bus)
        feedback.clearAll()
        val extractor = BackupTestExtractor()
        val trainer = PersonalizationTrainer(
            TrainingDatasetBuilder(
                historyDao = db.historyDao(),
                libraryDao = db.libraryDao(),
                toolsDao = db.libraryToolsDao(),
                audioFeatureDao = db.audioFeatureDao(),
                audioFeatureExtractor = extractor,
                sampleDao = db.trainingSampleDao(),
            ),
            BackupNoOpModel(),
            object : TrainingScheduler { override fun scheduleTraining(rebuild: Boolean) = Unit },
            bus,
        )
        repository = RoomLocalBackupRepository(
            database = db,
            libraryDao = db.libraryDao(),
            playlistDao = db.playlistDao(),
            lyricsDao = db.lyricsDao(),
            toolsDao = db.libraryToolsDao(),
            historyDao = db.historyDao(),
            feedbackRepository = feedback,
            settingsRepository = DataStoreSettingsRepository(context),
            personalizationTrainer = trainer,
            searchIndexer = EffectiveTrackSearchIndexer(db, db.libraryDao(), db.libraryToolsDao()),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fullRoundTripAndRepeatedImportStayIdempotent() = runTest {
        seedTrack(trackId.toString(), "hash-main", favorite = true)
        db.playlistDao().upsertPlaylist(PlaylistEntity("playlist", "Backup", null, null, 10L, 11L))
        db.playlistDao().insertTrack(PlaylistTrackEntity("playlist", trackId.toString(), 0, 12L))
        db.lyricsDao().insert(
            LyricsVersionEntity("lyrics", trackId.toString(), "USER", null, "[00:01]line", "LRC", true, true, 0L, 13L, 14L),
        )
        db.libraryToolsDao().upsertMetadataOverride(
            TrackMetadataOverrideEntity(trackId.toString(), "Edited", "Artist", "Album", null, 15L),
        )
        db.libraryToolsDao().upsertRulePlaylist(
            RulePlaylistEntity(
                "playlist",
                "ALL",
                RulePlaylistCodec.encode(listOf(PlaylistRule(RuleKind.FAVORITE))),
                "TITLE",
                16L,
            ),
        )
        feedback.set(trackId, RecommendationFeedbackAction.MORE_LIKE_THIS, Instant.ofEpochMilli(20L))

        val sessionId = UUID.randomUUID().toString()
        val playbackId = UUID.randomUUID().toString()
        db.historyDao().upsertSession(ListeningSessionEntity(sessionId, 30L, null, "ORDERED"))
        db.historyDao().insertEvent(
            ListeningEventEntity(
                id = "event-1",
                playbackInstanceId = playbackId,
                trackId = trackId.toString(),
                sessionId = sessionId,
                type = "PLAY_STARTED",
                occurredAtEpochMs = 30L,
                localHour = 10,
                dayOfWeek = 3,
                timeBucket = "MORNING",
                isWeekend = false,
                positionMs = 0L,
                durationMs = 180_000L,
                completionRatio = 0.0,
                initiatedBy = "USER",
                playbackMode = "ORDERED",
            ),
        )

        val bytes = repository.export(BackupOptions(includeHistory = true))
        val preview = repository.preview(bytes)
        assertTrue(preview.includeHistory)
        assertEquals(0, preview.unresolvedTrackReferences)

        db.playlistDao().deletePlaylist("playlist")
        db.lyricsDao().delete("lyrics", trackId.toString())
        db.libraryToolsDao().deleteMetadataOverride(trackId.toString())
        db.libraryDao().setFavorite(trackId.toString(), false, 40L)
        db.historyDao().clearEvents()
        db.historyDao().clearSessions()
        db.historyDao().clearPreferenceStats()
        feedback.clearAll()

        repository.restore(bytes)
        repository.restore(bytes)

        assertTrue(db.libraryDao().trackById(trackId.toString())!!.favorite)
        assertEquals(1, db.playlistDao().entries("playlist").size)
        assertEquals(1, db.lyricsDao().allVersions().size)
        assertEquals("Edited", db.libraryToolsDao().metadataOverride(trackId.toString())!!.title)
        assertEquals(1, db.libraryToolsDao().allRulePlaylists().size)
        assertEquals(1, db.historyDao().allEventsChronological().size)
        assertEquals(1, feedback.snapshot(Instant.now()).size)
    }

    @Test
    fun historyIsOffByDefault() = runTest {
        seedTrack(trackId.toString(), "hash-main", favorite = false)
        val sessionId = UUID.randomUUID().toString()
        db.historyDao().upsertSession(ListeningSessionEntity(sessionId, 1L, null, "ORDERED"))
        db.historyDao().insertEvent(
            ListeningEventEntity(
                id = "event-default-off",
                playbackInstanceId = UUID.randomUUID().toString(),
                trackId = trackId.toString(),
                sessionId = sessionId,
                type = "PLAY_STARTED",
                occurredAtEpochMs = 1L,
                localHour = 10,
                dayOfWeek = 3,
                timeBucket = "MORNING",
                isWeekend = false,
                positionMs = 0L,
                durationMs = 180_000L,
                completionRatio = 0.0,
                initiatedBy = "USER",
                playbackMode = "ORDERED",
            ),
        )

        val decoded = BackupFormat.decode(repository.export())

        assertFalse(decoded.includeHistory)
        assertTrue(decoded.records.none { it.type == "HISTORY" })
    }

    @Test
    fun stableHashResolvesAcrossInstallationIdsAndUnknownTrackStaysUnresolved() = runTest {
        val currentId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        seedTrack(currentId.toString(), "shared-hash", favorite = false)
        val oldId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val unknownId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
        val records = listOf(
            BackupRecord("TRACK", listOf(oldId.toString(), "song", "artist", "180000", "shared-hash", "true")),
            BackupRecord("TRACK", listOf(unknownId.toString(), "missing", "artist", "180000", "missing-hash", "true")),
        )
        val result = repository.restore(BackupFormat.encode("portable", 1L, false, records))

        assertTrue(db.libraryDao().trackById(currentId.toString())!!.favorite)
        assertEquals(1, result.unresolvedReferences)
        assertEquals(1, db.libraryToolsDao().unresolvedBackupReferences("portable").size)
    }

    @Test
    fun malformedRuleRollsBackEarlierRoomWrites() = runTest {
        val records = listOf(
            BackupRecord("PLAYLIST", listOf("tx-playlist", "Should roll back", null, null, "1", "1")),
            BackupRecord("RULE_PLAYLIST", listOf("tx-playlist", "ALL", "not-a-rule-codec", "TITLE", "1")),
        )
        val error = runCatching {
            repository.restore(BackupFormat.encode("rollback", 1L, false, records))
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(db.playlistDao().allPlaylists().none { it.id == "tx-playlist" })
    }

    @Test
    fun expiredFeedbackIsNotRestored() = runTest {
        seedTrack(trackId.toString(), "hash-main", favorite = false)
        val oldId = trackId.toString()
        val records = listOf(
            BackupRecord("TRACK", listOf(oldId, "song", "artist", "180000", "hash-main", "false")),
            BackupRecord(
                "FEEDBACK",
                listOf(
                    RecommendationFeedbackAction.SNOOZE.name,
                    "1000",
                    "2000",
                    oldId,
                    "song",
                    "artist",
                    "180000",
                    "hash-main",
                ),
            ),
        )

        repository.restore(BackupFormat.encode("expired", 1L, false, records))

        assertTrue(feedback.snapshot(Instant.now()).isEmpty())
    }

    private suspend fun seedTrack(id: String, hash: String, favorite: Boolean) {
        db.libraryDao().upsertTrack(
            TrackEntity(
                id = id,
                title = "Song",
                normalizedTitle = "song",
                artist = "Artist",
                normalizedArtist = "artist",
                album = "Album",
                durationMs = 180_000L,
                trackNumber = null,
                year = null,
                artworkRef = null,
                favorite = favorite,
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
                contentUri = "content://music/$id",
                localPath = null,
                mimeType = "audio/mpeg",
                fileSizeBytes = 1_000L,
                contentHashSha256 = hash,
                trainingEligible = true,
                createdAtEpochMs = 1L,
                lastVerifiedAtEpochMs = 1L,
            ),
        )
    }
}

private class BackupTestExtractor : AudioFeatureExtractor {
    override val extractorName = "backup-test"
    override val extractorVersion = "1"
    override val schemaVersion = 1
    override suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? = null
}

private class BackupNoOpModel : PersonalizationModel {
    private var current = PersonalizationModelState()

    override suspend fun state(): PersonalizationModelState = current

    override suspend fun score(
        context: RecommendationContext,
        candidates: List<TrackFeatures>,
    ): List<ModelTrackScore> = emptyList()

    override suspend fun scoreBatch(features: Map<UUID, DoubleArray>): Map<UUID, Double> = emptyMap()

    override suspend fun update(samples: List<TrainingSample>) = Unit

    override suspend fun rebuild(samples: List<TrainingSample>) = Unit

    override suspend fun reset(trainingDataVersion: Long) {
        current = PersonalizationModelState(
            trainingDataVersion = trainingDataVersion,
            historyFloorVersion = trainingDataVersion,
        )
    }
}
