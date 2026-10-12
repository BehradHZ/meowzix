package dev.behradhz.meowzix.data.telegram

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TdLibClientAdapterTest {
    @Test
    fun nativeClientPublishesInitialAuthorizationStateAsynchronously() {
        runBlocking {
            val client = TdLibClientAdapter()
            try {
                // The process-wide updates flow replays the previous client's last update.
                // Observe this client's StateFlow to avoid a stale AuthorizationStateClosed.
                val initialState = withTimeout(15_000) {
                    client.authorizationStates.filterNotNull().first()
                }
                assertTrue(initialState is TdApi.AuthorizationStateWaitTdlibParameters)
            } finally {
                withTimeout(15_000) { client.closeAndAwaitClosed() }
                client.deactivate()
            }
            assertTrue(client.authorizationStates.value is TdApi.AuthorizationStateClosed)
        }
    }

    @Test
    fun lateSubscriberSeesAuthorizationStateAndClientCanRestartAfterClose() {
        runBlocking {
            val first = TdLibClientAdapter()
            withTimeout(15_000) {
                first.authorizationStates.filterNotNull().first()
            }
            assertTrue(first.authorizationStates.value is TdApi.AuthorizationStateWaitTdlibParameters)
            withTimeout(15_000) { first.closeAndAwaitClosed() }
            first.deactivate()

            val second = TdLibClientAdapter()
            val initial = withTimeout(15_000) {
                second.authorizationStates.filterNotNull().first()
            }
            assertTrue(initial is TdApi.AuthorizationStateWaitTdlibParameters)
            withTimeout(15_000) { second.closeAndAwaitClosed() }
        }
    }
}
