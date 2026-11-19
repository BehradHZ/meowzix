package dev.behradhz.meowzix.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.domain.settings.LibrarySelectionSettings
import dev.behradhz.meowzix.domain.settings.selectionLabel
import dev.behradhz.meowzix.navigation.selectionActionIcon
import kotlin.math.roundToInt

/** A live configuration preview; it never issues real selection or playback commands. */
@Composable
internal fun SettingsSelectionConfigurator(
    settings: LibrarySelectionSettings,
    reduceMotion: Boolean,
    onDockSlot: (Int, LibrarySelectionAction) -> Unit,
    onMoreEnabled: (LibrarySelectionAction, Boolean) -> Unit,
    onMoreMove: (LibrarySelectionAction, Int) -> Unit,
) {
    val selection = settings.normalized()
    val colors = MaterialTheme.colorScheme
    val duration = if (reduceMotion) 0 else 230
    var activeSlot by rememberSaveable { mutableIntStateOf(0) }
    val slot = activeSlot.coerceIn(0, 3)
    val chosen = selection.dock[slot]

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text("Tap a dock position to change its action.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.65f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(25.dp))
                .padding(5.dp).testTag("selection-settings-dock"),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            selection.dock.forEachIndexed { index, action ->
                val active = slot == index
                val opacity by animateFloatAsState(
                    if (active) 1f else 0.38f, tween(duration), label = "selection-slot-opacity")
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(19.dp))
                        .background(if (active) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable(role = Role.Button) { activeSlot = index }
                        .testTag("selection-settings-slot-$index")
                        .padding(horizontal = 2.dp, vertical = 13.dp).alpha(opacity),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(selectionActionIcon(action), null, modifier = Modifier.size(24.dp))
                    Text(action.selectionLabel(false), style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text("Available actions", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .testTag("selection-settings-action-picker"),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            LibrarySelectionAction.entries.forEach { action ->
                val active = chosen == action
                val shape = RoundedCornerShape(18.dp)
                Column(
                    modifier = Modifier.width(91.dp).clip(shape)
                        .background(if (active) colors.primaryContainer.copy(alpha = 0.65f)
                            else colors.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.dp, if (active) colors.primary.copy(alpha = 0.65f)
                            else colors.outlineVariant.copy(alpha = 0.28f), shape)
                        .clickable(role = Role.Button) { onDockSlot(slot, action) }
                        .testTag("selection-settings-option-" + action.name.lowercase())
                        .padding(horizontal = 5.dp, vertical = 13.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(selectionActionIcon(action), null, modifier = Modifier.size(24.dp),
                        tint = if (active) colors.primary else colors.onSurface)
                    Text(action.label, style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
                        lineHeight = 12.sp, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AnimatedContent(
            targetState = chosen,
            transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
            label = "selection-settings-description",
        ) { action ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(action.label, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = colors.primary)
                Text(selectionActionDescription(action), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
        }
        SettingsRowDivider()
        Text("Selection menu", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        Text("An upper-screen preview with More open. Toggle actions or drag their handles to reorder.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        SettingsSelectionMorePreview(selection, reduceMotion, onMoreEnabled, onMoreMove)
    }
}

@Composable
private fun SettingsSelectionMorePreview(
    selection: LibrarySelectionSettings,
    reduceMotion: Boolean,
    onEnabled: (LibrarySelectionAction, Boolean) -> Unit,
    onMove: (LibrarySelectionAction, Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by rememberSaveable { mutableStateOf(true) }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier.fillMaxWidth().height(398.dp)
            .clip(shape).background(colors.surface.copy(alpha = 0.42f))
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
            .testTag("selection-settings-more-preview"),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Selected 3", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(CircleShape).clickable(role = Role.Button) { menuOpen = !menuOpen }
                        .testTag("selection-settings-menu-trigger").padding(8.dp),
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Toggle preview More menu")
                }
            }
            listOf("A quiet beginning", "After the rain", "Better days").forEach { title ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(Modifier.size(22.dp).background(colors.primary, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = colors.onPrimary,
                            modifier = Modifier.size(15.dp))
                    }
                    Box(Modifier.size(38.dp).background(colors.surfaceVariant,
                        RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, null, tint = colors.onSurfaceVariant)
                    }
                    Text(title, modifier = Modifier.weight(1f), maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        AnimatedVisibility(
            visible = menuOpen,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 8.dp),
            enter = fadeIn(tween(if (reduceMotion) 0 else 190)) + expandVertically(),
            exit = fadeOut(tween(if (reduceMotion) 0 else 130)) + shrinkVertically(),
        ) {
            val menuShape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier.width(278.dp).clip(menuShape)
                    .background(Brush.verticalGradient(listOf(
                        colors.surface.copy(alpha = 0.98f),
                        colors.surfaceVariant.copy(alpha = 0.98f))))
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.7f), menuShape)
                    .padding(vertical = 6.dp),
            ) {
                Text("More options", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 288.dp),
                ) {
                    itemsIndexed(selection.moreOrder, key = { _, action -> action.name }) { index, action ->
                        Box(Modifier.animateItem()) {
                            SettingsMoreActionRow(
                                action = action, index = index, count = selection.moreOrder.size,
                                enabled = action in selection.more,
                                reduceMotion = reduceMotion,
                                onEnabled = { onEnabled(action, it) },
                                onMove = { onMove(action, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMoreActionRow(
    action: LibrarySelectionAction,
    index: Int,
    count: Int,
    enabled: Boolean,
    reduceMotion: Boolean,
    onEnabled: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val rowPx = with(LocalDensity.current) { 54.dp.toPx() }
    var dragging by remember(action) { mutableFloatStateOf(0f) }
    val opacity by animateFloatAsState(
        if (enabled) 1f else 0.5f, tween(if (reduceMotion) 0 else 180),
        label = "selection-menu-row-opacity",
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp)
            .zIndex(if (dragging != 0f) 1f else 0f)
            .graphicsLayer { translationY = dragging }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(selectionActionIcon(action), null, modifier = Modifier.size(20.dp).alpha(opacity))
        Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
            modifier = Modifier.weight(1f).alpha(opacity))
        Switch(checked = enabled, onCheckedChange = onEnabled,
            modifier = Modifier.testTag("selection-settings-more-toggle-" + action.name.lowercase()))
        Icon(
            Icons.Rounded.DragHandle, contentDescription = "Reorder " + action.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
                .testTag("selection-settings-more-handle-" + action.name.lowercase())
                .pointerInput(action, index, count) {
                    detectDragGestures(
                        onDragStart = { dragging = 0f },
                        onDragCancel = { dragging = 0f },
                        onDragEnd = {
                            val target = (index + (dragging / rowPx).roundToInt()).coerceIn(0, count - 1)
                            if (target != index) onMove(target)
                            dragging = 0f
                        },
                    ) { change, delta ->
                        change.consume()
                        dragging += delta.y
                    }
                },
        )
    }
}

internal fun selectionActionDescription(action: LibrarySelectionAction): String = when (action) {
    LibrarySelectionAction.DELETE -> "Remove selected tracks from the library without deleting their source files."
    LibrarySelectionAction.PLAY_NEXT -> "Put selected tracks immediately after the currently playing song."
    LibrarySelectionAction.ADD_TO_PLAYLIST -> "Choose a playlist to receive the selected tracks."
    LibrarySelectionAction.SHARE -> "Forward the selected tracks through Telegram."
    LibrarySelectionAction.ADD_TO_QUEUE -> "Append the selected tracks to the playback queue."
    LibrarySelectionAction.DOWNLOAD -> "Keep the selected tracks available offline."
    LibrarySelectionAction.LIKE -> "Like every unliked song. If all are liked, unlike them all. Selection stays active."
    LibrarySelectionAction.PLAY_SELECTED -> "Play the selected tracks in their current order."
    LibrarySelectionAction.SHUFFLE -> "Play the selected tracks in random order."
}
