package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationBus
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationReason
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import dev.behradhz.meowzix.domain.recommendation.RecommendationConfig
import dev.behradhz.meowzix.domain.recommendation.TrainingScheduler
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** WorkManager owns job lifetime; mutex serializes updates, resets, and historical rebuilds. */
@Singleton
class PersonalizationTrainer @Inject constructor(
    private val datasetBuilder: TrainingDatasetBuilder,
    private val model: PersonalizationModel,
    private val scheduler: TrainingScheduler,
    private val adaptationBus: SmartQueueAdaptationBus,
) {
    private val trainingMutex = Mutex()

    fun onPlaybackFinalized(@Suppress("UNUSED_PARAMETER") playbackInstanceId: UUID) = scheduler.scheduleTraining()
    fun refreshIfStale() = scheduler.scheduleTraining()

    suspend fun trainPending(forceRebuild: Boolean = false) = withContext(Dispatchers.Default) {
        var updatedModel = false
        trainingMutex.withLock {
            val state = model.state()
            val rebuild = forceRebuild || state.requiresRebuild
            val latest = datasetBuilder.latestDataVersion()
            if (!rebuild && latest <= state.trainingDataVersion) return@withLock
            if (!rebuild && !datasetBuilder.hasPendingBatch(state.historyFloorVersion, state.trainingDataVersion)) return@withLock
            if (rebuild) datasetBuilder.rebuildPreferenceStats(state.historyFloorVersion)
            val samples = datasetBuilder.materialize(state.historyFloorVersion, if (rebuild) 0L else state.trainingDataVersion, latestFirst = rebuild)
            if (rebuild) {
                model.rebuild(samples)
                updatedModel = true
            } else if (samples.size >= RecommendationConfig.UPDATE_BATCH) {
                model.update(samples)
                updatedModel = true
                // Drain an oversized backlog in chronological batches without jumping past old rewards.
                val updated = model.state()
                if (datasetBuilder.hasPendingBatch(updated.historyFloorVersion, updated.trainingDataVersion)) scheduler.scheduleTraining()
            }
        }
        if (updatedModel) adaptationBus.emit(SmartQueueAdaptationReason.MODEL_UPDATED)
    }

    suspend fun resetAfterHistoryClear() = trainingMutex.withLock {
        datasetBuilder.clearDerivedSamples()
        model.reset(0L)
    }
    suspend fun resetLearningKeepHistory() = trainingMutex.withLock {
        // Serialize aggregate clearing with rebuilds, so an in-flight worker cannot restore old stats
        // after the user's reset but before the model's privacy floor is published.
        datasetBuilder.clearPreferenceStats()
        datasetBuilder.clearDerivedSamples()
        model.reset(datasetBuilder.latestEventSequence())
    }
    suspend fun rebuildFromStoredHistory() {
        trainingMutex.withLock { model.reset(0L); datasetBuilder.clearDerivedSamples() }
        scheduler.scheduleTraining(rebuild = true)
    }
}
