package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
            val modes = listOf("App theme", "Wallpaper", "Nothing monochrome")
            SettingsChoiceRow(
                "Color theme",
                "Only this widget; follows app or wallpaper changes",
                Icons.Rounded.Palette,
                when (instance.theme) {
                    WidgetThemeMode.APP -> modes[0]
                    WidgetThemeMode.WALLPAPER -> modes[1]
                    WidgetThemeMode.NOTHING -> modes[2]
                },
                modes,
            ) { selected ->
                onTheme(instance, when (selected) {
                    modes[1] -> WidgetThemeMode.WALLPAPER
                    modes[2] -> WidgetThemeMode.NOTHING
                    else -> WidgetThemeMode.APP
                })
            }
            val classic = if (instance.kind == WidgetKind.VINYL) "Vinyl disc" else "Glass"
            val minimal = if (instance.kind == WidgetKind.VINYL) "Cover circle" else "Minimal"
            SettingsChoiceRow(
                "Style",
                "Adjust the appearance without changing playback controls",
                Icons.Rounded.Tune,
                if (instance.style == WidgetVisualStyle.CLASSIC) classic else minimal,
                listOf(classic, minimal),
                centerOptions = true,
            ) { selected ->
                onStyle(
                    instance,
                    if (selected == classic) WidgetVisualStyle.CLASSIC else WidgetVisualStyle.MINIMAL,
                )
            }
        }
    }
}
