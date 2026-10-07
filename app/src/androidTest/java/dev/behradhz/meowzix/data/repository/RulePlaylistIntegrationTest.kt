package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.ListeningEventEntity
import dev.behradhz.meowzix.data.db.ListeningSessionEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.PlaylistEntity
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.recommendation.DataStoreRecommendationFeedbackRepository
import dev.behradhz.meowzix.data.recommendation.PersonalizationTrainer
import dev.behradhz.meowzix.data.recommendation.TrainingDatasetBuilder
import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import dev.behradhz.meowzix.domain.library.RuleMatchMode
import dev.behradhz.meowzix.domain.library.RulePlaylistDefinition
import dev.behradhz.meowzix.domain.library.RulePlaylistSort
import dev.behradhz.meowzix.domain.library.TrackMetadataOverride
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationBus
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureSource
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureVector
import dev.behradhz.meowzix.domain.recommendation.ModelTrackScore
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModelState
import dev.behradhz.meowzix.domain.recommendation.RecommendationContext
import dev.behradhz.meowzix.domain.recommendation.TrackFeatures
import dev.behradhz.meowzix.domain.recommendation.TrainingSample
import dev.behradhz.meowzix.domain.recommendation.TrainingScheduler
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RulePlaylistIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MeowzixDatabase
    private lateinit var repository: RoomLibraryToolsRepository
    private lateinit var clock: MutableRuleClock

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        val bus = SmartQueueAdaptationBus()
        val feedback = DataStoreRecommendationFeedbackRepository(context, bus)
        feedback.clearAll()
        val extractor = RuleTestAudioFeatureExtractor()
        val trainer = PersonalizationTrainer(
            datasetBuilder = TrainingDatasetBuilder(
                historyDao = db.historyDao(),
                libraryDao = db.libraryDao(),
                toolsDao = db.libraryToolsDao(),
                audioFeatureDao = db.audioFeatureDao(),
                audioFeatureExtractor = extractor,
                sampleDao = db.trainingSampleDao(),
            ),
            model = RuleTestPersonalizationModel(),
            scheduler = object : TrainingScheduler {
                override fun scheduleTraining(rebuild: Boolean) = Unit
            },
            adaptationBus = bus,
        )
        clock = MutableRuleClock(0L)
        repository = RoomLibraryToolsRepository(
            toolsDao = db.libraryToolsDao(),
            libraryDao = db.libraryDao(),
            browseDao = db.libraryBrowseDao(),
            database = db,
            feedbackRepository = feedback,
            personalizationTrainer = trainer,
            searchIndexer = EffectiveTrackSearchIndexer(db, db.libraryDao(), db.libraryToolsDao()),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addedAndNotListenedRulesTransitionAtInjectedClockBoundaries() = runTest {
        val trackId = UUID.fromString("11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val playlistId = UUID.fromString("22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val createdAt = 1_000L
        seedTrack(trackId, "Boundary", "Artist", favorite = false, createdAt = createdAt)
        db.playlistDao().upsertPlaylist(
            PlaylistEntity(playlistId.toString(), "Boundary rules", null, null, createdAt, createdAt),
        )

        repository.saveRulePlaylist(
            definition(
                playlistId,
                listOf(PlaylistRule(RuleKind.ADDED_WITHIN_DAYS, "1")),
                RulePlaylistSort.RECENTLY_ADDED,
            ),
        )
        clock.now = createdAt + DAY_MS - 1L
        assertEquals(listOf(trackId), repository.evaluateRulePlaylist(playlistId).map { it.id })
        clock.now = createdAt + DAY_MS + 1L
        assertEquals(emptyList<UUID>(), repository.evaluateRulePlaylist(playlistId).map { it.id })

        val eventAt = 10_000L
        val sessionId = UUID.fromString("33333333-cccc-cccc-cccc-cccccccccccc").toString()
        db.historyDao().upsertSession(ListeningSessionEntity(sessionId, eventAt, null, "ORDERED"))
        db.historyDao().insertEvent(
            ListeningEventEntity(
                id = UUID.fromString("44444444-dddd-dddd-dddd-dddddddddddd").toString(),
                playbackInstanceId = UUID.fromString("55555555-eeee-eeee-eeee-eeeeeeeeeeee").toString(),
                trackId = trackId.toString(),
                sessionId = sessionId,
                type = "PLAY_COMPLETED",
                occurredAtEpochMs = eventAt,
                localHour = 10,
                dayOfWeek = 3,
                timeBucket = "MORNING",
                isWeekend = false,
                positionMs = 180_000L,
                durationMs = 180_000L,
                completionRatio = 1.0,
                initiatedBy = "USER",
                playbackMode = "ORDERED",
            ),
        )
        repository.saveRulePlaylist(
            definition(
                playlistId,
                listOf(PlaylistRule(RuleKind.NOT_LISTENED_WITHIN_DAYS, "1")),
                RulePlaylistSort.LAST_PLAYED,
            ),
        )
        clock.now = eventAt + DAY_MS - 1L
        assertEquals(emptyList<UUID>(), repository.evaluateRulePlaylist(playlistId).map { it.id })
        clock.now = eventAt + DAY_MS + 1L
        assertEquals(listOf(trackId), repository.evaluateRulePlaylist(playlistId).map { it.id })
    }

    @Test
    fun allAnySortingAndMutableInputsUseCurrentEffectiveLibraryState() = runTest {
        val first = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111")
        val second = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222")
        val playlistId = UUID.fromString("cccccccc-3333-3333-3333-333333333333")
        seedTrack(first, "Zulu", "First Artist", favorite = true, createdAt = 1L)
        seedTrack(second, "Alpha", "Provider Artist", favorite = false, createdAt = 2L)
        db.playlistDao().upsertPlaylist(
            PlaylistEntity(playlistId.toString(), "Mutable rules", null, null, 1L, 1L),
        )
        repository.setMetadataOverride(
            TrackMetadataOverride(
                trackId = second,
                artist = "Edited Artist",
                updatedAtEpochMs = 3L,
            ),
        )

        repository.saveRulePlaylist(
            definition(
                playlistId,
                listOf(
                    PlaylistRule(RuleKind.FAVORITE),
                    PlaylistRule(RuleKind.ARTIST_IS, "edited artist"),
                ),
                RulePlaylistSort.TITLE,
                matchMode = RuleMatchMode.ANY,
            ),
        )
        assertEquals(listOf(second, first), repository.evaluateRulePlaylist(playlistId).map { it.id })

        repository.saveRulePlaylist(
            definition(
                playlistId,
                listOf(
                    PlaylistRule(RuleKind.FAVORITE),
                    PlaylistRule(RuleKind.ARTIST_IS, "edited artist"),
                ),
                RulePlaylistSort.TITLE,
                matchMode = RuleMatchMode.ALL,
            ),
        )
        assertEquals(emptyList<UUID>(), repository.evaluateRulePlaylist(playlistId).map { it.id })

        db.libraryDao().setFavorite(second.toString(), true, 4L)
        assertEquals(listOf(second), repository.evaluateRulePlaylist(playlistId).map { it.id })

        repository.saveRulePlaylist(
            definition(
                playlistId,
                listOf(PlaylistRule(RuleKind.FAVORITE), PlaylistRule(RuleKind.OFFLINE)),
                RulePlaylistSort.TITLE,
            ),
        )
        assertEquals(listOf(second, first), repository.evaluateRulePlaylist(playlistId).map { it.id })

        val secondSource = requireNotNull(db.libraryDao().sourceById("source-$second"))
        db.libraryDao().upsertSource(secondSource.copy(availability = SourceAvailability.MISSING))
        assertEquals(listOf(first), repository.evaluateRulePlaylist(playlistId).map { it.id })

        repository.deleteRulePlaylist(playlistId)
        assertNull(repository.rulePlaylist(playlistId))
        assertEquals(emptyList<UUID>(), repository.evaluateRulePlaylist(playlistId).map { it.id })
    }

    private suspend fun seedTrack(
        id: UUID,
        title: String,
        artist: String,
        favorite: Boolean,
        createdAt: Long,
    ) {
        db.libraryDao().upsertTrack(
            TrackEntity(
                id = id.toString(),
                title = title,
                normalizedTitle = title.lowercase(),
                artist = artist,
                normalizedArtist = artist.lowercase(),
                album = "Album",
                durationMs = 180_000L,
                trackNumber = null,
                year = null,
                artworkRef = null,
                favorite = favorite,
                hidden = false,
                createdAtEpochMs = createdAt,
                updatedAtEpochMs = createdAt,
            ),
        )
        db.libraryDao().upsertSource(
            TrackSourceEntity(
                id = "source-$id",
                trackId = id.toString(),
                type = TrackSourceType.LOCAL_MEDIASTORE,
                availability = SourceAvailability.AVAILABLE_LOCAL,
                contentUri = "content://rule/$id",
                localPath = null,
                mimeType = "audio/mpeg",
                fileSizeBytes = 1_000L,
                contentHashSha256 = null,
                trainingEligible = true,
                createdAtEpochMs = createdAt,
                lastVerifiedAtEpochMs = createdAt,
            ),
        )
    }

    private fun definition(
        playlistId: UUID,
        rules: List<PlaylistRule>,
        sort: RulePlaylistSort,
        matchMode: RuleMatchMode = RuleMatchMode.ALL,
    ) = RulePlaylistDefinition(
        playlistId = playlistId,
        matchMode = matchMode,
        rules = rules,
        sort = sort,
        updatedAtEpochMs = clock.now,
    )

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

private class MutableRuleClock(var now: Long) : RulePlaylistClock() {
    override fun millis(): Long = now
}

private class RuleTestAudioFeatureExtractor : AudioFeatureExtractor {
    override val extractorName: String = "rule-test"
    override val extractorVersion: String = "1"
    override val schemaVersion: Int = 1

    override suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector? = null
}

private class RuleTestPersonalizationModel : PersonalizationModel {
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
