package dev.behradhz.meowzix.feature.telegram

/**
 * A newly collected StateFlow starts closed. Only a close AFTER an observed open should
 * dismiss the notification entry activity. Kept independent of Compose for regression tests.
 */
internal class ForwardPickerVisibilityGate {
    var hasShownPicker: Boolean = false
        private set

    fun shouldFinish(isOpen: Boolean): Boolean {
        if (isOpen) hasShownPicker = true
        return hasShownPicker && !isOpen
    }
}
