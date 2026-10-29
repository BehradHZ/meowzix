package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import dev.behradhz.meowzix.domain.lyrics.LyricsVersion
import java.util.UUID

/** Cross-window hit testing for the anchored menu's press-drag-release gesture. */
internal fun draggedPlayerAction(screenPoint: Offset, rowBounds: Map<Int, Rect>, horizontalTolerancePx: Float = 24f): Int? =
    rowBounds.entries.firstOrNull { (_, rect) ->
        screenPoint.y >= rect.top && screenPoint.y <= rect.bottom &&
            screenPoint.x >= rect.left - horizontalTolerancePx &&
            screenPoint.x <= rect.right + horizontalTolerancePx
    }?.key

internal enum class PlayerActionsPage { ACTIONS, LYRICS }

/** Two-page anchored glass popup, matching Library's track-actions menu. */
@Composable
internal fun PlayerActionsMenu(
    expanded: Boolean,
    trackId: UUID?,
    lyricsState: NowPlayingLyricsState,
    lyricsExpanded: Boolean,
    sleepTimerLabel: String,
    onDismiss: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onWhyThisSong: () -> Unit,
    onContinueVibe: () -> Unit,
    onToggleLyrics: () -> Unit,
    onImportLrc: () -> Unit,
    onPasteLyrics: () -> Unit,
    onAdjustDelay: (Long) -> Unit,
    onSelectVersion: (UUID) -> Unit,
    onDeleteVersion: (UUID) -> Unit,
    onRemoveAllLyrics: () -> Unit = {},
    onRestoreAutomaticLyrics: () -> Unit = {},
    sleepTimerActive: Boolean = false,
    sleepTimerCanEnable: Boolean = true,
    onSleepTimerEnabledChange: (Boolean) -> Unit = {},
    dragInProgress: Boolean = false,
    draggedAction: Int? = null,
    draggedLyricsRequest: Int = 0,
    onActionBoundsChanged: (Int, Rect) -> Unit = { _, _ -> },
) {
    var page by remember(trackId) { mutableStateOf(PlayerActionsPage.ACTIONS) }
    var pendingDelete by remember(trackId) { mutableStateOf<LyricsVersion?>(null) }
    var pendingRemoveAll by remember(trackId) { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) page = PlayerActionsPage.ACTIONS }
    LaunchedEffect(draggedLyricsRequest) {
        if (draggedLyricsRequest > 0) page = PlayerActionsPage.LYRICS
    }
    val accent = MaterialTheme.colorScheme.primary
    val glass = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(18.dp)
    val versions = lyricsState.trackLyrics?.versions.orEmpty()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(288.dp),
        offset = DpOffset(x = (-8).dp, y = 0.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        // The originating gesture must remain on the trigger until the finger is released.
        properties = PopupProperties(focusable = !dragInProgress),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.verticalGradient(listOf(
                    glass.copy(alpha = 0.92f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
                    glass.copy(alpha = 0.995f),
                )))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .testTag("glass-player-actions-menu"),
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "player-actions-page",
            ) { currentPage ->
                if (currentPage == PlayerActionsPage.ACTIONS) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("player-actions-page"),
                    ) {
                        Text(
                            "More options",
                            modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // Full-width rows keep labels independent, readable and clickable even
                        // with large accessibility fonts. The timer status belongs to its action.
                        PlayerMenuOptionRow(
                            icon = Icons.Rounded.Schedule,
                            title = "Sleep timer",
                            highlighted = draggedAction == 0,
                            onBoundsChanged = { onActionBoundsChanged(0, it) },
                            subtitle = sleepTimerLabel,
                            switchChecked = sleepTimerActive,
                            switchEnabled = sleepTimerCanEnable,
                            onSwitchCheckedChange = { enabled ->
                                onDismiss()
                                onSleepTimerEnabledChange(enabled)
                            },
                            onClick = { onDismiss(); onOpenSleepTimer() },
                        )
                        PlayerMenuOptionRow(
                            icon = Icons.Rounded.HelpOutline,
                            title = "Why this song?",
                            highlighted = draggedAction == 1,
                            onBoundsChanged = { onActionBoundsChanged(1, it) },
                            onClick = { onDismiss(); onWhyThisSong() },
                        )
                        PlayerMenuOptionRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Continue the vibe",
                            highlighted = draggedAction == 2,
                            onBoundsChanged = { onActionBoundsChanged(2, it) },
                            onClick = { onDismiss(); onContinueVibe() },
                        )
                        PlayerMenuOptionRow(
                            icon = Icons.Rounded.MusicNote,
                            title = "Lyrics",
                            highlighted = draggedAction == 3,
                            onBoundsChanged = { onActionBoundsChanged(3, it) },
                            subtitle = if (versions.isEmpty()) "Add lyrics" else
                                versions.size.toString() + " version" + (if (versions.size == 1) "" else "s"),
                            onClick = { page = PlayerActionsPage.LYRICS },
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("player-lyrics-page")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { page = PlayerActionsPage.ACTIONS },
                                modifier = Modifier.testTag("player-lyrics-back")) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to player actions")
                            }
                            Text("Lyrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(13.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
                                .padding(horizontal = 8.dp),
                        ) {
                            PlayerActionLine(Icons.Rounded.Add, "Import .lrc") { onDismiss(); onImportLrc() }
                            PlayerActionLine(Icons.Rounded.Description, "Paste lyrics") { onDismiss(); onPasteLyrics() }
                            PlayerActionLine(Icons.Rounded.Visibility,
                                if (lyricsExpanded) "Back to player" else "Show lyrics",
                            ) { onDismiss(); onToggleLyrics() }
                        }
                        Spacer(Modifier.heightIn(min = 10.dp))
                        Text("SYNC TIMING", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                        val timed = lyricsState.selected?.parsed?.contentType == LyricsContentType.TIMED
                        val delay = lyricsState.selected?.userDelayMs ?: 0L
                        Row(
                            Modifier.fillMaxWidth()
                                .background(accent.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            PlayerDelayButton(Icons.Rounded.Remove, "Lyrics earlier by 250 milliseconds",
                                "player-lyrics-delay-minus", timed) { onAdjustDelay(-250L) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Delay " + (if (delay >= 0L) "+" else "") + delay + " ms",
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (timed) "250 ms steps" else "Timed lyrics only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PlayerDelayButton(Icons.Rounded.Add, "Lyrics later by 250 milliseconds",
                                "player-lyrics-delay-plus", timed) { onAdjustDelay(250L) }
                        }
                        Spacer(Modifier.heightIn(min = 10.dp))
                        Text("VERSIONS", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                        if (versions.size > 1) {
                            val index = versions.indexOfFirst { it.id == lyricsState.selected?.id }.coerceAtLeast(0)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onSelectVersion(versions[(index + versions.size - 1) % versions.size].id) },
                                    modifier = Modifier.testTag("player-lyrics-previous-version"),
                                ) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous lyrics version") }
                                Text((index + 1).toString() + " of " + versions.size,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(
                                    onClick = { onSelectVersion(versions[(index + 1) % versions.size].id) },
                                    modifier = Modifier.testTag("player-lyrics-next-version"),
                                ) { Icon(Icons.Rounded.ChevronRight, contentDescription = "Next lyrics version") }
                            }
                        }
                        if (versions.isEmpty()) {
                            Text(if (lyricsState.trackLyrics?.automaticDiscoveryDisabled == true)
                                    "Lyrics removed from this track." else
                                    "No lyrics yet. Import an .lrc file or paste text.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp).testTag("player-lyrics-empty"))
                        } else {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 212.dp)
                                    .verticalScroll(rememberScrollState())
                                    .testTag("player-lyrics-versions"),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                versions.forEach { version ->
                                    val name = version.displayName()
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                            .background(accent.copy(alpha =
                                                if (version.id == lyricsState.selected?.id) 0.13f else 0.04f))
                                            .clickable { onSelectVersion(version.id) }
                                            .padding(start = 10.dp, end = 2.dp)
                                            .testTag("player-lyrics-version-" + version.id),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium)
                                            Text(if (version.id == lyricsState.selected?.id) "Selected" else
                                                if (version.sourceType == LyricsSourceType.EMBEDDED) "Auto-detected · read-only" else
                                                    version.sourceType.name.lowercase().replace('_', ' '),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (version.id == lyricsState.selected?.id) {
                                            Icon(Icons.Rounded.Check, contentDescription = null,
                                                tint = accent, modifier = Modifier.size(18.dp))
                                        }
                                        if (version.sourceType != LyricsSourceType.EMBEDDED) IconButton(
                                            onClick = { pendingDelete = version },
                                            modifier = Modifier.testTag("player-lyrics-delete-" + version.id),
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete lyrics " + name,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.heightIn(min = 10.dp))
                        if (versions.isNotEmpty()) {
                            PlayerActionLine(
                                icon = Icons.Rounded.DeleteOutline,
                                title = "Remove lyrics from track",
                            ) { pendingRemoveAll = true }
                            Text(
                                "Removes all lyrics in Meowzix, not files on your device.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
                            )
                        }
                        if (lyricsState.trackLyrics?.automaticDiscoveryDisabled == true) {
                            PlayerActionLine(
                                icon = Icons.Rounded.Refresh,
                                title = "Restore detected lyrics",
                            ) {
                                onDismiss()
                                onRestoreAutomaticLyrics()
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingRemoveAll) {
        AlertDialog(
            onDismissRequest = { pendingRemoveAll = false },
            title = { Text("Remove lyrics from track?") },
            text = { Text("All lyrics versions will be removed from Meowzix. The song and any original .lrc files on your device will remain untouched. Auto-detection will be disabled for this track until restored.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoveAll = false
                    onRemoveAllLyrics()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveAll = false }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { version ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete lyrics?") },
            text = { Text("Remove \"" + version.displayName() + "\" from this track? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteVersion(version.id) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun LyricsVersion.displayName(): String =
    if (sourceType == LyricsSourceType.EMBEDDED) "Embedded lyrics"
    else sourceLabel?.takeIf(String::isNotBlank) ?: when (sourceType) {
        LyricsSourceType.USER_LRC -> "Imported .lrc"
        LyricsSourceType.USER_PASTE -> "Pasted lyrics"
        LyricsSourceType.EMBEDDED -> "Embedded lyrics"
    }

@Composable
private fun PlayerDelayButton(
    icon: ImageVector, label: String, tag: String, enabled: Boolean, onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(40.dp).testTag(tag),
        shape = CircleShape,
        color = accent.copy(alpha = if (enabled) 0.16f else 0.06f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label,
                tint = accent.copy(alpha = if (enabled) 1f else 0.4f))
        }
    }
}

/** A consistent, accessible row for every primary Now Playing menu action. */
@Composable
private fun PlayerMenuOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    highlighted: Boolean = false,
    onBoundsChanged: ((Rect) -> Unit)? = null,
    switchChecked: Boolean? = null,
    switchEnabled: Boolean = true,
    onSwitchCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (highlighted) colors.primary.copy(alpha = 0.17f) else Color.Transparent)
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.positionOnScreen()
                if (topLeft != Offset.Unspecified) {
                    onBoundsChanged?.invoke(
                        Rect(
                            topLeft.x,
                            topLeft.y,
                            topLeft.x + coordinates.size.width.toFloat(),
                            topLeft.y + coordinates.size.height.toFloat(),
                        ),
                    )
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("player-action-" + title.lowercase().replace(' ', '-').replace("?", "")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp)
                .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (switchChecked != null && onSwitchCheckedChange != null) {
            // Separate hit target: toggling does not trigger the row's settings action.
            Switch(
                checked = switchChecked,
                enabled = switchEnabled,
                onCheckedChange = onSwitchCheckedChange,
                modifier = Modifier.testTag("player-sleep-timer-switch"),
            )
        } else {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerActionLine(
    icon: ImageVector, title: String, subtitle: String? = null,
    trailing: Boolean = false, enabled: Boolean = true, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("player-action-" + title.lowercase().replace(' ', '-')),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
                fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing) Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}
