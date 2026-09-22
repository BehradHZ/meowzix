package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.AudioFeatureDao
import dev.behradhz.meowzix.data.db.AudioFeatureVectorEntity
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureSource
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureVectorCodec
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Opportunistic background feature work. It never waits on remote bytes and never blocks queue
 * generation. A future WorkManager wrapper can call the same scheduling entry points for deferred
 * charging/idle batches without changing extraction or persistence semantics.
 */
@Singleton
class AudioFeatureExtractionCoordinator @Inject constructor(
    private val libraryDao: LibraryDao,
    private val audioFeatureDao: AudioFeatureDao,
    private val extractor: AudioFeatureExtractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(1)
    private val inFlight = Collections.synchronizedSet(mutableSetOf<UUID>())

    fun schedule(trackIds: Collection<UUID>) {
        trackIds.distinct().forEach(::schedule)
    }

    fun schedule(trackId: UUID) {
        if (!inFlight.add(trackId)) return
        scope.launch {
            try {
                gate.withPermit { extractIfNeeded(trackId) }
            } finally {
                inFlight.remove(trackId)
            }
        }
    }

    private suspend fun extractIfNeeded(trackId: UUID) {
        val source = chooseReadableSource(libraryDao.sourcesForTrack(trackId.toString())) ?: return
        val existing = audioFeatureDao.compatibleVector(
            trackId = trackId.toString(),
            extractorName = extractor.extractorName,
            extractorVersion = extractor.extractorVersion,
            schemaVersion = extractor.schemaVersion,
        )
        if (existing != null && existing.matches(source)) return

        val feature = extractor.extract(
            trackId,
            AudioFeatureSource(
                sourceId = UUID.fromString(source.id),
                contentUri = source.contentUri,
                localPath = source.localPath,
                mimeType = source.mimeType,
                fileSizeBytes = source.fileSizeBytes,
                contentHashSha256 = source.contentHashSha256,
            ),
        ) ?: return

        audioFeatureDao.put(
            AudioFeatureVectorEntity(
                id = feature.id.toString(),
                trackId = feature.trackId.toString(),
                sourceIdUsed = feature.sourceIdUsed.toString(),
                extractorName = feature.extractorName,
                extractorVersion = feature.extractorVersion,
                schemaVersion = feature.schemaVersion,
                vectorFormat = feature.vectorFormat.name,
                vectorBlob = AudioFeatureVectorCodec.encode(feature.values, feature.vectorFormat),
                generatedAtEpochMs = feature.generatedAt.toEpochMilli(),
                sourceContentHash = feature.sourceContentHash,
            ),
        )
    }

    private fun chooseReadableSource(sources: List<TrackSourceEntity>): TrackSourceEntity? =
        sources.asSequence()
            .filter { it.trainingEligible }
            .filter { it.availability == SourceAvailability.AVAILABLE_LOCAL }
            .filter { !it.contentUri.isNullOrBlank() || !it.localPath.isNullOrBlank() }
            .sortedBy { sourcePriority(it.type) }
            .firstOrNull()

    private fun sourcePriority(type: TrackSourceType): Int = when (type) {
        TrackSourceType.LOCAL_MEDIASTORE -> 0
        TrackSourceType.APP_OFFLINE_COPY -> 1
        TrackSourceType.TDLIB_LOCAL -> 2
        TrackSourceType.TELEGRAM_REMOTE -> 3
    }

    private fun AudioFeatureVectorEntity.matches(source: TrackSourceEntity): Boolean {
        val storedHash = sourceContentHash
        val currentHash = source.contentHashSha256
        return if (!storedHash.isNullOrBlank() && !currentHash.isNullOrBlank()) {
            storedHash == currentHash
        } else {
            sourceIdUsed == source.id
        }
    }
}
