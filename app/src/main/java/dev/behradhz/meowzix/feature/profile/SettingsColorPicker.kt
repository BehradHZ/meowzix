package dev.behradhz.meowzix.feature.profile

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.ui.theme.parseCustomColorHex
import dev.behradhz.meowzix.ui.theme.seedArgb

private val availablePalettes = listOf(
    ColorThemePreference.MEOWZIX to "Meowzix",
    ColorThemePreference.WALLPAPER to "Wallpaper",
    ColorThemePreference.CORAL to "Coral",
    ColorThemePreference.AMBER to "Amber",
    ColorThemePreference.OCEAN to "Ocean",
    ColorThemePreference.MINT to "Mint",
    ColorThemePreference.VIOLET to "Violet",
    ColorThemePreference.ROSE to "Rose",
    ColorThemePreference.CUSTOM to "Custom",
)

/** Mode is exclusive, but choosing a preset never discards the saved custom color. */
@Composable
internal fun SettingsColorPicker(
    appearance: AppearanceSettings,
    onSelect: (ColorThemePreference) -> Unit,
    onCustom: (Int) -> Unit,
) {
    val supportsWallpaper = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val wallpaperSwatch = if (supportsWallpaper) {
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else Color(ColorThemePreference.MEOWZIX.seedArgb(appearance.customColorSeed))
    var hex by remember(appearance.customColorSeed) {
        mutableStateOf("%06X".format(appearance.customColorSeed and 0xFFFFFF))
    }
    val candidate = parseCustomColorHex(hex)

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Color palette", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Choose an accent, or match Android's wallpaper colors. Light and dark appearance can be set independently.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        availablePalettes.chunked(3).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { (mode, label) ->
                    val enabled = mode != ColorThemePreference.WALLPAPER || supportsWallpaper
                    val selected = appearance.colorTheme == mode
                    val seed = if (mode == ColorThemePreference.WALLPAPER) wallpaperSwatch else Color(mode.seedArgb(appearance.customColorSeed))
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = 80.dp)
                            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                                onSelect(mode)
                            }
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            Modifier.size(38.dp).background(seed, CircleShape)
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint =
                                if (seed.toArgb().let { androidx.core.graphics.ColorUtils.calculateContrast(
                                        android.graphics.Color.WHITE, it or 0xFF000000.toInt(),
                                    ) } >= 4.5) Color.White else Color.Black, modifier = Modifier.size(19.dp))
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                }
                repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (!supportsWallpaper) {
            Text("Wallpaper colors require Android 12 or newer. Meowzix's palette remains available.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (appearance.colorTheme == ColorThemePreference.WALLPAPER && supportsWallpaper) {
            Text("Following Android's current wallpaper/system palette. Changing your wallpaper can update app colors.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = hex,
                onValueChange = { input ->
                    val next = input.trim().removePrefix("#")
                    if (next.length <= 6 && next.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) hex = next.uppercase()
                },
                modifier = Modifier.weight(1f),
                label = { Text("Custom hex · #RRGGBB") },
                singleLine = true,
                isError = candidate == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Button(
                onClick = { candidate?.let(onCustom) },
                enabled = candidate != null,
            ) { Text("Apply") }
        }
        Text("The palette is tonal: background and text colors are adjusted for readability instead of using the raw accent everywhere.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Launcher icon: Android's Themed icons can recolor its background from the system palette when supported and enabled in your launcher. The app's custom accent does not override the launcher's wallpaper color.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
