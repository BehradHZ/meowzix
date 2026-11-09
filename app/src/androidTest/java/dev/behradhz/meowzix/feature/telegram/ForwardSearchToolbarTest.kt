package dev.behradhz.meowzix.feature.telegram

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForwardSearchToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun refreshIsASeparateRightHandGlassHitTarget() {
        var refreshCount = 0
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                ForwardSearchToolbar(
                    query = "",
                    onQueryChange = {},
                    onReloadFolders = { refreshCount++ },
                    foldersLoading = false,
                    hazeState = rememberHazeState(),
                )
            }
        }
        compose.onNodeWithTag("telegram-forward-search").assertIsDisplayed()
        compose.onNodeWithTag("telegram-forward-refresh").assertIsDisplayed()
        val search = compose.onNodeWithTag("telegram-forward-search")
            .fetchSemanticsNode().boundsInRoot
        val refresh = compose.onNodeWithTag("telegram-forward-refresh")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Refresh must remain to the right of Search", refresh.left > search.right)
        compose.onNodeWithTag("telegram-forward-refresh").performClick()
        compose.runOnIdle { assertEquals(1, refreshCount) }
    }
}
