package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.data.db.*
import dev.behradhz.meowzix.domain.history.*
import dev.behradhz.meowzix.domain.recommendation.*
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TrainingDatasetBuilder @Inject constructor(
    private val historyDao: HistoryDao,
    private val libraryDao: LibraryDao,
    private val toolsDao: LibraryToolsDao,
    private val audioFeatureDao: AudioFeatureDao,
    private val audioFeatureExtractor: AudioFeatureExtractor,
    private val sampleDao: TrainingSampleDao,
) {
    /** Full causal replay is off the playback/UI path. Persisted samples are derived and versioned. */
    suspend fun buildAll(floor: Long = 0L, after: Long = 0L, latestFirst: Boolean = true,
        outcomeLimit: Int = RecommendationConfig.MAX_REBUILD_OUTCOMES): List<TrainingSample> = withContext(Dispatchers.Default) {
        require(outcomeLimit in 1..RecommendationConfig.MAX_REBUILD_OUTCOMES)
        // A finite event boundary keeps replay reproducible while playback continues appending events.
        val through = historyDao.latestEventSequence()
        val canonicalAliases = toolsDao.activeMergeJournal().associate { row ->
            UUID.fromString(row.mergedTrackId) to UUID.fromString(row.survivorTrackId)
        }
        fun canonicalTrackId(id: UUID): UUID = canonicalAliases[id] ?: id

        // Hidden merged rows remain in Room so an in-flight Media3 item never loses its FK target,
        // but they are not separate learning identities. All post-merge events are canonicalized below.
        val tracks = libraryDao.allTracks().asSequence().filterNot { it.hidden }.associate { row ->
            val id = UUID.fromString(row.id)
            id to TrainingTrack(id, row.normalizedArtist, row.album, row.durationMs)
        }
        val audio = audioFeatureDao.compatibleVectors(audioFeatureExtractor.extractorName,
            audioFeatureExtractor.extractorVersion, audioFeatureExtractor.schemaVersion).mapNotNull { row ->
            val values = try { AudioFeatureVectorCodec.decode(row.vectorBlob, AudioVectorFormat.valueOf(row.vectorFormat)) } catch (_: Exception) { null }
            values?.takeIf(AudioFeatureSchema::isCompatible)?.let {
                canonicalTrackId(UUID.fromString(row.trackId)) to TrainingAudio(it, Instant.ofEpochMilli(row.generatedAtEpochMs),
                    UUID.fromString(row.sourceIdUsed), row.extractorVersion)
            }
        }.toMap()
        val samples = ArrayList<TrainingSample>()
        var outcomeCursor = maxOf(floor, after)
        do {
            currentCoroutineContext().ensureActive()
            val outcomes = historyDao.trainingOutcomes(outcomeCursor, floor, outcomeLimit, latestFirst, through)
            if (outcomes.isEmpty()) break
            val eligible = outcomes.mapTo(hashSetOf()) { UUID.fromString(it.playbackInstanceId) }
            val accumulator = CausalTrainingDataset.Accumulator(tracks, audio, eligible, floor)
            val batchThrough = outcomes.maxOf { it.sequence }
            var cursor = floor
            while (cursor < batchThrough) {
                currentCoroutineContext().ensureActive()
                val page = historyDao.trainingEventPage(cursor, RecommendationConfig.EVENT_PAGE_SIZE, batchThrough)
                if (page.isEmpty()) break
                page.mapNotNull { row ->
                    try {
                        val event = row.event
                        TrainingEvent(row.sequence, ListeningEvent(
                            UUID.fromString(event.id), canonicalTrackId(UUID.fromString(event.trackId)), ListeningEventType.valueOf(event.type),
                            Instant.ofEpochMilli(event.occurredAtEpochMs), event.localHour, DayOfWeek.of(event.dayOfWeek),
                            TimeBucket.valueOf(event.timeBucket), event.positionMs, event.durationMs, event.completionRatio,
                            dev.behradhz.meowzix.domain.history.PlaybackInitiator.valueOf(event.initiatedBy),
                            dev.behradhz.meowzix.domain.playback.PlaybackMode.valueOf(event.playbackMode),
                            UUID.fromString(event.playbackInstanceId), UUID.fromString(event.sessionId),
                        ), UUID.fromString(event.sessionId), UUID.fromString(event.playbackInstanceId))
                    } catch (_: IllegalArgumentException) { null }
                }.forEach(accumulator::accept)
                cursor = page.last().sequence
            }
            samples.addAll(accumulator.samples().take(outcomeLimit - samples.size))
            outcomeCursor = batchThrough
            // A large neutral prefix must not starve later meaningful feedback. Sparse batches replay
            // their causal past again, retaining bounded vectors and cancellation between pages.
            if (latestFirst || outcomes.size < outcomeLimit) break
        } while (samples.size < minOf(RecommendationConfig.UPDATE_BATCH, outcomeLimit))
        samples
    }

    suspend fun materialize(floor: Long, after: Long = 0L, latestFirst: Boolean = true): List<TrainingSample> {
        val samples = buildAll(floor, after, latestFirst)
        val now = System.currentTimeMillis()
        // Chunk writes keep transactions bounded on large historical backfills.
        samples.chunked(250).forEach { chunk ->
            sampleDao.put(chunk.map { sample -> TrainingSampleEntity(
                sample.playbackInstanceId.toString(), sample.trackId.toString(), AudioFeatureVectorCodec.encode(sample.features),
                sample.reward, sample.weight, sample.dataVersion, sample.featureSchemaVersion, sample.rewardSchemaVersion,
                now, sample.eventIds.joinToString(","), sample.sourceIdUsedForAudio?.toString(), sample.audioExtractorVersion,
            ) })
        }
        return samples
    }

    suspend fun sampleForPlayback(playbackInstanceId: UUID): TrainingSample? = buildAll().firstOrNull { it.playbackInstanceId == playbackInstanceId }
    suspend fun latestDataVersion(): Long = historyDao.latestOutcomeVersion()
    suspend fun latestEventSequence(): Long = historyDao.latestEventSequence()
    suspend fun rebuildPreferenceStats(floor: Long) = historyDao.rebuildPreferenceStats(floor)
    suspend fun clearPreferenceStats() = historyDao.clearPreferenceStats()
    /** Cheap bounded gate: fewer than ten terminal outcomes cannot contain ten meaningful samples. */
    suspend fun hasPendingBatch(floor: Long, after: Long): Boolean = historyDao.trainingOutcomes(
        maxOf(floor, after), floor, RecommendationConfig.UPDATE_BATCH, latestFirst = false,
    ).size >= RecommendationConfig.UPDATE_BATCH
    suspend fun clearDerivedSamples() = sampleDao.clear()
}