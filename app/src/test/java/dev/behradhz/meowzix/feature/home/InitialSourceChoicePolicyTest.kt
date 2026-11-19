package dev.behradhz.meowzix.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialSourceChoicePolicyTest {
    @Test
    fun freshInstallShowsChoiceOnlyAfterHomeIsReady() {
        assertFalse(
            shouldShowInitialSourceChoice(
                isReady = false,
                isFirstAppLaunch = true,
                isCompleted = false,
                telegramConnected = false,
            ),
        )
        assertTrue(
            shouldShowInitialSourceChoice(
                isReady = true,
                isFirstAppLaunch = true,
                isCompleted = false,
                telegramConnected = false,
            ),
        )
    }

    @Test
    fun existingInstallDoesNotShowFirstLaunchChoice() {
        assertFalse(
            shouldShowInitialSourceChoice(
                isReady = true,
                isFirstAppLaunch = false,
                isCompleted = false,
                telegramConnected = false,
            ),
        )
    }

    @Test
    fun completedChoiceIsNeverShownAgain() {
        assertFalse(
            shouldShowInitialSourceChoice(
                isReady = true,
                isFirstAppLaunch = true,
                isCompleted = true,
                telegramConnected = false,
            ),
        )
    }

    @Test
    fun alreadyConnectedTelegramAccountSkipsTheChoice() {
        assertFalse(
            shouldShowInitialSourceChoice(
                isReady = true,
                isFirstAppLaunch = true,
                isCompleted = false,
                telegramConnected = true,
            ),
        )
    }
}
