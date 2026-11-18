package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.ui.components.MeowzixHeaderAction

/** Header overflow uses the user-selected actions, never the four pinned dock actions. */
@Composable
internal fun LibrarySelectionMoreMenu(
    actions: List<LibrarySelectionAction>,
    onAction: (LibrarySelectionAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MeowzixHeaderAction("More selection actions", Icons.Rounded.MoreVert) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (actions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Configure in Settings") },
                    onClick = { expanded = false },
                )
            } else {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            expanded = false
                            onAction(action)
                        },
                    )
                }
            }
        }
    }
}
