package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationBus
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationReason
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Observes finalized outcomes only; seeks, failures and timer/service stops never masquerade as skips. */
@Singleton
class SmartOutcomeAdaptationObserver @Inject constructor(
    private val historyDao: HistoryDao,
    private val adaptationBus: SmartQueueAdaptationBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastSeenSequence: Long? = null

    @Synchronized
    fun initialize() {
        if (job != null) return
        job = scope.launch {
            historyDao.observeRecentFinalizedOutcomes(RECENT_OUTCOME_LIMIT).collectLatest { outcomes ->
                val newestSequence = outcomes.maxOfOrNull { it.eventSequence } ?: return@collectLatest
                val previous = lastSeenSequence
                if (previous == null) {
                    // Establish a baseline instead of replaying historical skips on every process start.
                    lastSeenSequence = newestSequence
                    return@collectLatest
                }
                if (newestSequence <= previous) return@collectLatest
                lastSeenSequence = newestSequence

                val newOutcomes = outcomes.filter { it.eventSequence > previous }
                if (newOutcomes.any { it.type == EARLY_SKIP }) {
                    adaptationBus.emit(SmartQueueAdaptationReason.EARLY_SKIP)
                }
                if (outcomes.take(SKIP_STREAK_THRESHOLD).all { it.type == EARLY_SKIP }) {
                    adaptationBus.emit(SmartQueueAdaptationReason.SKIP_STREAK)
                }
            }
        }
    }

    private companion object {
        const val EARLY_SKIP = "SKIPPED_EARLY"
        const val SKIP_STREAK_THRESHOLD = 2
        const val RECENT_OUTCOME_LIMIT = 6
    }
}
