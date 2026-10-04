package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.*
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationBus
import dev.behradhz.meowzix.domain.recommendation.*
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRecommendationPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MeowzixDatabase
    private lateinit var model: LocalLinearPersonalizationModel
    private val trackId = UUID.randomUUID()
    private lateinit var directory: File
    private lateinit var preferenceScope: CoroutineScope
    private lateinit var preferences: DataStore<Preferences>
    private val artifact get() = File(directory, "model.bin")
    private fun newModel() = LocalLinearPersonalizationModel(db.historyDao(), preferences, AtomicFile(artifact))
    private val features get() = PersonalizationFeatureVectorizer.vectorize(RecommendationContext(23, 4, false, dev.behradhz.meowzix.domain.history.TimeBucket.NIGHT), TrackPersonalizationFeatures(trackId, "artist", false, 200_000))
    @Before fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        directory = File(context.cacheDir, "recommendation-test-${UUID.randomUUID()}").apply { mkdirs() }
        preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = PreferenceDataStoreFactory.create(scope = preferenceScope, produceFile = { File(directory, "model.preferences_pb") })
        model = newModel()
        model.reset(0)
        db.libraryDao().upsertTrack(TrackEntity(trackId.toString(), "Track", "track", "Artist", "artist", "Album", 200_000, null, null, null, false, false, 1, 1))
    }
    @After fun cleanup() { preferenceScope.cancel(); directory.deleteRecursively(); db.close() }
    private fun samples(n: Int) = (1..n).map { TrainingSample(trackId, features, 0.95, dataVersion = it.toLong(), playbackInstanceId = UUID(0, it.toLong())) }
    @Test fun modelSurvivesReloadAndDoesNotDoubleCountSamples() = runTest {
        model.update(samples(100))
        model.update(samples(100))
        val reloaded = newModel()
        assertEquals(100L, reloaded.state().sampleCount)
        assertTrue(reloaded.state().active)
        assertEquals(model.scoreBatch(mapOf(trackId to features)), reloaded.scoreBatch(mapOf(trackId to features)))
        assertNotNull(reloaded.state().artifactChecksum)
    }
    @Test fun corruptionFallsBackAndRetainsResetFloor() = runTest {
        model.reset(50)
        model.update(samples(150))
        artifact.writeBytes(byteArrayOf(1, 2, 3))
        val reloaded = newModel()
        assertFalse(reloaded.state().active)
        assertTrue(reloaded.state().requiresRebuild)
        assertEquals(50L, reloaded.state().historyFloorVersion)
        assertTrue(reloaded.scoreBatch(mapOf(trackId to features)).isEmpty())
    }
    @Test fun resetLeavesCanonicalMusicAndStopsOldSamplesRelearning() = runTest {
        model.update(samples(100))
        model.reset(100)
        model.rebuild(samples(100))
        assertFalse(model.state().active)
        assertEquals(0L, model.state().sampleCount)
        assertNotNull(db.libraryDao().trackById(trackId.toString()))
    }
    @Test fun sourceMultiplicityDoesNotDuplicateBehavioralTraining() = runTest {
        db.libraryDao().upsertSource(source(TrackSourceType.LOCAL_MEDIASTORE, SourceAvailability.AVAILABLE_LOCAL))
        db.libraryDao().upsertSource(source(TrackSourceType.TELEGRAM_REMOTE, SourceAvailability.REMOTE_ONLY))
        val session = UUID.randomUUID().toString()
        val occurrence = UUID.randomUUID().toString()
        db.historyDao().upsertSession(ListeningSessionEntity(session, 1, null, "ORDERED"))
        listOf("MANUAL_SELECTED", "PLAY_STARTED", "PLAY_COMPLETED").forEach { type ->
            db.historyDao().insertEvent(ListeningEventEntity(UUID.randomUUID().toString(), occurrence, trackId.toString(), session,
                type, 1000, 23, 4, "NIGHT", false, 190_000, 200_000, 0.95, "USER", "ORDERED"))
        }
        val builder = TrainingDatasetBuilder(db.historyDao(), db.libraryDao(), db.audioFeatureDao(), TestExtractor(), db.trainingSampleDao())
        val dataset = builder.materialize(0)
        assertEquals(1, dataset.size)
        assertEquals(trackId, dataset.single().trackId)
        assertEquals(1, db.trainingSampleDao().samplesAfter(0, PersonalizationFeatureVectorizer.SCHEMA_VERSION, PersonalizationRewardBuilder.VERSION).size)
        db.libraryDao().sourcesForTrack(trackId.toString()).forEach { db.libraryDao().upsertSource(it.copy(type = TrackSourceType.TELEGRAM_REMOTE, availability = SourceAvailability.REMOTE_ONLY)) }
        assertArrayEquals(dataset.single().features, builder.buildAll().single().features, 0.0)
    }
    @Test fun incrementalTrainingWaitsForTenMeaningfulOutcomes() = runTest {
        val builder = TrainingDatasetBuilder(db.historyDao(), db.libraryDao(), db.audioFeatureDao(), TestExtractor(), db.trainingSampleDao())
        val trainer = PersonalizationTrainer(builder, model, object : TrainingScheduler {
            override fun scheduleTraining(rebuild: Boolean) {}
        }, SmartQueueAdaptationBus())
        val session = UUID.randomUUID().toString()
        db.historyDao().upsertSession(ListeningSessionEntity(session, 1, null, "ORDERED"))
        suspend fun outcome() {
            val instance = UUID.randomUUID().toString()
            listOf("AUTO_SELECTED", "PLAY_STARTED", "PLAY_COMPLETED").forEach { type ->
                db.historyDao().insertEvent(ListeningEventEntity(UUID.randomUUID().toString(), instance, trackId.toString(), session,
                    type, 1000, 23, 4, "NIGHT", false, 190_000, 200_000, 0.95, "QUEUE", "ORDERED"))
            }
        }
        repeat(9) { outcome() }
        trainer.trainPending()
        assertEquals(0L, model.state().sampleCount)
        assertTrue(db.trainingSampleDao().samplesAfter(0, PersonalizationFeatureVectorizer.SCHEMA_VERSION, PersonalizationRewardBuilder.VERSION).isEmpty())
        outcome()
        trainer.trainPending()
        assertEquals(10L, model.state().sampleCount)
        trainer.trainPending()
        assertEquals(10L, model.state().sampleCount)
    }
    private suspend fun appendOutcome(terminal: String = "PLAY_COMPLETED"): Long {
        val session = UUID.randomUUID().toString()
        val instance = UUID.randomUUID().toString()
        db.historyDao().upsertSession(ListeningSessionEntity(session, 1, null, "ORDERED"))
        listOf("AUTO_SELECTED", "PLAY_STARTED", terminal).forEach { type ->
            db.historyDao().insertEvent(ListeningEventEntity(UUID.randomUUID().toString(), instance, trackId.toString(), session,
                type, 1000, 23, 4, "NIGHT", false, if (terminal == "PLAY_COMPLETED") 190_000L else 1_000L,
                200_000L, if (terminal == "PLAY_COMPLETED") 0.95 else 0.005, "QUEUE", "ORDERED"))
        }
        return db.historyDao().latestOutcomeVersion()
    }
    @Test fun incrementalBatchesProcessOldestFeedbackBeforeAdvancingTheWatermark() = runTest {
        val versions = (1..5).map { appendOutcome() }
        val builder = TrainingDatasetBuilder(db.historyDao(), db.libraryDao(), db.audioFeatureDao(), TestExtractor(), db.trainingSampleDao())
        val first = builder.buildAll(latestFirst = false, outcomeLimit = 2)
        val second = builder.buildAll(after = first.last().dataVersion, latestFirst = false, outcomeLimit = 2)
        assertEquals(versions.take(2), first.map { it.dataVersion })
        assertEquals(versions.drop(2).take(2), second.map { it.dataVersion })
        assertEquals(versions.takeLast(2), builder.buildAll(outcomeLimit = 2).map { it.dataVersion })
    }
    @Test fun aNeutralPrefixCannotStarveLaterMeaningfulFeedback() = runTest {
        repeat(5) { appendOutcome("PLAY_STOPPED") }
        val first = appendOutcome()
        val second = appendOutcome()
        val builder = TrainingDatasetBuilder(db.historyDao(), db.libraryDao(), db.audioFeatureDao(), TestExtractor(), db.trainingSampleDao())
        val samples = builder.buildAll(latestFirst = false, outcomeLimit = 2)
        assertEquals(listOf(first, second), samples.map { it.dataVersion })
        assertTrue(samples.all { it.reward > 0.0 })
    }
    @Test fun outcomeSelectionAndEventPagesRespectAFrozenSequenceBoundary() = runTest {
        val boundary = appendOutcome()
        appendOutcome()
        val selected = db.historyDao().trainingOutcomes(0, 0, 10, latestFirst = false, through = boundary)
        assertEquals(listOf(boundary), selected.map { it.sequence })
        val events = db.historyDao().trainingEventPage(0, 100, through = boundary)
        assertEquals(3, events.size)
        assertTrue(events.all { it.sequence <= boundary })
    }
    @Test fun explicitRebuildRestoresTheStatisticsUsedByRankingAfterReset() = runTest {
        appendOutcome()
        appendOutcome()
        val builder = TrainingDatasetBuilder(db.historyDao(), db.libraryDao(), db.audioFeatureDao(), TestExtractor(), db.trainingSampleDao())
        val trainer = PersonalizationTrainer(builder, model, object : TrainingScheduler {
            override fun scheduleTraining(rebuild: Boolean) {}
        }, SmartQueueAdaptationBus())
        trainer.resetLearningKeepHistory()
        db.historyDao().clearTrackStats()
        db.historyDao().clearTimeStats()
        trainer.rebuildFromStoredHistory()
        trainer.trainPending(forceRebuild = true)
        assertEquals(2L, model.state().sampleCount)
        assertEquals(2, db.historyDao().trackStats(trackId.toString())!!.totalStarts)
        assertEquals(2, db.historyDao().trackStats(trackId.toString())!!.totalCompletions)
        assertEquals(2, db.historyDao().timeStats(trackId.toString(), "NIGHT")!!.completions)
    }
    @Test fun aggregateRebuildCannotCrossThePrivacyResetFloor() = runTest {
        val floor = appendOutcome()
        appendOutcome()
        db.historyDao().rebuildPreferenceStats(floor)
        assertEquals(1, db.historyDao().trackStats(trackId.toString())!!.totalStarts)
        assertEquals(1, db.historyDao().trackStats(trackId.toString())!!.totalCompletions)
        assertEquals(1, db.historyDao().timeStats(trackId.toString(), "NIGHT")!!.starts)
    }
    @Test fun aggregateRebuildAttributesAnOutcomeToItsOriginalDecisionBucket() = runTest {
        appendOutcome()
        db.openHelper.writableDatabase.execSQL("UPDATE listening_events SET timeBucket = 'MORNING' WHERE type IN ('AUTO_SELECTED', 'PLAY_STARTED')")
        db.historyDao().rebuildPreferenceStats(0)
        assertEquals(1, db.historyDao().timeStats(trackId.toString(), "MORNING")!!.completions)
        assertNull(db.historyDao().timeStats(trackId.toString(), "NIGHT"))
    }
    @Test fun resetWaitsForARebuildThenClearsBothLearnedAndAggregateState() = runTest {
        appendOutcome()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val slowModel = object : PersonalizationModel by model {
            override suspend fun rebuild(samples: List<TrainingSample>) {
                entered.complete(Unit)
                release.await()
                model.rebuild(samples)
            }
        }
        val builder = TrainingDatasetBuilder(db.historyDao(), db.libraryDao(), db.audioFeatureDao(), TestExtractor(), db.trainingSampleDao())
        val trainer = PersonalizationTrainer(builder, slowModel, object : TrainingScheduler {
            override fun scheduleTraining(rebuild: Boolean) {}
        }, SmartQueueAdaptationBus())
        val rebuilding = launch { trainer.trainPending(forceRebuild = true) }
        entered.await()
        val resetting = launch { trainer.resetLearningKeepHistory() }
        release.complete(Unit)
        rebuilding.join()
        resetting.join()
        assertEquals(0L, model.state().sampleCount)
        assertEquals(db.historyDao().latestEventSequence(), model.state().historyFloorVersion)
        assertNull(db.historyDao().trackStats(trackId.toString()))
        assertNull(db.historyDao().timeStats(trackId.toString(), "NIGHT"))
    }
    @Test fun eventClockSurvivesEventDeletionAndNeverReusesWatermarks() = runTest {
        val session = UUID.randomUUID().toString()
        db.historyDao().upsertSession(ListeningSessionEntity(session, 1, null, "ORDERED"))
        fun event() = ListeningEventEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(), trackId.toString(), session,
            "QUEUE_REMOVED", 1000, 23, 4, "NIGHT", false, null, null, null, "USER", "ORDERED")
        db.historyDao().insertEvent(event())
        val before = db.historyDao().latestEventSequence()
        db.historyDao().clearEvents()
        db.historyDao().insertEvent(event())
        assertTrue(db.historyDao().sequencedEvents().single().sequence > before)
    }
    private fun source(type: TrackSourceType, availability: SourceAvailability) = TrackSourceEntity(UUID.randomUUID().toString(), trackId.toString(), type, availability, null, null, "audio/mpeg", 123, null, true, 1, 1)
}

internal class TestExtractor : AudioFeatureExtractor {
    var calls = 0
    var failure: Exception? = null
    val sourceFailures = mutableMapOf<UUID, Exception>()
    var usedSource: UUID? = null
    override val extractorName = "test-pcm"
    override val extractorVersion = "2"
    override val schemaVersion = AudioFeatureSchema.VERSION
    override suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector {
        calls++
        usedSource = source.sourceId
        failure?.let { throw it }
        sourceFailures[source.sourceId]?.let { throw it }
        return AudioFeatureVector(UUID.randomUUID(), trackId, source.sourceId, extractorName, extractorVersion, schemaVersion,
            AudioVectorFormat.FLOAT64_LE, DoubleArray(AudioFeatureSchema.names.size) { 0.25 }, Instant.now(), source.contentHashSha256)
    }
}
