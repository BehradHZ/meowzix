package dev.behradhz.meowzix.data.downloads

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferSchedulerTest {
    @Test
    fun neverExceedsConfiguredConcurrency() = runTest {
        val scheduler = TransferScheduler(maxConcurrentTransfers = 3, scope = this)
        val release = CompletableDeferred<Unit>()
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)

        val jobs = (0 until 12).map { index ->
            async {
                scheduler.run("file-$index", TransferPriority.BULK_DOWNLOAD) {
                    val now = active.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    release.await()
                    active.decrementAndGet()
                    index
                }
            }
        }

        testScheduler.runCurrent()
        assertEquals(3, scheduler.snapshot().active)
        assertEquals(9, scheduler.snapshot().queued)
        release.complete(Unit)
        jobs.awaitAll()
        assertEquals(3, peak.get())
    }

    @Test
    fun higherPriorityRunsBeforeOlderBulkWaiter() = runTest {
        val scheduler = TransferScheduler(maxConcurrentTransfers = 1, scope = this)
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val blocker = async {
            scheduler.run("active", TransferPriority.BULK_DOWNLOAD) {
                release.await()
                order += "active"
                Unit
            }
        }
        testScheduler.runCurrent()

        val bulk = async {
            scheduler.run("bulk", TransferPriority.BULK_DOWNLOAD) {
                order += "bulk"
                Unit
            }
        }
        val playback = async {
            scheduler.run("playback", TransferPriority.IMMEDIATE_PLAYBACK) {
                order += "playback"
                Unit
            }
        }
        testScheduler.runCurrent()
        release.complete(Unit)
        awaitAll(blocker, bulk, playback)

        assertEquals(listOf("active", "playback", "bulk"), order)
    }

    @Test
    fun identicalPhysicalRequestIsCoalesced() = runTest {
        val scheduler = TransferScheduler(maxConcurrentTransfers = 3, scope = this)
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger(0)

        val first = async {
            scheduler.run("td:99:0:0:true", TransferPriority.USER_ACTION) {
                executions.incrementAndGet()
                release.await()
                42
            }
        }
        testScheduler.runCurrent()
        val second = async {
            scheduler.run("td:99:0:0:true", TransferPriority.IMMEDIATE_PLAYBACK) {
                error("shared request must not execute twice")
            }
        }
        testScheduler.runCurrent()

        assertEquals(1, scheduler.snapshot().active)
        assertEquals(1, scheduler.snapshot().sharedRequests)
        release.complete(Unit)
        assertEquals(listOf(42, 42), awaitAll(first, second))
        assertEquals(1, executions.get())
    }

    @Test
    fun queuedPriorityCanBePromotedBySharedConsumer() = runTest {
        val scheduler = TransferScheduler(maxConcurrentTransfers = 1, scope = this)
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val blocker = async {
            scheduler.run("blocker", TransferPriority.USER_ACTION) {
                release.await()
                Unit
            }
        }
        testScheduler.runCurrent()
        val sharedLow = async {
            scheduler.run("shared", TransferPriority.SPECULATIVE_PREFETCH) {
                order += "shared"
                1
            }
        }
        val bulk = async {
            scheduler.run("bulk", TransferPriority.BULK_DOWNLOAD) {
                order += "bulk"
                2
            }
        }
        val sharedHigh = async {
            scheduler.run("shared", TransferPriority.IMMEDIATE_PLAYBACK) { 3 }
        }
        testScheduler.runCurrent()
        release.complete(Unit)
        awaitAll(blocker, sharedLow, bulk, sharedHigh)

        assertTrue(order.indexOf("shared") < order.indexOf("bulk"))
    }
}
