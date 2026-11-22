package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramSourceSyncCoordinatorTest {
    @Test fun manualRefreshPreemptsAndResumesAutomaticIndexing() = runTest {
        val scheduler = TelegramSourceSyncCoordinator(backgroundScope)
        val events = mutableListOf<String>()
        var automaticRuns = 0
        scheduler.requestBackground {
            automaticRuns++
            if (automaticRuns == 1) {
                events += "automatic-start"
                awaitCancellation()
            } else events += "automatic-resumed"
        }
        runCurrent()
        val manual = backgroundScope.async {
            scheduler.foreground { events += "manual-refresh" }
        }
        runCurrent()
        manual.await()
        runCurrent()
        assertEquals(listOf("automatic-start", "manual-refresh", "automatic-resumed"), events)
        assertEquals(2, automaticRuns)
    }

    @Test fun automaticRequestDuringRefreshIsQueued() = runTest {
        val scheduler = TelegramSourceSyncCoordinator(backgroundScope)
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val manual = backgroundScope.async {
            scheduler.foreground {
                events += "manual-start"
                release.await()
                events += "manual-end"
            }
        }
        runCurrent()
        scheduler.requestBackground { events += "automatic" }
        runCurrent()
        assertEquals(listOf("manual-start"), events)
        release.complete(Unit)
        runCurrent()
        manual.await()
        assertEquals(listOf("manual-start", "manual-end", "automatic"), events)
    }

    @Test fun repeatedBackgroundRequestsAreCoalesced() = runTest {
        val scheduler = TelegramSourceSyncCoordinator(backgroundScope)
        val release = CompletableDeferred<Unit>()
        var runs = 0
        scheduler.requestBackground {
            runs++
            release.await()
        }
        runCurrent()
        scheduler.requestBackground { runs++ }
        runCurrent()
        assertEquals(1, runs)
        release.complete(Unit)
        runCurrent()
        assertEquals(1, runs)
    }
}
