package dev.behradhz.meowzix.feature.profile

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.ui.theme.parseCustomColorHex
import dev.behradhz.meowzix.ui.theme.seedArgb
import kotlin.math.roundToInt

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

/** Presets remain exclusive, and editing a custom color never changes the saved seed until Apply. */
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
    var showCustomPicker by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Accent color",
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Choose an accent or follow your device wallpaper. Light and dark appearance can be set separately.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        availablePalettes.chunked(3).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { (mode, label) ->
                    val enabled = mode != ColorThemePreference.WALLPAPER || supportsWallpaper
                    val selected = appearance.colorTheme == mode
                    val seed = if (mode == ColorThemePreference.WALLPAPER) wallpaperSwatch
                        else Color(mode.seedArgb(appearance.customColorSeed))
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = 80.dp)
                            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                                if (mode == ColorThemePreference.CUSTOM) showCustomPicker = true else onSelect(mode)
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
                            if (selected) {
                                val contrast = androidx.core.graphics.ColorUtils.calculateContrast(
                                    android.graphics.Color.WHITE,
                                    seed.toArgb() or 0xFF000000.toInt(),
                                )
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = if (contrast >= 4.5) Color.White else Color.Black,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (!supportsWallpaper) {
            Text(
                "Wallpaper colors require Android 12 or newer. Meowzix's palette remains available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (appearance.colorTheme == ColorThemePreference.WALLPAPER && supportsWallpaper) {
            Text(
                "Following Android's current wallpaper/system palette. Changing your wallpaper can update app colors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = { showCustomPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier.size(20.dp).background(Color(appearance.customColorSeed), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text("Custom RGB · #" + "%06X".format(appearance.customColorSeed and 0xFFFFFF))
        }
        Text(
            "The palette is tonal: background and text colors are adjusted for readability instead of using the raw accent everywhere.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "The original Meowzix logo background is #E5948A. Other in-app logo colors use the exact chosen RGB without tonal filtering; launcher icons are controlled by Android.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showCustomPicker) {
        CustomRgbPickerDialog(
            initialArgb = appearance.customColorSeed,
            onDismiss = { showCustomPicker = false },
            onApply = {
                showCustomPicker = false
                onCustom(it)
            },
        )
    }
}

@Composable
private fun CustomRgbPickerDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    var selectedArgb by remember(initialArgb) { mutableIntStateOf(initialArgb or 0xFF000000.toInt()) }
    var hex by remember(initialArgb) { mutableStateOf("%06X".format(initialArgb and 0xFFFFFF)) }

    fun setColor(argb: Int) {
        selectedArgb = argb or 0xFF000000.toInt()
        hex = "%06X".format(selectedArgb and 0xFFFFFF)
    }

    val red = (selectedArgb ushr 16) and 0xFF
    val green = (selectedArgb ushr 8) and 0xFF
    val blue = selectedArgb and 0xFF
    val validHex = parseCustomColorHex(hex) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom RGB color") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(90.dp).clip(RoundedCornerShape(20.dp))
                            .background(Color(selectedArgb)).padding(5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.meowzix_launcher_art),
                            contentDescription = "Live Meowzix logo preview",
                            modifier = Modifier.size(80.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Column {
                        Text("Live preview", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "#" + "%06X".format(selectedArgb and 0xFFFFFF),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Drag or edit RGB below", style = MaterialTheme.typography.bodySmall)
                    }
                }
                RgbColorPlane(selectedArgb, ::setColor)
                RgbChannelSlider("R", red) { setColor(AndroidColor.rgb(it, green, blue)) }
                RgbChannelSlider("G", green) { setColor(AndroidColor.rgb(red, it, blue)) }
                RgbChannelSlider("B", blue) { setColor(AndroidColor.rgb(red, green, it)) }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        val value = input.trim().removePrefix("#")
                        if (value.length <= 6 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                            hex = value.uppercase()
                            parseCustomColorHex(hex)?.let { selectedArgb = it }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HEX · #RRGGBB") },
                    singleLine = true,
                    isError = !validHex,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                Text(
                    "The logo and color values update instantly. Apply saves the custom accent; Cancel keeps your previous theme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(selectedArgb) }, enabled = validHex) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RgbChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(16.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(value.toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
    }
}

/** The S/V square and vertical hue strip work for taps and continuous drags. */
@Composable
private fun RgbColorPlane(argb: Int, onChange: (Int) -> Unit) {
    val hsv = remember(argb) { FloatArray(3).also { AndroidColor.colorToHSV(argb, it) } }
    val liveArgb by rememberUpdatedState(argb)
    val liveCallback by rememberUpdatedState(onChange)
    var svSize by remember { mutableStateOf(IntSize.Zero) }
    var hueSize by remember { mutableStateOf(IntSize.Zero) }
    val currentSvSize by rememberUpdatedState(svSize)
    val currentHueSize by rememberUpdatedState(hueSize)

    fun updateSv(offset: Offset) {
        val size = currentSvSize
        if (size.width == 0 || size.height == 0) return
        val current = FloatArray(3).also { AndroidColor.colorToHSV(liveArgb, it) }
        val saturation = (offset.x / size.width).coerceIn(0f, 1f)
        val brightness = (1f - offset.y / size.height).coerceIn(0f, 1f)
        liveCallback(AndroidColor.HSVToColor(floatArrayOf(current[0], saturation, brightness)))
    }
    fun updateHue(offset: Offset) {
        val size = currentHueSize
        if (size.height == 0) return
        val current = FloatArray(3).also { AndroidColor.colorToHSV(liveArgb, it) }
        val hue = (offset.y / size.height * 360f).coerceIn(0f, 359.999f)
        liveCallback(AndroidColor.HSVToColor(floatArrayOf(hue, current[1], current[2])))
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(
            Modifier.weight(1f).height(178.dp).clip(RoundedCornerShape(12.dp))
                .onSizeChanged { svSize = it }
                .pointerInput(Unit) { detectTapGestures(onTap = ::updateSv) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = ::updateSv,
                        onDrag = { change, _ ->
                            updateSv(change.position)
                            change.consume()
                        },
                    )
                },
        ) {
            drawRect(Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))))
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val center = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height)
            drawCircle(Color.Black, radius = 10.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, radius = 10.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
        }
        Canvas(
            Modifier.width(28.dp).height(178.dp).clip(RoundedCornerShape(9.dp))
                .onSizeChanged { hueSize = it }
                .pointerInput(Unit) { detectTapGestures(onTap = ::updateHue) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = ::updateHue,
                        onDrag = { change, _ ->
                            updateHue(change.position)
                            change.consume()
                        },
                    )
                },
        ) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                ),
            )
            val y = (hsv[0] / 360f) * size.height
            drawLine(Color.Black, Offset(0f, y), Offset(size.width, y), strokeWidth = 6.dp.toPx())
            drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
        }
    }
}
