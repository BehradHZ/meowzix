package dev.behradhz.meowzix.data.telegram

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.filterIsInstance
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
    fun nativeClientEmitsInitialAuthorizationUpdateAsynchronously() {
        runBlocking {
            val client = TdLibClientAdapter()

            val update = withTimeout(15_000) {
                client.updates
                    .filterIsInstance<TdApi.UpdateAuthorizationState>()
                    .first()
            }

            assertTrue(update.authorizationState is TdApi.AuthorizationStateWaitTdlibParameters)
            client.send(TdApi.Close())
        }
    }
}
