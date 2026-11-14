package dev.behradhz.meowzix.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupPermissionFlowTest {
    @Test fun firstLaunchPromptsAreOrderedAndExcludeNotifications() {
        var stage = StartupPermissionStage.restore(null)
        val visited = mutableListOf<StartupPermissionStage>()
        while (stage != StartupPermissionStage.COMPLETE) {
            visited += stage
            stage = stage.next()
        }
        assertEquals(
            listOf(
                StartupPermissionStage.INTRO,
                StartupPermissionStage.LOCAL_AUDIO,
                StartupPermissionStage.LIVE_SPECTRUM,
                StartupPermissionStage.OVERLAY,
            ),
            visited,
        )
    }

    @Test fun completedOrDismissedSetupIsNotReplayedAfterRestart() {
        assertEquals(
            StartupPermissionStage.COMPLETE,
            StartupPermissionStage.restore(StartupPermissionStage.COMPLETE.name),
        )
        assertEquals(StartupPermissionStage.COMPLETE, StartupPermissionStage.COMPLETE.next())
        assertEquals(StartupPermissionStage.INTRO, StartupPermissionStage.restore("invalid-old-stage"))
    }
}
