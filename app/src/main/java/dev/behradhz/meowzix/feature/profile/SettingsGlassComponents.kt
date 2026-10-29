package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.ui.theme.LocalMeowzixLogoBackground
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.feature.library.PlaylistGlassPanel
import dev.behradhz.meowzix.domain.settings.PlayerSeekBarStyle
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.ui.components.SyntheticWaveformSeekBar
import dev.behradhz.meowzix.ui.components.CurlyMusicSlider
import dev.chrisbanes.haze.HazeState


/**
 * Both preview controls are interactive, but never issue playback commands.
 * Live FFT is requested only while the waveform card is inside the viewport.
 */
@Composable
internal fun SettingsSeekBarStylePicker(
    selected: PlayerSeekBarStyle,
    onSelect: (PlayerSeekBarStyle) -> Unit,
    playback: PlaybackState = PlaybackState(),
    spectrum: AudioSpectrumState = AudioSpectrumState(),
    reduceMotion: Boolean = false,
    onPreviewCaptureChange: (Boolean) -> Unit = {},
) {
    val demoBands = remember { FloatArray(24) { index -> 0.18f + ((index * 11) % 15) / 19f } }
    val playing = playback.status == PlaybackStatus.PLAYING && playback.currentTrack != null
    val trackKey = if (playing) playback.currentTrack!!.id.toString() else "settings-demo"
    val playbackFraction = if (playing && playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else 0.42f
    val hasLiveCapture = playing && !spectrum.sourceUri.isNullOrBlank()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Seekbar appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        listOf(
            Triple(PlayerSeekBarStyle.WAVEFORM, "Live equalizer", "Bars react to the actual music"),
            Triple(PlayerSeekBarStyle.CLASSIC, "Android wave", "A smooth animated wave while playing"),
        ).forEach { (style, title, description) ->
            // Track changes reset the demo scrub; user scrubbing never changes PlaybackController.
            var scrubPosition by remember(style, trackKey) { mutableStateOf<Float?>(null) }
            var cardVisible by remember(style) { mutableStateOf(false) }
            val chosen = selected == style
            if (style == PlayerSeekBarStyle.WAVEFORM) {
                LaunchedEffect(cardVisible, playing) {
                    onPreviewCaptureChange(cardVisible && playing)
                }
                DisposableEffect(Unit) {
                    onDispose { onPreviewCaptureChange(false) }
                }
            }
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val clipped = coordinates.boundsInWindow()
                        cardVisible = clipped.width > 0f && clipped.height > 0f
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(role = Role.RadioButton) { onSelect(style) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RadioButton(selected = chosen, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val previewPosition = scrubPosition ?: playbackFraction
                if (style == PlayerSeekBarStyle.WAVEFORM) {
                    SyntheticWaveformSeekBar(
                        trackKey = trackKey,
                        liveBands = if (playing) {
                            if (hasLiveCapture) spectrum.bands else FloatArray(0)
                        } else demoBands,
                        value = previewPosition,
                        onValueChange = { scrubPosition = it },
                        valueRange = 0f..1f,
                        isPlaying = cardVisible && (playing || !reduceMotion),
                        barCount = 40,
                        modifier = Modifier.fillMaxWidth(),
                        reduceMotion = reduceMotion,
                        accessibilityLabel = "Live equalizer preview",
                    )
                } else {
                    CurlyMusicSlider(
                        value = previewPosition,
                        onValueChange = { scrubPosition = it },
                        valueRange = 0f..1f,
                        isPlaying = cardVisible && (playing || !reduceMotion),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Consistent hierarchy: section title 18sp; setting label 15sp; supporting text 12sp.
 * Keep a single frosted surface per section rather than stacking glass cards.
 */
@Composable
internal fun SettingsGlassSection(
    hazeState: HazeState,
    title: String,
    description: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(34.dp).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    RoundedCornerShape(12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlaylistGlassPanel(hazeState, modifier = Modifier.fillMaxWidth(), radius = 26.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp)) {
                content()
            }
        }
    }
}

/** Meowzix identity is integrated with settings, not presented as a second Profile screen. */
@Composable
internal fun SettingsGlassHero(
    hazeState: HazeState,
    connected: Boolean,
    smartEnabled: Boolean,
    downloads: DownloadSettingsSummary,
    onOpenBrand: () -> Unit = {},
) {
    PlaylistGlassPanel(hazeState, modifier = Modifier.fillMaxWidth(), accented = true, radius = 32.dp) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(78.dp)
                        .background(LocalMeowzixLogoBackground.current, RoundedCornerShape(25.dp))
                        .clickable(role = Role.Button, onClick = onOpenBrand)
                        .padding(7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.meowzix_launcher_art),
                        contentDescription = "Open Meowzix cat fullscreen",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "YOUR SPACE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp, letterSpacing = 1.4.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Meowzix",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp, lineHeight = 29.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Music that feels like yours.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SettingsStatusChip(
                    if (connected) "Telegram connected" else "Telegram offline",
                    Modifier.weight(1f),
                )
                SettingsStatusChip(
                    if (smartEnabled) "Discovery on" else "Discovery off",
                    Modifier.weight(1f),
                )
            }
            Text(
                "${downloads.activeCount} active downloads  ·  ${downloads.pinnedCount} available offline",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsStatusChip(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            Icons.Rounded.AutoAwesome, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(start = 59.dp, end = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun SettingsLeadingIcon(icon: ImageVector, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(38.dp).background(color.copy(alpha = 0.10f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SettingsRowLabel(title: String, subtitle: String?, modifier: Modifier = Modifier, danger: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
            fontWeight = FontWeight.Medium,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 11.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLeadingIcon(icon)
        SettingsRowLabel(title, subtitle, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun SettingsChoiceRow(title: String, subtitle: String?, icon: ImageVector, current: String, options: List<String>, onChoice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 11.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsLeadingIcon(icon)
            SettingsRowLabel(title, subtitle, Modifier.weight(1f))
            Text(current, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.lowercase().replaceFirstChar(Char::uppercase)) }, onClick = {
                    expanded = false
                    onChoice(option)
                })
            }
        }
    }
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValue: (Float) -> Unit,
    enabled: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsLeadingIcon(icon)
            SettingsRowLabel(title, subtitle, Modifier.weight(1f))
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            enabled = enabled,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth().padding(start = 50.dp, end = 6.dp).semantics {
                contentDescription = title
                stateDescription = valueLabel
            },
        )
    }
}

@Composable
internal fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLeadingIcon(icon)
        SettingsRowLabel(title, subtitle, Modifier.weight(1f))
        Text(actionLabel, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SettingsInfoRow(title: String, subtitle: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 11.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLeadingIcon(icon)
        SettingsRowLabel(title, subtitle, Modifier.weight(1f))
    }
}

@Composable
internal fun SettingsDangerRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLeadingIcon(icon, danger = true)
        SettingsRowLabel(title, subtitle, Modifier.weight(1f), danger = true)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
    }
}

internal enum class SettingsDangerAction(val title: String, val message: String, val confirm: String) {
    CLEAR_CACHE(
        "Clear temporary cache?",
        "Only evictable app-managed cache will be cleared. Pinned offline and MediaStore files are preserved.",
        "Clear cache",
    ),
    CLEAR_HISTORY(
        "Clear listening history?",
        "This deletes raw listening events and rebuild-derived state from that history. This cannot be undone.",
        "Clear history",
    ),
    RESET_PERSONALIZATION(
        "Reset personalization?",
        "Learned recommendation state and feedback will be reset. Your music library is preserved; listening history is handled separately.",
        "Reset",
    ),
    DISCONNECT_TELEGRAM(
        "Disconnect Telegram?",
        "Your Telegram session will be disconnected. Local and previously available offline music remain usable.",
        "Disconnect",
    ),
    CLEAR_FEEDBACK(
        "Clear recommendation feedback?",
        "All active recommendation feedback will be cleared. You can provide feedback again later.",
        "Clear feedback",
    ),
}
