package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.domain.recommendation.*
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.personalizationModelDataStore by preferencesDataStore("personalization_model")

/** Existing binding/name retained; implementation upgraded to shared LinUCB. */
@Singleton
class LocalLinearPersonalizationModel internal constructor(
    private val historyDao: HistoryDao,
    private val preferences: DataStore<Preferences>,
    private val artifactFile: AtomicFile,
) : PersonalizationModel {
    @Inject constructor(@ApplicationContext context: Context, historyDao: HistoryDao) : this(
        historyDao, context.personalizationModelDataStore,
        AtomicFile(File(context.noBackupFilesDir, "personalization/shared-linucb.bin")),
    )
    private val mutex = Mutex()
    private var loaded = false
    private var ranker = SharedLinUcb()
    private var modelState = PersonalizationModelState(requiresRebuild = true)

    override suspend fun state(): PersonalizationModelState = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked(); modelState }
    }

    override suspend fun score(context: RecommendationContext, candidates: List<TrackFeatures>): List<ModelTrackScore> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                loadLocked()
                if (!modelState.active) return@withLock emptyList()
                val coefficients = ranker.coefficients()
                candidates.map { candidate ->
                    currentCoroutineContext().ensureActive()
                    require(candidate.schemaVersion == modelState.featureSchemaVersion)
                    RecommendationFeatureSchemaV2.validate(candidate.values)
                    val (reward, uncertainty) = ranker.predict(candidate.values)
                    val contributions = candidate.values.mapIndexed { index, value ->
                        FeatureContribution(RecommendationFeatureSchemaV2.names[index], coefficients[index] * value)
                    }.filter { it.value > 0.01 }.sortedByDescending { it.value }.take(8)
                    ModelTrackScore(candidate.trackId, reward, uncertainty, contributions)
                }
            }
        }

    override suspend fun scoreBatch(features: Map<UUID, DoubleArray>): Map<UUID, Double> =
        score(RecommendationContext(0, 1, false, dev.behradhz.meowzix.domain.history.TimeBucket.NIGHT),
            features.map { (id, vector) -> TrackFeatures(id, vector) })
            .associate { it.trackId to (it.expectedReward + 1.0) / 2.0 }

    override suspend fun update(samples: List<TrainingSample>) = train(samples, rebuild = false)
    override suspend fun rebuild(samples: List<TrainingSample>) = train(samples, rebuild = true)

    private suspend fun train(samples: List<TrainingSample>, rebuild: Boolean) = withContext(Dispatchers.Default) {
        mutex.withLock {
            loadLocked()
            val floor = modelState.historyFloorVersion
            val watermark = if (rebuild) floor else modelState.trainingDataVersion
            val fresh = samples.distinctBy { it.playbackInstanceId }.filter { it.dataVersion > watermark }.sortedBy { it.dataVersion }
            if (!rebuild && fresh.isEmpty()) return@withLock
            val next = if (rebuild) SharedLinUcb() else ranker.copy()
            fresh.forEach { sample ->
                currentCoroutineContext().ensureActive()
                require(sample.featureSchemaVersion == PersonalizationFeatureVectorizer.SCHEMA_VERSION && sample.rewardSchemaVersion == PersonalizationRewardBuilder.VERSION)
                RecommendationFeatureSchemaV2.validate(sample.features)
                next.update(sample.features, sample.reward, sample.weight)
            }
            next.coefficients()
            val count = (if (rebuild) 0L else modelState.sampleCount) + fresh.size
            val state = PersonalizationModelState(
                trainingDataVersion = maxOf(watermark, fresh.maxOfOrNull { it.dataVersion } ?: floor),
                historyFloorVersion = floor,
                trainedAtEpochMs = System.currentTimeMillis(),
                sampleCount = count,
                active = count >= RecommendationConfig.MIN_TRAINING_SAMPLES,
            )
            publishLocked(next, state)
        }
    }

    override suspend fun reset(trainingDataVersion: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val floor = trainingDataVersion.coerceAtLeast(0L)
            // Separate privacy floor survives artifact corruption and model version changes.
            preferences.edit { it[SEQUENCE_FLOOR] = floor }
            ranker = SharedLinUcb()
            modelState = PersonalizationModelState(trainingDataVersion = floor, historyFloorVersion = floor)
            loaded = true
            publishLocked(ranker, modelState)
        }
    }

    private suspend fun loadLocked() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            val prefs = preferences.data.first()
            // Old epoch-millisecond reset watermark is converted without losing the user's reset.
            val floor = prefs[SEQUENCE_FLOOR] ?: (prefs[LEGACY_FLOOR]?.let { historyDao.sequenceAtOrBefore(it) } ?: 0L)
            if (prefs[SEQUENCE_FLOOR] == null) preferences.edit { it[SEQUENCE_FLOOR] = floor }
            try {
                val file = artifactFile
                require(file.baseFile.length() <= RecommendationConfig.MODEL_ARTIFACT_LIMIT_BYTES)
                val bytes = file.openRead().use { input ->
                    val bounded = ByteArray(RecommendationConfig.MODEL_ARTIFACT_LIMIT_BYTES + 1)
                    var count = 0
                    while (count < bounded.size) {
                        val read = input.read(bounded, count, bounded.size - count)
                        if (read < 0) break
                        count += read
                    }
                    require(count <= RecommendationConfig.MODEL_ARTIFACT_LIMIT_BYTES)
                    bounded.copyOf(count)
                }
                val artifact = ModelArtifactCodec.decode(bytes)
                require(artifact.state.historyFloorVersion == floor)
                ranker = artifact.model
                modelState = artifact.state
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                ranker = SharedLinUcb()
                modelState = PersonalizationModelState(trainingDataVersion = floor, historyFloorVersion = floor, requiresRebuild = true)
            }
            loaded = true
        }
    }

    private suspend fun publishLocked(next: SharedLinUcb, state: PersonalizationModelState) = withContext(Dispatchers.IO) {
        val bytes = ModelArtifactCodec.encode(LearnedModelArtifact(state, next))
        val verified = ModelArtifactCodec.decode(bytes)
        val file = artifactFile
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        ranker = next
        modelState = verified.state
    }

    companion object {
        const val MIN_TRAINING_SAMPLES = RecommendationConfig.MIN_TRAINING_SAMPLES
        private val SEQUENCE_FLOOR = longPreferencesKey("sequence_history_floor")
        private val LEGACY_FLOOR = longPreferencesKey("history_floor_version")
    }
}
