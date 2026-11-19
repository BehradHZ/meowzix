package dev.behradhz.meowzix.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialSourceChoicePolicyTest {
    @Test
    fun pickerRemainsVisibleWhileBothSourcesAreUnavailable() {
        assertTrue(shouldShowInitialSourceChoice(
            telegramConnected = false,
            localAudioPermissionGranted = false,
        ))
    }

    @Test
    fun connectingTelegramHidesThePickerWithoutLocalAudioAccess() {
        assertFalse(shouldShowInitialSourceChoice(
            telegramConnected = true,
            localAudioPermissionGranted = false,
        ))
    }

    @Test
    fun grantingLocalAudioAccessHidesThePickerWithoutTelegram() {
        assertFalse(shouldShowInitialSourceChoice(
            telegramConnected = false,
            localAudioPermissionGranted = true,
        ))
    }

    @Test
    fun bothSourcesReadyKeepNormalHomeVisible() {
        assertFalse(shouldShowInitialSourceChoice(
            telegramConnected = true,
            localAudioPermissionGranted = true,
        ))
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
