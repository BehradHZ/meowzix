package dev.behradhz.meowzix.feature.telegram

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.telegram.TelegramSendJob
import dev.behradhz.meowzix.domain.telegram.TelegramSendState
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramSendCancellationControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingTheUploadProgressRingActuallyRequestsCancellation() {
        val job = send(TelegramSendState.UPLOADING)
        var cancelCalls = 0
        compose.setContent {
            MeowzixTheme {
                TelegramSendStatusCard(
                    hazeState = rememberHazeState(),
                    job = job,
                    onRetry = {},
                    onCancel = { cancelCalls++ },
                )
            }
        }
        compose.onNodeWithTag("telegram-send-cancel-upload-" + job.id).performClick()
        compose.runOnIdle { assertEquals(1, cancelCalls) }
    }

    @Test fun tappingTheXOnAnUncertainSendActuallyDismissesIt() {
        val job = send(TelegramSendState.VERIFYING)
        var cancelCalls = 0
        compose.setContent {
            MeowzixTheme {
                TelegramSendStatusCard(
                    hazeState = rememberHazeState(),
                    job = job,
                    onRetry = {},
                    onCancel = { cancelCalls++ },
                )
            }
        }
        compose.onNodeWithTag("telegram-send-dismiss-" + job.id).performClick()
        compose.runOnIdle { assertEquals(1, cancelCalls) }
    }

    private fun send(state: TelegramSendState) = TelegramSendJob(
        id = UUID.randomUUID(),
        trackId = UUID.randomUUID(),
        targetChatId = 42L,
        targetTitle = "Test chat",
        state = state,
        progressPercent = 42,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )
}
