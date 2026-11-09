package dev.behradhz.meowzix.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import dev.behradhz.meowzix.domain.settings.LiveSongCardStyle
import dev.behradhz.meowzix.domain.settings.LiveSongSideAction
import dev.behradhz.meowzix.domain.settings.SleepTimerPresetMode
import dev.behradhz.meowzix.playback.formatLiveSongTime
import dev.behradhz.meowzix.ui.components.sleepTimerPresetLabel
import dev.behradhz.meowzix.ui.components.sleepTimerStatusLabel

internal fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.OFF -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.OFF
}

@Composable
internal fun SettingsPlaybackModeSelector(selected: PlaybackMode, onSelect: (PlaybackMode) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Default playback mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(PlaybackMode.ORDERED, "Ordered", Icons.AutoMirrored.Rounded.QueueMusic),
                Triple(PlaybackMode.PURE_SHUFFLE, "Pure Shuffle", Icons.Rounded.Shuffle),
                Triple(PlaybackMode.SMART_SHUFFLE, "Smart Shuffle", Icons.Rounded.AutoAwesome),
            ).forEach { (mode, name, icon) ->
                val active = selected == mode
                Surface(
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f).heightIn(min = 90.dp)
                        .testTag("settings-playback-mode-${mode.name}")
                        .semantics { stateDescription = if (active) "Selected" else "Not selected" },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.20f else 0.06f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.55f else 0.12f)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp),
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(name, textAlign = TextAlign.Center, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsRepeatCycleRow(selected: RepeatMode, onSelect: (RepeatMode) -> Unit) {
    val icon = if (selected == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
    val name = selected.name.lowercase().replaceFirstChar(Char::uppercase)
    Surface(
        onClick = { onSelect(nextRepeatMode(selected)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 11.dp)
            .heightIn(min = 58.dp).testTag("settings-repeat-cycle"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.17f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f)) {
                Text("Default repeat", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Tap to cycle: Off · One · All", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("settings-repeat-value"))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsLiveUpdateLayoutPicker(
    selected: LiveSongCardStyle,
    onSelect: (LiveSongCardStyle) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("Live Update layout", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LiveSongCardStyle.MINIMAL to "Minimal", LiveSongCardStyle.CONTROLS to "Playback controls")
                .forEach { (style, name) ->
                    val active = selected == style
                    Surface(
                        onClick = { onSelect(style) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("settings-live-layout-${style.name}")
                            .semantics { stateDescription = if (active) "Selected" else "Not selected" },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.19f else 0.06f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.52f else 0.13f)),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                            Text(name, fontSize = 12.sp, textAlign = TextAlign.Center,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
        }
    }
}

@Composable
internal fun SettingsLiveUpdatePreview(
    playback: PlaybackState,
    style: LiveSongCardStyle,
    leftAction: LiveSongSideAction,
    rightAction: LiveSongSideAction,
) {
    val track = playback.currentTrack
    val placeholder = track == null
    val title = track?.title ?: "No song selected"
    val artist = track?.artist?.takeIf { it.isNotBlank() } ?: if (placeholder) "Play a song to see it here" else "Unknown artist"
    val playing = playback.status == PlaybackStatus.PLAYING
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 5.dp)
            .testTag("settings-live-update-preview"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Preview", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("settings-live-update-chip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(
            Modifier.fillMaxWidth().background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                RoundedCornerShape(18.dp),
            ).testTag(if (placeholder) "settings-live-update-placeholder" else "settings-live-update-track")
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.11f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                if (placeholder) "—:— / —:—" else
                    "${formatLiveSongTime(playback.positionMs)} / ${if (playback.durationMs > 0L) formatLiveSongTime(playback.durationMs) else "—:—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            )
            if (style == LiveSongCardStyle.CONTROLS) {
                // Three equal-weight, centered icon surfaces. Transition only the
                // icon, not the row, when Settings changes a side action.
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("settings-live-update-controls"),
                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LivePreviewControl(action = leftAction, tag = "settings-live-left-icon")
                    Box(
                        modifier = Modifier.size(54.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = .19f),
                                RoundedCornerShape(17.dp),
                            )
                            .testTag("settings-live-middle-icon"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Pause,
                            contentDescription = "Pause",
                            modifier = Modifier.size(29.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LivePreviewControl(action = rightAction, tag = "settings-live-right-icon")
                }
            }
        }
        Text(
            when {
                placeholder -> "No active track. The selected layout is ready to preview."
                !playing -> "Playback is paused; the live card is shown only while playing."
                else -> "Preview only · Android controls the final notification layout."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LivePreviewControl(action: LiveSongSideAction, tag: String) {
    Box(
        modifier = Modifier.size(54.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = .055f),
                RoundedCornerShape(17.dp),
            )
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(initialScale = .86f, animationSpec = tween(180)))
                    .togetherWith(
                        fadeOut(tween(110)) + scaleOut(targetScale = 1.08f, animationSpec = tween(130)),
                    )
            },
            label = "live-update-icon-transition",
        ) { current ->
            Icon(
                livePreviewActionIcon(current),
                contentDescription = current.name.lowercase().replace('_', ' '),
                modifier = Modifier.size(29.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun livePreviewActionIcon(action: LiveSongSideAction): ImageVector = when (action) {
    LiveSongSideAction.PREVIOUS -> Icons.Rounded.SkipPrevious
    LiveSongSideAction.NEXT -> Icons.Rounded.SkipNext
    LiveSongSideAction.FAVORITE -> Icons.Rounded.Favorite
    LiveSongSideAction.SHUFFLE -> Icons.Rounded.Shuffle
    LiveSongSideAction.REPEAT -> Icons.Rounded.Repeat
}

@Composable
internal fun SettingsSleepTimerHeader(
    state: SleepTimerState,
    lastMode: SleepTimerPresetMode,
    lastMinutes: Int,
    endOfTrackEnabled: Boolean,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onExpand)
                .testTag("settings-sleep-timer-expand"),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("Sleep timer", style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
            Text(
                if (state.active) sleepTimerStatusLabel(state) else
                    "Last: " + sleepTimerPresetLabel(lastMode, lastMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = state.active,
            enabled = state.active || lastMode != SleepTimerPresetMode.END_OF_TRACK || endOfTrackEnabled,
            onCheckedChange = onToggle,
            modifier = Modifier.testTag("settings-sleep-timer-toggle"),
        )
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Collapse sleep timer" else "Expand sleep timer",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).clickable(onClick = onExpand),
        )
    }
}
