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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
