package dev.behradhz.meowzix.data.repository

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
import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import dev.behradhz.meowzix.domain.library.RuleMatchMode
import dev.behradhz.meowzix.domain.library.RulePlaylistDefinition
import dev.behradhz.meowzix.domain.library.RulePlaylistSort
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
class TrackMergeIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MeowzixDatabase
    private lateinit var feedback: DataStoreRecommendationFeedbackRepository
    private lateinit var datasetBuilder: TrainingDatasetBuilder
    private lateinit var repository: RoomLibraryToolsRepository
    private lateinit var searchIndexer: EffectiveTrackSearchIndexer
    private lateinit var search: LibraryQueryRepository
    private val survivorId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val mergedId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        val bus = SmartQueueAdaptationBus()
        feedback = DataStoreRecommendationFeedbackRepository(context, bus)
        feedback.clearAll()
        val extractor = MergeTestExtractor()
        datasetBuilder = TrainingDatasetBuilder(
            historyDao = db.historyDao(),
            libraryDao = db.libraryDao(),
            toolsDao = db.libraryToolsDao(),
            audioFeatureDao = db.audioFeatureDao(),
            audioFeatureExtractor = extractor,
            sampleDao = db.trainingSampleDao(),
        )
        val trainer = PersonalizationTrainer(
            datasetBuilder,
            MergeNoOpModel(),
            object : TrainingScheduler { override fun scheduleTraining(rebuild: Boolean) = Unit },
            bus,
        )
        searchIndexer = EffectiveTrackSearchIndexer(db, db.libraryDao(), db.libraryToolsDao())
        repository = RoomLibraryToolsRepository(
            toolsDao = db.libraryToolsDao(),
            libraryDao = db.libraryDao(),
            browseDao = db.libraryBrowseDao(),
            database = db,
            feedbackRepository = feedback,
            personalizationTrainer = trainer,
            searchIndexer = searchIndexer,
            clock = FixedMergeClock(5_000L),
        )
        search = LibraryQueryRepository(db.libraryBrowseDao(), db.libraryDao(), searchIndexer)
        seed()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun mergeThenUnmergePreservesCrossFeatureProvenanceAndSingleLearningOutcome() = runTest {
        assertEquals(1, datasetBuilder.buildAll().size)
        // Prime the effective index before merging so post-merge assertions prove that merge
        // invalidation refreshes the already-initialized search identity.
        assertEquals(listOf(mergedId), search.search("merged album").map { it.id })

        val merged = repository.mergeTracks(survivorId, mergedId, confirmMetadataOnly = false)

        assertTrue(db.libraryDao().trackById(mergedId.toString())!!.hidden)
        assertTrue(db.libraryDao().trackById(survivorId.toString())!!.favorite)
        assertEquals(2, db.libraryDao().sourcesForTrack(survivorId.toString()).size)
        assertEquals(1, db.playlistDao().entries("manual").size)
        assertEquals(survivorId.toString(), db.lyricsDao().byId("lyric-merged")!!.trackId)
        assertEquals(survivorId.toString(), db.trackMergeDao().eventIdsForTrack(survivorId.toString()).let { ids ->
            assertEquals(3, ids.size)
            db.historyDao().allEventsChronological().first().trackId
        })
        assertEquals("Survivor title", db.libraryToolsDao().metadataOverride(survivorId.toString())!!.title)
        assertEquals("Merged album", db.libraryToolsDao().metadataOverride(survivorId.toString())!!.album)
        assertNotNull(
            db.audioFeatureDao().compatibleVector(
                mergedId.toString(),
                MergeTestExtractor.NAME,
                MergeTestExtractor.VERSION,
                MergeTestExtractor.SCHEMA,
            ),
        )
        val mergedDataset = datasetBuilder.buildAll()
        assertEquals(1, mergedDataset.size)
        assertEquals(survivorId, mergedDataset.single().trackId)
        assertEquals(listOf(survivorId), repository.evaluateRulePlaylist(UUID.fromString(SMART_ID)).map { it.id })
        assertEquals(listOf(survivorId), search.search("merged album").map { it.id })

        assertTrue(repository.unmerge(merged.journalId))

        assertFalse(db.libraryDao().trackById(mergedId.toString())!!.hidden)
        assertFalse(db.libraryDao().trackById(survivorId.toString())!!.favorite)
        assertTrue(db.libraryDao().trackById(mergedId.toString())!!.favorite)
        assertEquals(1, db.libraryDao().sourcesForTrack(mergedId.toString()).size)
        assertEquals(listOf(0, 1), db.playlistDao().entries("manual").map { it.position })
        assertEquals(mergedId.toString(), db.lyricsDao().byId("lyric-merged")!!.trackId)
        assertEquals(3, db.trackMergeDao().eventIdsForTrack(mergedId.toString()).size)
        assertEquals("Survivor title", db.libraryToolsDao().metadataOverride(survivorId.toString())!!.title)
        assertEquals("Merged album", db.libraryToolsDao().metadataOverride(mergedId.toString())!!.album)
        assertNotNull(
            db.audioFeatureDao().compatibleVector(
                mergedId.toString(),
                MergeTestExtractor.NAME,
                MergeTestExtractor.VERSION,
                MergeTestExtractor.SCHEMA,
            ),
        )
        val restoredDataset = datasetBuilder.buildAll()
        assertEquals(1, restoredDataset.size)
        assertEquals(mergedId, restoredDataset.single().trackId)
        assertEquals(listOf(mergedId), repository.evaluateRulePlaylist(UUID.fromString(SMART_ID)).map { it.id })
        assertEquals(listOf(mergedId), search.search("merged album").map { it.id })
        assertEquals(1, feedback.snapshot(Instant.now()).count { it.trackId == mergedId })
    }

    private suspend fun seed() {
        putTrack(survivorId, "Survivor", favorite = false, sourceId = "source-survivor")
        putTrack(mergedId, "Merged", favorite = true, sourceId = "source-merged")

        db.playlistDao().upsertPlaylist(PlaylistEntity("manual", "Manual", null, null, 1L, 1L))
        db.playlistDao().insertTrack(PlaylistTrackEntity("manual", survivorId.toString(), 0, 1L))
        db.playlistDao().insertTrack(PlaylistTrackEntity("manual", mergedId.toString(), 1, 2L))

        db.playlistDao().upsertPlaylist(PlaylistEntity(SMART_ID, "Favorites", null, null, 1L, 1L))
        db.libraryToolsDao().upsertRulePlaylist(
            RulePlaylistEntity(
                SMART_ID,
                RuleMatchMode.ALL.name,
                RulePlaylistCodec.encode(listOf(PlaylistRule(RuleKind.FAVORITE))),
                RulePlaylistSort.TITLE.name,
                1L,
            ),
        )

        db.lyricsDao().insert(
            LyricsVersionEntity("lyric-merged", mergedId.toString(), "USER", null, "lyrics", "PLAIN", true, true, 0L, 1L, 1L),
        )
        db.libraryToolsDao().upsertMetadataOverride(
            TrackMetadataOverrideEntity(survivorId.toString(), "Survivor title", null, null, null, 1L),
        )
        db.libraryToolsDao().upsertMetadataOverride(
            TrackMetadataOverrideEntity(mergedId.toString(), null, null, "Merged album", null, 1L),
        )

        val session = UUID.randomUUID().toString()
        val playback = UUID.randomUUID().toString()
        db.historyDao().upsertSession(ListeningSessionEntity(session, 1L, null, "ORDERED"))
        listOf("AUTO_SELECTED", "PLAY_STARTED", "PLAY_COMPLETED").forEachIndexed { index, type ->
            db.historyDao().insertEvent(
                ListeningEventEntity(
                    id = UUID.nameUUIDFromBytes("merge-event-$index".toByteArray()).toString(),
                    playbackInstanceId = playback,
                    trackId = mergedId.toString(),
                    sessionId = session,
                    type = type,
                    occurredAtEpochMs = 100L + index,
                    localHour = 10,
                    dayOfWeek = 3,
                    timeBucket = "MORNING",
                    isWeekend = false,
                    positionMs = if (type == "PLAY_COMPLETED") 180_000L else 0L,
                    durationMs = 180_000L,
                    completionRatio = if (type == "PLAY_COMPLETED") 1.0 else 0.0,
                    initiatedBy = "QUEUE",
                    playbackMode = "ORDERED",
                ),
            )
        }

        feedback.set(mergedId, RecommendationFeedbackAction.MORE_LIKE_THIS, Instant.ofEpochMilli(1_000L))

        db.audioFeatureDao().put(
            AudioFeatureVectorEntity(
                id = "feature-merged",
                trackId = mergedId.toString(),
                sourceIdUsed = "source-merged",
                extractorName = MergeTestExtractor.NAME,
                extractorVersion = MergeTestExtractor.VERSION,
                schemaVersion = MergeTestExtractor.SCHEMA,
                vectorFormat = "DENSE_DOUBLE",
                vectorBlob = byteArrayOf(1, 2, 3),
                generatedAtEpochMs = 1L,
                sourceContentHash = DUPLICATE_HASH,
            ),
        )
    }

    private suspend fun putTrack(id: UUID, title: String, favorite: Boolean, sourceId: String) {
        db.libraryDao().upsertTrack(
            TrackEntity(
                id.toString(),
                title,
                title.lowercase(),
                "Artist",
                "artist",
                "Album",
                180_000L,
                null,
                null,
                null,
                favorite,
                false,
                1L,
                1L,
            ),
        )
        db.libraryDao().upsertSource(
            TrackSourceEntity(
                id = sourceId,
                trackId = id.toString(),
                type = TrackSourceType.LOCAL_MEDIASTORE,
                availability = SourceAvailability.AVAILABLE_LOCAL,
                contentUri = "content://merge/$sourceId",
                localPath = null,
                mimeType = "audio/mpeg",
                fileSizeBytes = 1000L,
                contentHashSha256 = DUPLICATE_HASH,
                trainingEligible = true,
                createdAtEpochMs = 1L,
                lastVerifiedAtEpochMs = 1L,
            ),
        )
    }

    private companion object {
        const val SMART_ID = "33333333-3333-3333-3333-333333333333"
        const val DUPLICATE_HASH = "same-content"
    }
}

private class FixedMergeClock(private val value: Long) : RulePlaylistClock() {
    override fun millis(): Long = value
}

private class MergeTestExtractor : AudioFeatureExtractor {
    override val extractorName = NAME
    override val extractorVersion = VERSION
    override val schemaVersion = SCHEMA
    override suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? = null

    companion object {
        const val NAME = "merge-test"
        const val VERSION = "1"
        const val SCHEMA = 1
    }
}

private class MergeNoOpModel : PersonalizationModel {
    private var current = PersonalizationModelState()
    override suspend fun state(): PersonalizationModelState = current
    override suspend fun score(context: RecommendationContext, candidates: List<TrackFeatures>): List<ModelTrackScore> = emptyList()
    override suspend fun scoreBatch(features: Map<UUID, DoubleArray>): Map<UUID, Double> = emptyMap()
    override suspend fun update(samples: List<TrainingSample>) = Unit
    override suspend fun rebuild(samples: List<TrainingSample>) = Unit
    override suspend fun reset(trainingDataVersion: Long) {
        current = PersonalizationModelState(trainingDataVersion = trainingDataVersion, historyFloorVersion = trainingDataVersion)
    }
}
