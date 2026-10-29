package dev.behradhz.meowzix.feature.library

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingAndClosingPlaylistKeepTheOutgoingSurfaceUntilMotionFinishes() {
        var selected by mutableStateOf<String?>(null)
        var gridSettled = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MeowzixTheme {
                PlaylistContentTransition(
                    selectedKey = selected,
                    reduceMotion = false,
                    onGridSettled = { gridSettled++ },
                    gridContent = { Text("Collections", Modifier.testTag("collections")) },
                    detailContent = { Text("Detail: $it", Modifier.testTag("playlist-detail")) },
                )
            }
        }
        compose.onNodeWithTag("collections").assertExists()
        compose.runOnIdle { selected = "night-drive" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("collections").assertExists()
        compose.onNodeWithTag("playlist-detail").assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("collections").assertDoesNotExist()

        compose.runOnIdle { selected = null }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("playlist-detail").assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("playlist-detail").assertDoesNotExist()
        compose.onNodeWithTag("collections").assertExists()
        compose.runOnIdle { assertTrue("Grid settlement callback must run after closing", gridSettled >= 2) }
    }
}
