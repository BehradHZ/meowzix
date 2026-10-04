package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.model.*
import dev.behradhz.meowzix.data.db.*
import dev.behradhz.meowzix.domain.downloads.ManagedFileLease
import dev.behradhz.meowzix.domain.downloads.ManagedStorageRepository
import dev.behradhz.meowzix.domain.downloads.ManagedStorageUsage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioFeatureCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MeowzixDatabase
    private lateinit var coordinator: AudioFeatureExtractionCoordinator
    private val extractor = TestExtractor()
    private val files = mutableListOf<File>()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, MeowzixDatabase::class.java).build()
        coordinator = AudioFeatureExtractionCoordinator(
            db.libraryDao(),
            db.audioFeatureDao(),
            extractor,
            RecommendationWorkScheduler(context),
            TestManagedStorageRepository,
            context,
        )
    }
    @After fun cleanup() { files.forEach(File::delete); db.close() }
    private fun file(bytes: ByteArray) = File.createTempFile("meowzix-feature", ".bin", context.cacheDir).also { it.writeBytes(bytes); files += it }
    private suspend fun track(): UUID {
        val id = UUID.randomUUID()
        db.libraryDao().upsertTrack(TrackEntity(id.toString(), "Track", "track", null, null, null, 200_000, null, null, null, false, false, 1, 1))
        return id
    }
    private suspend fun source(trackId: UUID, type: TrackSourceType, path: String): UUID {
        val id = UUID.randomUUID()
        db.libraryDao().upsertSource(TrackSourceEntity(id.toString(), trackId.toString(), type, SourceAvailability.AVAILABLE_LOCAL,
            null, path, "audio/wav", null, null, true, 1, 1))
        return id
    }
    @Test fun unavailablePrioritySourceFallsBackAndContentIsAnalyzedOnlyOnce() = runTest {
        val id = track()
        source(id, TrackSourceType.LOCAL_MEDIASTORE, "/missing/meowzix-test-file")
        val readable = source(id, TrackSourceType.APP_OFFLINE_COPY, file(byteArrayOf(1, 2, 3)).path)
        coordinator.extractIfNeeded(id)
        coordinator.extractIfNeeded(id)
        assertEquals(1, extractor.calls)
        assertEquals(readable, extractor.usedSource)
    }
    @Test fun identicalBytesAcrossCanonicalTracksReuseCachedVector() = runTest {
        val a = track(); val b = track()
        source(a, TrackSourceType.APP_OFFLINE_COPY, file(byteArrayOf(1, 2, 3)).path)
        source(b, TrackSourceType.TDLIB_LOCAL, file(byteArrayOf(1, 2, 3)).path)
        coordinator.extractIfNeeded(a); coordinator.extractIfNeeded(b)
        assertEquals(1, extractor.calls)
        assertNotNull(db.audioFeatureDao().compatibleVector(b.toString(), extractor.extractorName, extractor.extractorVersion, extractor.schemaVersion))
    }
    @Test fun changedContentAndCorruptVectorInvalidateCache() = runTest {
        val id = track(); val file = file(byteArrayOf(1, 2, 3))
        source(id, TrackSourceType.APP_OFFLINE_COPY, file.path)
        coordinator.extractIfNeeded(id)
        file.writeBytes(byteArrayOf(5, 6, 7))
        coordinator.extractIfNeeded(id)
        assertEquals(2, extractor.calls)
        val stored = db.audioFeatureDao().compatibleVector(id.toString(), extractor.extractorName, extractor.extractorVersion, extractor.schemaVersion)!!
        db.audioFeatureDao().put(stored.copy(vectorBlob = byteArrayOf(1)))
        coordinator.extractIfNeeded(id)
        assertEquals(3, extractor.calls)
    }
    @Test fun cancellationPropagatesWithoutCachingPartialFeatures() = runTest {
        val id = track()
        source(id, TrackSourceType.APP_OFFLINE_COPY, file(byteArrayOf(1, 2, 3)).path)
        extractor.failure = kotlinx.coroutines.CancellationException("cancel analysis")
        try { coordinator.extractIfNeeded(id); fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertNull(db.audioFeatureDao().compatibleVector(id.toString(), extractor.extractorName, extractor.extractorVersion, extractor.schemaVersion))
    }
    @Test fun aDecoderFailureDoesNotBlockAReadableFallbackSource() = runTest {
        val id = track()
        val failed = source(id, TrackSourceType.LOCAL_MEDIASTORE, file(byteArrayOf(1, 2, 3)).path)
        val fallback = source(id, TrackSourceType.APP_OFFLINE_COPY, file(byteArrayOf(4, 5, 6)).path)
        extractor.sourceFailures[failed] = IllegalStateException("Decoder unavailable")
        coordinator.extractIfNeeded(id)
        val stored = db.audioFeatureDao().compatibleVector(id.toString(), extractor.extractorName, extractor.extractorVersion, extractor.schemaVersion)
        assertNotNull(stored)
        assertEquals(fallback.toString(), stored!!.sourceIdUsed)
        assertEquals(2, extractor.calls)
    }
    @Test fun remoteOnlySourcesDoNotTriggerNetworkOrExtraction() = runTest {
        val id = track()
        db.libraryDao().upsertSource(TrackSourceEntity(UUID.randomUUID().toString(), id.toString(), TrackSourceType.TELEGRAM_REMOTE,
            SourceAvailability.REMOTE_ONLY, null, null, "audio/mpeg", null, null, true, 1, 1))
        coordinator.extractIfNeeded(id)
        assertEquals(0, extractor.calls)
    }

    private object TestManagedStorageRepository : ManagedStorageRepository {
        private val usage = ManagedStorageUsage(
            temporaryPlaybackBytes = 0L,
            pinnedOfflineBytes = 0L,
            otherManagedBytes = 0L,
            protectedBytes = 0L,
            temporaryBudgetBytes = Long.MAX_VALUE,
            lowSpace = false,
        )
        override suspend fun usage(): ManagedStorageUsage = usage
        override suspend fun reconcileAndEnforceBudget(): ManagedStorageUsage = usage
        override suspend fun clearTemporaryCache(): ManagedStorageUsage = usage
        override fun acquireLease(path: String, owner: String): ManagedFileLease = object : ManagedFileLease {
            override val path: String = path
            override val owner: String = owner
            override fun close() = Unit
        }
    }
}
