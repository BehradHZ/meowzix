package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactDockInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactDestinationExpandsDockWithoutNavigating() {
        var compact by mutableStateOf(true)
        var expandCount by mutableIntStateOf(0)
        var navigationCount by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth()) {
                    MorphingDock(
                        hazeState = rememberHazeState(),
                        destinations = listOf(
                            DockDestination("home", "Home") {
                                Icon(Icons.Rounded.Home, contentDescription = null)
                            },
                        ),
                        selectedRoute = "home",
                        searchActive = false,
                        dockCompact = compact,
                        searchPlaceholder = "Search",
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onSelect = { navigationCount++ },
                        onExpandCompact = { expandCount++; compact = false },
                        onCloseSearch = {},
                        reduceMotion = true,
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Expand navigation dock").performClick()
        compose.runOnIdle {
            assertFalse(compact)
            assertEquals(1, expandCount)
            assertEquals("Compact tap must not navigate", 0, navigationCount)
        }
    }
    @Test fun searchBubbleExpandsThroughIntermediateWidthsAndContractsBack() {
        var searchActive by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth()) {
                    MorphingDock(
                        hazeState = rememberHazeState(),
                        destinations = listOf(
                            DockDestination("home", "Home") {
                                Icon(Icons.Rounded.Home, contentDescription = null)
                            },
                        ),
                        selectedRoute = if (searchActive) "search" else "home",
                        searchActive = searchActive,
                        dockCompact = false,
                        searchPlaceholder = "Search",
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onSelect = { if (it == "search") searchActive = true },
                        onExpandCompact = {},
                        onCloseSearch = { searchActive = false },
                        reduceMotion = false,
                    )
                }
            }
        }

        val circleWidth = compose.onNodeWithTag("dock-search-bubble")
            .getUnclippedBoundsInRoot().let { it.right - it.left }
        compose.runOnIdle { searchActive = true }
        compose.mainClock.advanceTimeBy(150)
        val intermediateWidth = compose.onNodeWithTag("dock-search-bubble")
            .getUnclippedBoundsInRoot().let { it.right - it.left }
        compose.mainClock.advanceTimeBy(DockSearchOpenDurationMs.toLong())
        val expandedWidth = compose.onNodeWithTag("dock-search-bubble")
            .getUnclippedBoundsInRoot().let { it.right - it.left }

        assertTrue("Search must grow from the existing circle", intermediateWidth > circleWidth + 5.dp)
        assertTrue("Search must not jump directly to full width", expandedWidth > intermediateWidth + 5.dp)

        compose.runOnIdle { searchActive = false }
        compose.mainClock.advanceTimeBy(DockSearchCloseDurationMs.toLong() + 80L)
        val collapsedWidth = compose.onNodeWithTag("dock-search-bubble")
            .getUnclippedBoundsInRoot().let { it.right - it.left }
        assertTrue("Closing must restore the original search circle", (collapsedWidth - circleWidth).value < 2f &&
            (collapsedWidth - circleWidth).value > -2f)
    }

}
