package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.data.db.AudioFeatureDao
import dev.behradhz.meowzix.data.db.AudioFeatureVectorEntity
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.ListeningEventEntity
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureVectorCodec
import dev.behradhz.meowzix.domain.recommendation.AudioVectorFormat
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
    private val audioFeatureDao: AudioFeatureDao,
    private val audioFeatureExtractor: AudioFeatureExtractor,
) {
    suspend fun buildAll(): List<TrainingSample> {
        val tracks = libraryDao.allTracks().associateBy { it.id }
        val audio = compatibleAudioFeatures()
        return historyDao.allEventsChronological()
            .groupBy { it.playbackInstanceId }
            .values
            .mapNotNull { events ->
                val trackId = events.firstOrNull()?.trackId
                sample(events, tracks[trackId], trackId?.let(audio::get))
            }
            .sortedBy { it.dataVersion }
    }

    suspend fun sampleForPlayback(playbackInstanceId: UUID): TrainingSample? {
        val events = historyDao.eventsForPlayback(playbackInstanceId.toString())
        val trackId = events.firstOrNull()?.trackId ?: return null
        val audio = audioFeatureDao.compatibleVector(
            trackId,
            audioFeatureExtractor.extractorName,
            audioFeatureExtractor.extractorVersion,
            audioFeatureExtractor.schemaVersion,
        )?.decodeValues()
        return sample(events, libraryDao.trackById(trackId), audio)
    }

    /**
     * Behavioral outcome time remains the training watermark. Audio extraction time is deliberately
     * excluded so extracting a feature for old history after Reset personalization cannot resurrect
     * that pre-reset behavioral data.
     */
    suspend fun latestDataVersion(): Long = historyDao.latestOutcomeVersion()

    private suspend fun compatibleAudioFeatures(): Map<String, DoubleArray> =
        audioFeatureDao.compatibleVectors(
            audioFeatureExtractor.extractorName,
            audioFeatureExtractor.extractorVersion,
            audioFeatureExtractor.schemaVersion,
        ).mapNotNull { row -> row.decodeValues()?.let { row.trackId to it } }.toMap()

    private fun sample(
        events: List<ListeningEventEntity>,
        track: TrackEntity?,
        audioFeatures: DoubleArray?,
    ): TrainingSample? {
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
            audioFeatures = audioFeatures,
        )
        return TrainingSample(
            trackId = trackFeatures.trackId,
            features = PersonalizationFeatureVectorizer.vectorize(context, trackFeatures),
            reward = PersonalizationRewardBuilder.reward(types),
            weight = 1.0,
            dataVersion = outcome.occurredAtEpochMs,
        )
    }

    private fun AudioFeatureVectorEntity.decodeValues(): DoubleArray? = runCatching {
        AudioFeatureVectorCodec.decode(vectorBlob, AudioVectorFormat.valueOf(vectorFormat))
    }.getOrNull()

    private companion object {
        val FINAL_OUTCOMES = setOf("PLAY_COMPLETED", "PLAY_STOPPED", "SKIPPED_EARLY", "SKIPPED_LATE")
    }
}
