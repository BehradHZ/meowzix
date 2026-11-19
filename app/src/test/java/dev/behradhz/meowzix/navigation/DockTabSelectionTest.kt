package dev.behradhz.meowzix.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DockTabSelectionTest {
    @Test fun tappingOriginTabWhileSettingsIsOpenOnlyDismissesSettings() {
        for (route in listOf("settings", "downloads", "history", "telegram-auth")) {
            assertEquals(
                DockTabTapAction.CLOSE_SETTINGS,
                dockTabTapAction(route, activeTab = "playlists", tappedTab = "playlists"),
            )
        }
    }

    @Test fun switchingFromSettingsClearsSettingsBeforeRestoringAnotherTab() {
        for (route in listOf("settings", "downloads", "history", "telegram-auth")) {
            assertEquals(
                DockTabTapAction.SWITCH_FROM_SETTINGS,
                dockTabTapAction(route, activeTab = "library", tappedTab = "queue"),
            )
        }
    }

    @Test fun topLevelTabMotionRespectsDockOrderInBothDirections() {
        assertEquals(1f, retainedTabOffsetFraction("library", "home"), 0f)
        assertEquals(-1f, retainedTabOffsetFraction("home", "library"), 0f)
        assertEquals(1f, retainedTabOffsetFraction("queue", "playlists"), 0f)
        assertEquals(-1f, retainedTabOffsetFraction("playlists", "queue"), 0f)
    }

    @Test fun selectedTabHasNoOffsetAndSettingsDoesNotShiftCachedTabs() {
        for (route in listOf("home", "library", "playlists", "queue")) {
            assertEquals(0f, retainedTabOffsetFraction(route, route), 0f)
            assertEquals(0f, retainedTabOffsetFraction(route, "settings"), 0f)
            assertEquals(0f, retainedTabOffsetFraction(route, "telegram-auth"), 0f)
        }
    }

    @Test fun regularDockReselectionDoesNotBehaveAsSettingsBack() {
        assertEquals(DockTabTapAction.RESELECT, dockTabTapAction("library", "library", "library"))
        assertEquals(DockTabTapAction.SWITCH, dockTabTapAction("queue", "queue", "home"))
    }
}
