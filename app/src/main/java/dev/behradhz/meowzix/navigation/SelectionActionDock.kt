package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction

/** Only contextual content, never a second glass dock: Search keeps its shared morph. */
@Composable
internal fun SelectionDockActions(
    actions: List<LibrarySelectionAction>,
    onAction: (LibrarySelectionAction) -> Unit,
    enabled: Boolean = true,
) {
    if (actions.size != 4) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .testTag("selection-dock-" + action.name.lowercase())
                        .clickable(enabled = enabled, role = Role.Button) { onAction(action) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = when (action) {
                            LibrarySelectionAction.DELETE -> Icons.Rounded.DeleteOutline
                            LibrarySelectionAction.PLAY_NEXT -> Icons.Rounded.PlaylistPlay
                            LibrarySelectionAction.ADD_TO_PLAYLIST -> Icons.Rounded.PlaylistAdd
                            LibrarySelectionAction.SHARE -> Icons.Rounded.Send
                            LibrarySelectionAction.ADD_TO_QUEUE -> Icons.Rounded.QueueMusic
                            LibrarySelectionAction.DOWNLOAD -> Icons.Rounded.Download
                            LibrarySelectionAction.LIKE -> Icons.Rounded.Favorite
                            LibrarySelectionAction.UNLIKE -> Icons.Rounded.FavoriteBorder
                            LibrarySelectionAction.PLAY_SELECTED -> Icons.Rounded.PlayArrow
                            LibrarySelectionAction.SHUFFLE -> Icons.Rounded.Shuffle
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = when (action) {
                            LibrarySelectionAction.ADD_TO_PLAYLIST -> "Add to\nplaylist"
                            else -> action.label
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
