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

    @Test
    fun disconnectedTelegramShowsReminderWhenHomeIsReady() {
        assertTrue(
            shouldShowTelegramConnectReminder(
                isReady = true,
                isConnected = false,
                isInitializing = false,
                isDismissed = false,
                isFirstLaunchChoiceVisible = false,
            ),
        )
    }

    @Test
    fun telegramReminderIsHiddenWhenDismissedOrConnected() {
        assertFalse(
            shouldShowTelegramConnectReminder(
                isReady = true,
                isConnected = false,
                isInitializing = false,
                isDismissed = true,
                isFirstLaunchChoiceVisible = false,
            ),
        )
        assertFalse(
            shouldShowTelegramConnectReminder(
                isReady = true,
                isConnected = true,
                isInitializing = false,
                isDismissed = false,
                isFirstLaunchChoiceVisible = false,
            ),
        )
    }

    @Test
    fun reminderDoesNotFlashDuringInitializationOrSourceChoice() {
        assertFalse(
            shouldShowTelegramConnectReminder(
                isReady = true,
                isConnected = false,
                isInitializing = true,
                isDismissed = false,
                isFirstLaunchChoiceVisible = false,
            ),
        )
        assertFalse(
            shouldShowTelegramConnectReminder(
                isReady = false,
                isConnected = false,
                isInitializing = false,
                isDismissed = false,
                isFirstLaunchChoiceVisible = false,
            ),
        )
        assertFalse(
            shouldShowTelegramConnectReminder(
                isReady = true,
                isConnected = false,
                isInitializing = false,
                isDismissed = false,
                isFirstLaunchChoiceVisible = true,
            ),
        )
    }
}
