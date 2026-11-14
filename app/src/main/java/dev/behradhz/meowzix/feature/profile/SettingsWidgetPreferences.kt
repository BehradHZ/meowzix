package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.ui.Alignment
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import dev.behradhz.meowzix.widget.WidgetInstance
import dev.behradhz.meowzix.widget.WidgetKind
import dev.behradhz.meowzix.widget.WidgetVisualStyle

/** Each configured instance gets its own persisted Theme AND Style. */
@Composable
internal fun SettingsWidgetPreferences(
    installed: List<WidgetInstance>,
    onTheme: (WidgetInstance, WidgetThemeMode) -> Unit,
    onStyle: (WidgetInstance, WidgetVisualStyle) -> Unit,
    onOpacity: (WidgetInstance, Int) -> Unit,
    onRedIntensity: (WidgetInstance, Int) -> Unit,
) {
    if (installed.isEmpty()) {
        Text(
            "Add a Vinyl or Playback widget to the home screen to configure it here.",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    installed.forEachIndexed { index, instance ->
        if (index > 0) SettingsRowDivider()
        val label = if (instance.kind == WidgetKind.VINYL) "Vinyl" else "Playback"
        Column(Modifier.fillMaxWidth()) {
            Text(
                "$label · Widget #${instance.id}",
                modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 2.dp),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            // Keep legacy in-app controls compatible with launcher configuration:
            // Vinyl has four color treatments and only the record visual style.
            val modes = if (instance.kind == WidgetKind.VINYL)
                listOf("App accent", "Wallpaper accent", "Full color", "Nothing monochrome")
            else listOf("App theme", "Wallpaper", "Nothing monochrome")
            SettingsChoiceRow(
                "Color theme",
                "Only this widget; follows app or wallpaper changes",
                Icons.Rounded.Palette,
                when (instance.theme) {
                    WidgetThemeMode.APP -> modes[0]
                    WidgetThemeMode.WALLPAPER -> modes[1]
                    WidgetThemeMode.NOTHING -> modes.last()
                    WidgetThemeMode.FULL_COLOR -> if (instance.kind == WidgetKind.VINYL) modes[2] else modes[0]
                },
                modes,
            ) { selected ->
                onTheme(instance, when (selected) {
                    modes[1] -> WidgetThemeMode.WALLPAPER
                    modes.last() -> WidgetThemeMode.NOTHING
                    "Full color" -> WidgetThemeMode.FULL_COLOR
                    else -> WidgetThemeMode.APP
                })
            }
            if (instance.kind == WidgetKind.VINYL && instance.theme == WidgetThemeMode.NOTHING) {
                var redDraft by remember(instance.id, instance.redIntensityPercent) {
                    mutableIntStateOf(instance.redIntensityPercent)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Red intensity", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${redDraft}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = redDraft.toFloat(),
                    onValueChange = { redDraft = (it / 5f).roundToInt().coerceIn(0, 20) * 5 },
                    onValueChangeFinished = { onRedIntensity(instance, redDraft) },
                    valueRange = 0f..100f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (instance.kind == WidgetKind.PLAYBACK) {
                SettingsChoiceRow(
                    "Style",
                    "Adjust the appearance without changing playback controls",
                    Icons.Rounded.Tune,
                    if (instance.style == WidgetVisualStyle.CLASSIC) "Glass" else "Minimal",
                    listOf("Glass", "Minimal"),
                    centerOptions = true,
                ) { selected ->
                    onStyle(
                        instance,
                        if (selected == "Glass") WidgetVisualStyle.CLASSIC else WidgetVisualStyle.MINIMAL,
                    )
                }
            }
            SettingsRowDivider()
            var opacityDraft by remember(instance.id, instance.opacityPercent) {
                mutableIntStateOf(instance.opacityPercent)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (instance.kind == WidgetKind.VINYL) "Vinyl opacity" else "Background opacity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${opacityDraft}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = opacityDraft.toFloat(),
                onValueChange = { opacityDraft = (it / 5f).roundToInt().coerceIn(0, 20) * 5 },
                onValueChangeFinished = { onOpacity(instance, opacityDraft) },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}
