package dev.behradhz.meowzix.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialSourceChoicePolicyTest {
    @Test
    fun newInstallShowsChoiceAfterHomeIsReady() {
        assertFalse(
            shouldShowInitialSourceChoice(
                isReady = false,
                isCompleted = false,
                telegramConnected = false,
                hasExistingLibrary = false,
            ),
        )
        assertTrue(
            shouldShowInitialSourceChoice(
                isReady = true,
                isCompleted = false,
                telegramConnected = false,
                hasExistingLibrary = false,
            ),
        )
    }

    @Test
    fun completedChoiceIsNeverShownAgain() {
        assertFalse(
            shouldShowInitialSourceChoice(
                isReady = true,
                isCompleted = true,
                telegramConnected = false,
                hasExistingLibrary = false,
            ),
        )
    }

    @Test
    fun existingLibraryOrTelegramAccountSkipsFirstLaunchChoice() {
        assertFalse(
            shouldShowInitialSourceChoice(
                isReady = true,
                isCompleted = false,
                telegramConnected = false,
                hasExistingLibrary = true,
            ),
        )
        assertFalse(
            shouldShowInitialSourceChoice(
                isReady = true,
                isCompleted = false,
                telegramConnected = true,
                hasExistingLibrary = false,
            ),
        )
    }
}
