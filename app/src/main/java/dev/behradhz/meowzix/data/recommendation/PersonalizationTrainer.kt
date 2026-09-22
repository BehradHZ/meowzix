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

    fun onPlaybackFinalized(playbackInstanceId: UUID) {
        scope.launch {
            runCatching {
                datasetBuilder.sampleForPlayback(playbackInstanceId)?.let { model.update(listOf(it)) }
            }
        }
    }

    fun refreshIfStale() {
        if (!refreshRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                runCatching {
                    val latest = datasetBuilder.latestDataVersion()
                    val state = model.state()
                    if (latest > state.trainingDataVersion) {
                        model.rebuild(datasetBuilder.buildAll())
                    }
                }
            } finally {
                refreshRunning.set(false)
            }
        }
    }
}
