package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A user-requested chat scan takes priority over long automatic history indexing.
 * The canceled background pass is restarted from the persisted chat checkpoints.
 * Repeated background requests coalesce rather than starting competing scans.
 */
internal class TelegramSourceSyncCoordinator(private val scope: CoroutineScope) {
    private val monitor = Any()
    private val foregroundMutex = Mutex()
    private var foregroundRequests = 0
    private var backgroundJob: Job? = null
    private var runningBackground: (suspend () -> Unit)? = null
    private var queuedBackground: (suspend () -> Unit)? = null

    fun requestBackground(block: suspend () -> Unit) {
        synchronized(monitor) {
            if (foregroundRequests == 0 && backgroundJob?.isActive == true) return
            queuedBackground = block
            startBackgroundIfIdle()
        }
    }

    suspend fun <T> foreground(block: suspend () -> T): T {
        val canceledBackground = synchronized(monitor) {
            foregroundRequests++
            backgroundJob?.takeIf { it.isActive }?.also { job ->
                if (queuedBackground == null) queuedBackground = runningBackground
                job.cancel()
            }
        }
        try {
            canceledBackground?.join()
            return foregroundMutex.withLock { block() }
        } finally {
            synchronized(monitor) {
                foregroundRequests--
                startBackgroundIfIdle()
            }
        }
    }

    /** Must be called while holding monitor; the lazy job is published before execution. */
    private fun startBackgroundIfIdle() {
        if (foregroundRequests != 0 || backgroundJob?.isActive == true) return
        val block = queuedBackground ?: return
        queuedBackground = null
        runningBackground = block
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                synchronized(monitor) {
                    if (backgroundJob === coroutineContext[Job]) {
                        backgroundJob = null
                        runningBackground = null
                        startBackgroundIfIdle()
                    }
                }
            }
        }
        backgroundJob = job
        job.start()
    }
}
