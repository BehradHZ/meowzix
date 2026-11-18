package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.ui.components.FloatingChromeGlassSurface
import dev.behradhz.meowzix.ui.components.floatingChromeFrame
import dev.chrisbanes.haze.HazeState

/** Occupies exactly the navigation dock's existing geometry; Search remains accessible. */
@Composable
internal fun SelectionActionDock(
    hazeState: HazeState,
    actions: List<LibrarySelectionAction>,
    onAction: (LibrarySelectionAction) -> Unit,
    onSearch: () -> Unit,
) {
    require(actions.size == 4)
    BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp)) {
        val shape = RoundedCornerShape(36.dp)
        FloatingChromeGlassSurface(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.CenterStart)
                .width(maxWidth - 72.dp).height(72.dp).floatingChromeFrame(shape),
            shape = shape,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    IconButton(
                        onClick = { onAction(action) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = when (action) {
                                LibrarySelectionAction.DELETE -> Icons.Rounded.DeleteOutline
                                LibrarySelectionAction.PLAY_NEXT -> Icons.Rounded.PlaylistPlay
                                LibrarySelectionAction.ADD_TO_PLAYLIST -> Icons.Rounded.PlaylistAdd
                                LibrarySelectionAction.SHARE -> Icons.Rounded.Share
                                LibrarySelectionAction.ADD_TO_QUEUE -> Icons.Rounded.QueueMusic
                                LibrarySelectionAction.DOWNLOAD -> Icons.Rounded.Download
                                LibrarySelectionAction.LIKE -> Icons.Rounded.Favorite
                                LibrarySelectionAction.UNLIKE -> Icons.Rounded.FavoriteBorder
                                LibrarySelectionAction.PLAY_SELECTED -> Icons.Rounded.PlayArrow
                                LibrarySelectionAction.SHUFFLE -> Icons.Rounded.Shuffle
                            },
                            contentDescription = action.label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        FloatingChromeGlassSurface(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.CenterEnd)
                .size(62.dp).floatingChromeFrame(RoundedCornerShape(31.dp))
                .clickable(onClick = onSearch),
            shape = RoundedCornerShape(31.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Search, contentDescription = "Search selected library", modifier = Modifier.size(25.dp))
            }
        }
    }
}
