package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.domain.settings.selectionLabel
import dev.behradhz.meowzix.navigation.selectionActionIcon
import dev.behradhz.meowzix.ui.components.MeowzixHeaderAction

/** Anchored frosted popup with the same single-surface treatment as Track Actions. */
@Composable
internal fun LibrarySelectionMoreMenu(
    actions: List<LibrarySelectionAction>,
    onAction: (LibrarySelectionAction) -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onAddToLibrary: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
    allSelectedLiked: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val surface = MaterialTheme.colorScheme.surface
    Box {
        MeowzixHeaderAction("More selection actions", Icons.Rounded.MoreVert, { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(276.dp),
            offset = DpOffset(x = (-8).dp, y = 0.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                Modifier.fillMaxWidth().clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                surface.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
                                surface.copy(alpha = 0.995f),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    .testTag("glass-library-selection-menu"),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "More options",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 8.dp),
                    )
                    if (onSelectAll != null) {
                        DropdownMenuItem(
                            text = { Text("Select / deselect all") },
                            leadingIcon = { Icon(Icons.Rounded.SelectAll, contentDescription = null) },
                            onClick = { expanded = false; onSelectAll() },
                        )
                    }
                    if (onAddToLibrary != null) DropdownMenuItem(
                        text = { Text("Add selected to Library") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = { expanded = false; onAddToLibrary() },
                    )
                    if (onRemoveFromLibrary != null) DropdownMenuItem(
                        text = { Text("Remove selected from Library") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = { expanded = false; onRemoveFromLibrary() },
                    )
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.selectionLabel(allSelectedLiked)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = selectionActionIcon(action, allSelectedLiked),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                expanded = false
                                onAction(action)
                            },
                        )
                    }
                    if (actions.isEmpty() && onSelectAll == null) {
                        DropdownMenuItem(
                            text = { Text("Configure in Settings") },
                            onClick = { expanded = false },
                        )
                    }
                }
            }
        }
    }
}
