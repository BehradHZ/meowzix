package dev.behradhz.meowzix.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorphingPlayerBackDispatchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun expandedPlayerConsumesBackBeforeBackgroundNavigation() {
        val player = MorphingPlayerState()
        var backgroundBacks = 0
        compose.setContent {
            // Mirrors the underlying NavHost's Back callback.
            BackHandler { backgroundBacks++ }
            MorphingPlayerBackHandler(player)
        }

        compose.runOnIdle { player.expand() }
        pressBack()

        compose.runOnIdle {
            assertFalse(player.targetExpanded)
            assertEquals(0, backgroundBacks)
        }

        // Background navigation is re-enabled after the collapse finishes.
        compose.runOnIdle { player.updateOverlayVisibility(false) }
        pressBack()
        compose.runOnIdle { assertEquals(1, backgroundBacks) }
    }

    @Test
    fun repeatedBackDuringCollapseNeverNavigatesUnderPlayer() {
        val player = MorphingPlayerState()
        var backgroundBacks = 0
        compose.setContent {
            BackHandler { backgroundBacks++ }
            MorphingPlayerBackHandler(player)
        }

        compose.runOnIdle {
            player.expand()
            player.updateOverlayVisibility(true)
        }
        pressBack()
        pressBack()

        compose.runOnIdle {
            assertEquals(0, backgroundBacks)
            assertTrue(player.finishCollapseImmediately)
        }
    }

    @Test
    fun playerSubpanelDismissesBeforeFullPlayerCollapses() {
        val player = MorphingPlayerState()
        val panelOpen = mutableStateOf(false)
        var backgroundBacks = 0
        compose.setContent {
            BackHandler { backgroundBacks++ }
            MorphingPlayerBackHandler(player)
            if (player.targetExpanded) {
                // The full player's lyrics/inline queue callback is composed inside the overlay.
                BackHandler(enabled = panelOpen.value) { panelOpen.value = false }
            }
        }

        compose.runOnIdle {
            player.expand()
            panelOpen.value = true
        }
        pressBack()
        compose.runOnIdle {
            assertFalse(panelOpen.value)
            assertTrue(player.targetExpanded)
            assertEquals(0, backgroundBacks)
        }
        pressBack()
        compose.runOnIdle {
            assertFalse(player.targetExpanded)
            assertEquals(0, backgroundBacks)
        }
    }
}
