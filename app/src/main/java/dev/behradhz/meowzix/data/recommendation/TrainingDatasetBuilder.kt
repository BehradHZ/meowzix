package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.ListeningEventEntity
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.recommendation.PersonalizationFeatureVectorizer
import dev.behradhz.meowzix.domain.recommendation.PersonalizationRewardBuilder
import dev.behradhz.meowzix.domain.recommendation.RecommendationContext
import dev.behradhz.meowzix.domain.recommendation.TrackPersonalizationFeatures
import dev.behradhz.meowzix.domain.recommendation.TrainingSample
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingDatasetBuilder @Inject constructor(
    private val historyDao: HistoryDao,
    private val libraryDao: LibraryDao,
) {
    suspend fun buildAll(): List<TrainingSample> {
        val tracks = libraryDao.allTracks().associateBy { it.id }
        return historyDao.allEventsChronological()
            .groupBy { it.playbackInstanceId }
            .values
            .mapNotNull { events -> sample(events, tracks[events.firstOrNull()?.trackId]) }
            .sortedBy { it.dataVersion }
    }

    suspend fun sampleForPlayback(playbackInstanceId: UUID): TrainingSample? {
        val events = historyDao.eventsForPlayback(playbackInstanceId.toString())
        val trackId = events.firstOrNull()?.trackId ?: return null
        return sample(events, libraryDao.trackById(trackId))
    }

    suspend fun latestDataVersion(): Long = historyDao.latestOutcomeVersion()

    private fun sample(events: List<ListeningEventEntity>, track: TrackEntity?): TrainingSample? {
        if (events.isEmpty() || track == null) return null
        val outcome = events.lastOrNull { it.type in FINAL_OUTCOMES } ?: return null
        val types = events.mapTo(linkedSetOf()) { it.type }
        val context = RecommendationContext(
            localHour = outcome.localHour,
            dayOfWeek = outcome.dayOfWeek,
            isWeekend = outcome.isWeekend,
            timeBucket = runCatching { TimeBucket.valueOf(outcome.timeBucket) }.getOrNull() ?: return null,
        )
        val trackFeatures = TrackPersonalizationFeatures(
            trackId = UUID.fromString(track.id),
            normalizedArtist = track.normalizedArtist,
            favorite = track.favorite,
            durationMs = track.durationMs,
        )
        return TrainingSample(
            trackId = trackFeatures.trackId,
            features = PersonalizationFeatureVectorizer.vectorize(context, trackFeatures),
            reward = PersonalizationRewardBuilder.reward(types),
            weight = 1.0,
            dataVersion = outcome.occurredAtEpochMs,
        )
    }

    private companion object {
        val FINAL_OUTCOMES = setOf("PLAY_COMPLETED", "PLAY_STOPPED", "SKIPPED_EARLY", "SKIPPED_LATE")
    }
}
