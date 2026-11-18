package dev.behradhz.meowzix.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction

/** Route-local state for the shared morphing dock. The active screen owns its selection. */
class ContextualSelectionController {
    var active by mutableStateOf(false)
    var onAction: (LibrarySelectionAction) -> Unit = {}
}
