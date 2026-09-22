package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps training away from Media3 callbacks and Compose. Any failure leaves heuristic Smart
 * Shuffle fully usable; the next stale-data refresh can rebuild from canonical listening history.
 */
@Singleton
class PersonalizationTrainer @Inject constructor(
    private val datasetBuilder: TrainingDatasetBuilder,
    private val model: PersonalizationModel,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshRunning = AtomicBoolean(false)
    private val trainingMutex = Mutex()

    fun onPlaybackFinalized(playbackInstanceId: UUID) {
        scope.launch {
            runCatching {
                trainingMutex.withLock {
                    datasetBuilder.sampleForPlayback(playbackInstanceId)?.let { model.update(listOf(it)) }
                }
            }
        }
    }

    fun refreshIfStale() {
        if (!refreshRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                runCatching {
                    trainingMutex.withLock {
                        val latest = datasetBuilder.latestDataVersion()
                        val state = model.state()
                        if (latest > state.trainingDataVersion) {
                            model.rebuild(datasetBuilder.buildAll())
                        }
                    }
                }
            } finally {
                refreshRunning.set(false)
            }
        }
    }

    suspend fun resetAfterHistoryClear() = trainingMutex.withLock { model.reset(0L) }

    suspend fun resetLearningKeepHistory() = trainingMutex.withLock {
        // Watermark everything already stored so the next stale check learns only from interactions
        // that happen after the user's explicit reset.
        model.reset(datasetBuilder.latestDataVersion())
    }

    /** Explicit developer/user maintenance action: discard weights and rebuild from stored history. */
    suspend fun rebuildFromStoredHistory() = trainingMutex.withLock {
        model.reset(0L)
        model.rebuild(datasetBuilder.buildAll())
    }
}
