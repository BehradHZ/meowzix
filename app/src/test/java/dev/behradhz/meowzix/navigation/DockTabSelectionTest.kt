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

    @Test fun regularDockReselectionDoesNotBehaveAsSettingsBack() {
        assertEquals(DockTabTapAction.RESELECT, dockTabTapAction("library", "library", "library"))
        assertEquals(DockTabTapAction.SWITCH, dockTabTapAction("queue", "queue", "home"))
    }
}
