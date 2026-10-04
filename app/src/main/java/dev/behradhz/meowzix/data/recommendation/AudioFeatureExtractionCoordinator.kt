package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.*
import dev.behradhz.meowzix.domain.downloads.ManagedStorageRepository
import dev.behradhz.meowzix.domain.recommendation.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Source adapters supply bytes; extraction never requests network/TDLib work or duplicates audio. */
@Singleton
class AudioFeatureExtractionCoordinator @Inject constructor(
    private val libraryDao: LibraryDao,
    private val audioFeatureDao: AudioFeatureDao,
    private val extractor: AudioFeatureExtractor,
    private val scheduler: RecommendationWorkScheduler,
    private val managedStorage: ManagedStorageRepository,
    @param:ApplicationContext private val context: Context,
) {
    private val gate = Mutex()
    fun schedule(trackIds: Collection<UUID>) = scheduler.scheduleAudio(trackIds)
    fun schedule(trackId: UUID) = schedule(listOf(trackId))
    suspend fun scheduleBulk() {
        if (managedStorage.usage().lowSpace) return
        scheduler.scheduleAudio(libraryDao.allTracks().map { UUID.fromString(it.id) }, bulk = true)
    }

    suspend fun extractIfNeeded(trackId: UUID) = withContext(Dispatchers.IO) {
        // Analysis is opportunistic. Low disk space must preserve playback/download headroom and
        // recovery is automatic: a later scheduled extraction rechecks current storage state.
        if (managedStorage.usage().lowSpace) return@withContext
        gate.withLock {
            if (managedStorage.usage().lowSpace) return@withLock
            val sources = libraryDao.sourcesForTrack(trackId.toString()).filter {
                it.trainingEligible && it.availability == SourceAvailability.AVAILABLE_LOCAL &&
                    (!it.contentUri.isNullOrBlank() || !it.localPath.isNullOrBlank())
            }.sortedBy { sourcePriority(it.type) }
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                val hash = try { contentHash(source) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } ?: continue
                libraryDao.updateContentHash(source.id, hash)
                val existing = audioFeatureDao.compatibleVector(trackId.toString(), extractor.extractorName, extractor.extractorVersion, extractor.schemaVersion)
                if (existing?.sourceContentHash == hash && existing.valid()) return@withLock
                val shared = audioFeatureDao.matchingContent(hash, extractor.extractorName, extractor.extractorVersion, extractor.schemaVersion)
                if (shared != null && shared.valid()) {
                    audioFeatureDao.put(shared.copy(id = UUID.randomUUID().toString(), trackId = trackId.toString(),
                        sourceIdUsed = source.id, generatedAtEpochMs = System.currentTimeMillis()))
                    return@withLock
                }
                val vector = try {
                    extractor.extract(trackId, AudioFeatureSource(UUID.fromString(source.id), source.contentUri,
                        source.localPath, source.mimeType, source.fileSizeBytes, hash))
                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } ?: continue
                if (!AudioFeatureSchema.isCompatible(vector.values)) continue
                val afterHash = try { contentHash(source) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
                if (afterHash != hash) continue
                audioFeatureDao.put(AudioFeatureVectorEntity(vector.id.toString(), trackId.toString(), source.id,
                    vector.extractorName, vector.extractorVersion, vector.schemaVersion, vector.vectorFormat.name,
                    AudioFeatureVectorCodec.encode(vector.values), vector.generatedAt.toEpochMilli(), hash))
                return@withLock
            }
        }
    }

    private suspend fun contentHash(source: TrackSourceEntity): String? {
        val stream = if (!source.contentUri.isNullOrBlank()) context.contentResolver.openInputStream(Uri.parse(source.contentUri))
            else source.localPath?.let { File(it).inputStream() }
        return stream?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
    private fun AudioFeatureVectorEntity.valid(): Boolean = try {
        AudioFeatureSchema.isCompatible(AudioFeatureVectorCodec.decode(vectorBlob, AudioVectorFormat.valueOf(vectorFormat)))
    } catch (_: Exception) { false }
    private fun sourcePriority(type: TrackSourceType) = when (type) {
        TrackSourceType.LOCAL_MEDIASTORE -> 0
        TrackSourceType.APP_OFFLINE_COPY -> 1
        TrackSourceType.TDLIB_LOCAL -> 2
        TrackSourceType.TELEGRAM_REMOTE -> 3
    }
}
