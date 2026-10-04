package dev.behradhz.meowzix.data.downloads

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Injectable facade over the process-wide transfer budget. TDLib's adapter also uses this same
 * scheduler, so playback, explicit downloads, bulk jobs, artwork and prefetch cannot accidentally
 * create separate concurrency pools.
 */
@Singleton
class PriorityTransferScheduler @Inject constructor() {
    suspend fun <T : Any> run(
        physicalKey: String,
        priority: TransferPriority,
        block: suspend () -> T,
    ): T = ProcessTransferBudget.scheduler.run(physicalKey, priority, block)

    suspend fun snapshot(): TransferSchedulerSnapshot = ProcessTransferBudget.scheduler.snapshot()
}

internal object ProcessTransferBudget {
    val scheduler = TransferScheduler(DEFAULT_MAX_CONCURRENT_TRANSFERS)
}

open class TransferScheduler(
    private val maxConcurrentTransfers: Int,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    init {
        require(maxConcurrentTransfers > 0)
    }

    private val mutex = Mutex()
    private val sequence = AtomicLong(0L)
    private val tasks = mutableMapOf<String, Task>()
    private val waiting = mutableListOf<Task>()
    private var activeCount = 0

    suspend fun <T : Any> run(
        physicalKey: String,
        priority: TransferPriority,
        block: suspend () -> T,
    ): T {
        val task = mutex.withLock {
            val existing = tasks[physicalKey]
            val selected = if (existing != null) {
                if (priority.weight > existing.priority.weight && existing.job == null) {
                    existing.priority = priority
                }
                existing
            } else {
                val erasedBlock: suspend () -> Any = { block() }
                Task(
                    key = physicalKey,
                    priority = priority,
                    sequence = sequence.getAndIncrement(),
                    result = CompletableDeferred(),
                    block = erasedBlock,
                ).also { created ->
                    tasks[physicalKey] = created
                    waiting += created
                }
            }
            selected.consumers += 1
            dispatchLocked()
            selected
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            task.result.await() as T
        } finally {
            releaseConsumer(task)
        }
    }

    suspend fun snapshot(): TransferSchedulerSnapshot = mutex.withLock {
        TransferSchedulerSnapshot(
            active = activeCount,
            queued = waiting.size,
            sharedRequests = tasks.values.count { it.consumers > 1 },
        )
    }

    private suspend fun releaseConsumer(task: Task) {
        mutex.withLock {
            task.consumers = (task.consumers - 1).coerceAtLeast(0)
            if (task.consumers != 0 || task.result.isCompleted) return@withLock

            if (task.job == null) {
                waiting.remove(task)
                tasks.remove(task.key, task)
                task.result.cancel(CancellationException("Transfer no longer has consumers"))
            } else {
                task.job?.cancel(CancellationException("Transfer no longer has consumers"))
            }
        }
    }

    private fun dispatchLocked() {
        while (activeCount < maxConcurrentTransfers && waiting.isNotEmpty()) {
            val next = waiting.maxWithOrNull(
                compareBy<Task> { it.priority.weight }
                    .thenByDescending { -it.sequence },
            ) ?: return
            waiting.remove(next)
            activeCount += 1
            next.job = scope.launch {
                try {
                    next.result.complete(next.block())
                } catch (cancelled: CancellationException) {
                    next.result.cancel(cancelled)
                    throw cancelled
                } catch (error: Throwable) {
                    next.result.completeExceptionally(error)
                } finally {
                    mutex.withLock {
                        activeCount = (activeCount - 1).coerceAtLeast(0)
                        tasks.remove(next.key, next)
                        dispatchLocked()
                    }
                }
            }
        }
    }

    private data class Task(
        val key: String,
        var priority: TransferPriority,
        val sequence: Long,
        val result: CompletableDeferred<Any>,
        val block: suspend () -> Any,
        var consumers: Int = 0,
        var job: Job? = null,
    )
}

enum class TransferPriority(val weight: Int) {
    SPECULATIVE_PREFETCH(0),
    BULK_DOWNLOAD(1),
    USER_ACTION(2),
    IMMEDIATE_PLAYBACK(3),
}

data class TransferSchedulerSnapshot(
    val active: Int,
    val queued: Int,
    val sharedRequests: Int,
)

internal const val DEFAULT_MAX_CONCURRENT_TRANSFERS = 3
