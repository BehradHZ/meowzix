package dev.behradhz.meowzix.feature.downloads

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveDownloadCancelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeDownloadCancelTargetsTheRightTrack() {
        val one = UUID.randomUUID()
        val two = UUID.randomUUID()
        val canceled = mutableListOf<UUID>()
        compose.setContent {
            MeowzixTheme {
                androidx.compose.foundation.layout.Column {
                    listOf(one, two).forEach { id ->
                        DownloadRowCard(
                            row = DownloadRow(id, "Track " + id, null, DownloadStatus.DOWNLOADING,
                                0.34f, 340, 1000, null),
                            onPause = {}, onResume = {}, onCancel = { canceled += id },
                            onRetry = {}, onRemove = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("download-cancel-" + two).performClick()
        compose.runOnIdle { assertEquals(listOf(two), canceled) }
    }
}
