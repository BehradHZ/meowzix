package dev.behradhz.meowzix.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.ProgressiveQueue
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import dev.behradhz.meowzix.domain.playback.SmartFutureRevisionResult
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationBus
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationReason
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Debounced Smart-queue mutation coordinator.
 *
 * It shares the existing MediaSession instead of owning another player. Ranking is performed from a
 * captured logical queue revision. Results are applied atomically only when that revision is still
 * current, so a user's Play Next/drag/remove action wins over an in-flight ranking job.
 */
@Singleton
class SmartQueueLiveAdapter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recommendationEngine: RecommendationEngine,
    private val progressiveQueue: ProgressiveQueue,
    private val adaptationBus: SmartQueueAdaptationBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connectionJob: Job? = null
    private var initialized = false
    private var lastBucket = currentTimeBucket()

    @Synchronized
    fun initialize() {
        if (initialized) return
        initialized = true
        connectionJob = scope.launch {
            val connected = runCatching {
                MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                ).buildAsync().await()
            }.getOrNull() ?: return@launch
            controller = connected

            launch {
                adaptationBus.signals.collectLatest {
                    delay(ADAPTATION_DEBOUNCE_MS)
                    rerankFuture(connected)
                }
            }
            launch {
                while (isActive) {
                    delay(TIME_BUCKET_CHECK_MS)
                    val next = currentTimeBucket()
                    val active = progressiveQueue.snapshot()
                    if (active?.playbackMode == PlaybackMode.SMART_SHUFFLE && next != lastBucket) {
                        lastBucket = next
                        adaptationBus.emit(SmartQueueAdaptationReason.TIME_BUCKET_CHANGED)
                    } else if (active?.playbackMode != PlaybackMode.SMART_SHUFFLE) {
                        lastBucket = next
                    }
                }
            }
        }
    }

    private suspend fun rerankFuture(connected: MediaController) {
        val captured = progressiveQueue.snapshot() ?: return
        if (captured.playbackMode != PlaybackMode.SMART_SHUFFLE) return
        val current = captured.tracks.getOrNull(captured.currentIndex) ?: return
        val eligible = captured.tracks.indices
            .asSequence()
            .filter { it > captured.currentIndex && captured.origins[it] == QueueItemOrigin.GENERATED }
            .take(ProgressiveQueue.SMART_RERANK_WINDOW)
            .map { captured.tracks[it].id }
            .distinct()
            .toList()
        if (eligible.size <= 1) return

        val ranked = try {
            recommendationEngine.generate(
                allowedTrackIds = eligible,
                currentTrackId = current.id,
                timeBucket = currentTimeBucket(),
            ).trackIds
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return
        }

        when (progressiveQueue.rerankGeneratedFuture(ranked, captured.revision)) {
            SmartFutureRevisionResult.APPLIED -> syncMaterializedFuture(connected)
            SmartFutureRevisionResult.NO_CHANGE,
            SmartFutureRevisionResult.STALE,
            SmartFutureRevisionResult.NOT_SMART -> Unit
        }
    }

    /** Current MediaItem is never removed or prepared again; only already-materialized future is replaced. */
    private fun syncMaterializedFuture(connected: MediaController) {
        val snapshot = progressiveQueue.snapshot() ?: return
        if (snapshot.playbackMode != PlaybackMode.SMART_SHUFFLE) return
        val playerCurrentIndex = connected.currentMediaItemIndex
        if (playerCurrentIndex !in 0 until connected.mediaItemCount) return

        val logicalFutureStart = snapshot.currentIndex + 1
        val logicalFutureEnd = snapshot.materializedEndExclusive
            .coerceIn(logicalFutureStart, snapshot.tracks.size)
        val desiredFuture = snapshot.tracks
            .subList(logicalFutureStart, logicalFutureEnd)
            .map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) }
        val playerFutureStart = playerCurrentIndex + 1

        if (playerFutureStart < connected.mediaItemCount) {
            connected.removeMediaItems(playerFutureStart, connected.mediaItemCount)
        }
        if (desiredFuture.isNotEmpty()) connected.addMediaItems(desiredFuture)
        // Deliberately no prepare(), seek(), or play(): current playback instance/position survive.
    }

    private companion object {
        const val ADAPTATION_DEBOUNCE_MS = 650L
        const val TIME_BUCKET_CHECK_MS = 60_000L
    }
}

private fun currentTimeBucket() =
    ListeningEventSemantics.timeContext(Instant.now(), ZoneId.systemDefault()).bucket
