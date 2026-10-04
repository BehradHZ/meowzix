package dev.behradhz.meowzix.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.recommendation.PersonalizationTrainer
import dev.behradhz.meowzix.data.recommendation.TrainingDatasetBuilder
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.PlaybackInitiator
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationBus
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureSource
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureVector
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModelState
import dev.behradhz.meowzix.domain.recommendation.TrainingSample
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomListeningHistoryRepositoryTest {
    private lateinit var database: MeowzixDatabase
    private lateinit var repository: RoomListeningHistoryRepository
    private val trackId = UUID.randomUUID()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        val extractor = NoOpAudioFeatureExtractor()
        val trainer = PersonalizationTrainer(
            datasetBuilder = TrainingDatasetBuilder(
                historyDao = database.historyDao(),
                libraryDao = database.libraryDao(),
                audioFeatureDao = database.audioFeatureDao(),
                audioFeatureExtractor = extractor,
                sampleDao = database.trainingSampleDao(),
            ),
            model = NoOpPersonalizationModel(),
            scheduler = object : dev.behradhz.meowzix.domain.recommendation.TrainingScheduler { override fun scheduleTraining(rebuild: Boolean) {} },
            adaptationBus = SmartQueueAdaptationBus(),
        )
        repository = RoomListeningHistoryRepository(database, database.historyDao(), trainer, dev.behradhz.meowzix.data.settings.DataStoreSettingsRepository(context))
    }

    @After fun tearDown() = database.close()

    @Test fun manualPlaybackLogsOnceAndClearRemovesHistory() = runTest {
        database.libraryDao().upsertTrack(track())
        val playbackId = repository.startPlayback(trackId, PlaybackInitiator.USER, PlaybackMode.ORDERED)
        repository.finalizePlayback(playbackId, 95_000, 100_000, false)
        repository.finalizePlayback(playbackId, 95_000, 100_000, false)

        val events = repository.observeEvents().first()
        assertEquals(1, events.count { it.type == ListeningEventType.MANUAL_SELECTED })
        assertEquals(1, events.count { it.type == ListeningEventType.PLAY_COMPLETED })
        assertEquals(1, repository.observeTrackStats().first().single().totalCompletions)

        repository.clear()
        assertEquals(emptyList<Any>(), repository.observeEvents().first())
    }

    @Test fun repeatedIntentionalSeeksAreStoredWithoutCreatingDuplicateOutcomes() = runTest {
        database.libraryDao().upsertTrack(track())
        val id = repository.startPlayback(trackId, PlaybackInitiator.USER, PlaybackMode.ORDERED)
        repository.recordSeek(id, 50_000, 100_000)
        repository.recordSeek(id, 10_000, 100_000)
        repository.finalizePlayback(id, 10_000, 100_000, true)
        repository.finalizePlayback(id, 95_000, 100_000, false)
        val events = repository.observeEvents().first()
        assertEquals(2, events.count { it.type == ListeningEventType.SEEKED })
        assertEquals(1, events.count { it.type == ListeningEventType.SKIPPED_EARLY })
        assertEquals(0, events.count { it.type == ListeningEventType.PLAY_COMPLETED })
        assertEquals(1, events.mapNotNull { it.playbackInstanceId }.distinct().size)
    }
    @Test fun manualOverrideKeepsOriginalModeAndIsNeutralForRewards() = runTest {
        database.libraryDao().upsertTrack(track())
        val id = repository.startPlayback(trackId, PlaybackInitiator.SMART_SHUFFLE, PlaybackMode.SMART_SHUFFLE)
        repository.finalizePlayback(id, 95_000, 100_000, false)
        repository.recordQueueOverride(id)
        val override = repository.observeEvents().first().single { it.type == ListeningEventType.QUEUE_OVERRIDDEN }
        assertEquals(PlaybackMode.SMART_SHUFFLE, override.playbackMode)
        assertEquals(id, override.playbackInstanceId)
        assertEquals(0.60, dev.behradhz.meowzix.domain.recommendation.PersonalizationRewardBuilder.reward(setOf("AUTO_SELECTED", "PLAY_COMPLETED", "QUEUE_OVERRIDDEN")), 0.0)
    }
    private fun track() = TrackEntity(
        trackId.toString(), "Track", "track", null, null, null, 100_000,
        null, null, null, false, false, 1, 1,
    )
}

private class NoOpAudioFeatureExtractor : AudioFeatureExtractor {
    override val extractorName: String = "test"
    override val extractorVersion: String = "1"
    override val schemaVersion: Int = 1

    override suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? = null
}

private class NoOpPersonalizationModel : PersonalizationModel {
    private var current = PersonalizationModelState()

    override suspend fun state(): PersonalizationModelState = current

    override suspend fun score(context: dev.behradhz.meowzix.domain.recommendation.RecommendationContext, candidates: List<dev.behradhz.meowzix.domain.recommendation.TrackFeatures>): List<dev.behradhz.meowzix.domain.recommendation.ModelTrackScore> = emptyList()

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
