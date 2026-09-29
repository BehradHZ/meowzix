package dev.behradhz.meowzix.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun criticalUserJourney() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Home -> Library exercises navigation, Room Paging setup, first page composition and row
        // rendering. Text selectors are stable product labels in the current English-only shell.
        device.findObject(By.text("Library"))?.click()
        device.waitForIdle()
        repeat(4) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                12,
            )
            device.waitForIdle()
        }

        // Exercise aggregate-backed library surfaces. These remain useful profile paths even for an
        // empty fixture because navigation/composition and DAO setup are deterministic.
        device.findObject(By.text("Artists"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Albums"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Tracks"))?.click()
        device.waitForIdle()

        // Search initializes the DB-backed FTS route; Queue covers the playback/queue UI path.
        device.findObject(By.text("Search"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Queue"))?.click()
        device.waitForIdle()

        // Return through Library so profile collection includes the warm navigation path too.
        device.findObject(By.text("Library"))?.click()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "dev.behradhz.meowzix"
    }
}
