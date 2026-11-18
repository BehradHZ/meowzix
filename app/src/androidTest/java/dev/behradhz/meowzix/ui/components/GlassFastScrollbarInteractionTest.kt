package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassFastScrollbarInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shortListThumbDragMovesWithinAnItemWithoutJumpingToTheNext() {
        val state = LazyListState()
        compose.setContent {
            MaterialTheme {
                GlassLazyColumn(
                    modifier = Modifier.fillMaxWidth().height(540.dp).testTag("short-list"),
                    state = state,
                ) {
                    items(10) {
                        Box(Modifier.fillMaxWidth().height(70.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        val pxPerDp = compose.density.density
        val widthPx = compose.onNodeWithTag("short-list").fetchSemanticsNode().size.width.toFloat()
        compose.onNodeWithTag("short-list").performTouchInput {
            // At the top the 96dp grip is centered 60dp from the list's top.
            down(Offset(widthPx - 7f * pxPerDp, 60f * pxPerDp))
            moveBy(Offset(0f, 12f * pxPerDp))
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemIndex)
            assertTrue(
                "A small drag should move a few pixels inside the same item",
                state.firstVisibleItemScrollOffset in 1 until (70f * pxPerDp).toInt(),
            )
        }
    }

    @Test
    fun shortGridUsesTheSamePixelAccurateScrubbing() {
        val state = LazyGridState()
        compose.setContent {
            MaterialTheme {
                GlassLazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(540.dp).testTag("short-grid"),
                    state = state,
                ) {
                    items(20) {
                        Box(Modifier.fillMaxWidth().height(80.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        val pxPerDp = compose.density.density
        val widthPx = compose.onNodeWithTag("short-grid").fetchSemanticsNode().size.width.toFloat()
        compose.onNodeWithTag("short-grid").performTouchInput {
            down(Offset(widthPx - 7f * pxPerDp, 60f * pxPerDp))
            moveBy(Offset(0f, 12f * pxPerDp))
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemIndex)
            assertTrue(
                "A grid should scroll within its first row",
                state.firstVisibleItemScrollOffset in 1 until (80f * pxPerDp).toInt(),
            )
        }
    }
}
