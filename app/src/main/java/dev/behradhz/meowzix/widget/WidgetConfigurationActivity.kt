package dev.behradhz.meowzix.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import dev.behradhz.meowzix.data.settings.DataStoreSettingsRepository
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import kotlin.math.roundToInt

/**
 * Android launcher Reconfigure route, scoped to a validated bound widget ID.
 * The configuration UI uses the app's actual Material 3 surface and system-bar
 * appearance; relying on Theme.Meowzix (a light Android window theme) alone made
 * labels and status-bar icons dark-on-dark.
 */
class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        val kind = when (provider?.className) {
            VinylWidgetProvider::class.java.name -> WidgetKind.VINYL
            PlaybackWidgetProvider::class.java.name -> WidgetKind.PLAYBACK
            else -> null
        }
        if (kind == null || provider?.packageName != packageName) {
            finish()
            return
        }

        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        val repository = DataStoreSettingsRepository(applicationContext)
        setContent {
            val appearance by repository.appearanceSettings.collectAsState(initial = AppearanceSettings())
            val systemDark = isSystemInDarkTheme()
            val dark = when (appearance.theme) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> systemDark
            }
            MeowzixTheme(
                darkTheme = dark,
                colorTheme = appearance.colorTheme,
                customColorSeed = appearance.customColorSeed,
            ) {
                val background = MaterialTheme.colorScheme.background
                SideEffect {
                    window.statusBarColor = background.toArgb()
                    window.navigationBarColor = background.toArgb()
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }

                var theme by remember(widgetId) {
                    mutableStateOf(WidgetInstancePreferences.theme(this, widgetId))
                }
                var style by remember(widgetId) {
                    mutableStateOf(WidgetInstancePreferences.style(this, widgetId))
                }
                var opacity by remember(widgetId) {
                    mutableIntStateOf(WidgetInstancePreferences.opacity(this, widgetId))
                }
                WidgetSettingsContent(
                    kind = kind,
                    theme = theme,
                    style = style,
                    opacityPercent = opacity,
                    appearance = appearance,
                    onThemeChange = { theme = it },
                    onStyleChange = { style = it },
                    onOpacityChange = { opacity = it },
                    onCancel = ::finish,
                    onApply = {
                        WidgetInstancePreferences.saveAppearance(
                            this@WidgetConfigurationActivity,
                            kind, widgetId, theme, style, opacity,
                        )
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        )
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun WidgetSettingsContent(
    kind: WidgetKind,
    theme: WidgetThemeMode,
    style: WidgetVisualStyle,
    opacityPercent: Int,
    appearance: AppearanceSettings,
    onThemeChange: (WidgetThemeMode) -> Unit,
    onStyleChange: (WidgetVisualStyle) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Widget settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    if (kind == WidgetKind.VINYL) "Vinyl widget · Personalize this instance"
                    else "Playback widget · Personalize this instance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                WidgetAppearancePreview(kind, theme, style, opacityPercent, appearance)

                SettingsGroup("Color theme") {
                    WidgetChoice("Follow app theme", theme == WidgetThemeMode.APP) {
                        onThemeChange(WidgetThemeMode.APP)
                    }
                    WidgetChoice("Follow wallpaper", theme == WidgetThemeMode.WALLPAPER) {
                        onThemeChange(WidgetThemeMode.WALLPAPER)
                    }
                    WidgetChoice("Nothing monochrome", theme == WidgetThemeMode.NOTHING) {
                        onThemeChange(WidgetThemeMode.NOTHING)
                    }
                }
                SettingsGroup("Visual style") {
                    WidgetChoice(
                        if (kind == WidgetKind.VINYL) "Vinyl disc" else "Glass",
                        style == WidgetVisualStyle.CLASSIC,
                    ) { onStyleChange(WidgetVisualStyle.CLASSIC) }
                    WidgetChoice(
                        if (kind == WidgetKind.VINYL) "Cover circle" else "Minimal",
                        style == WidgetVisualStyle.MINIMAL,
                    ) { onStyleChange(WidgetVisualStyle.MINIMAL) }
                }
                SettingsGroup("Opacity") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            if (kind == WidgetKind.VINYL) "Vinyl visibility" else "Background opacity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "$opacityPercent%",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = opacityPercent.toFloat(),
                        onValueChange = { onOpacityChange((it / 5f).roundToInt().coerceIn(0, 20) * 5) },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    )
                    Text(
                        if (kind == WidgetKind.VINYL)
                            "Adjusts the record visibility. Invisible touch controls still work."
                        else "Only the surface changes; song text and playback buttons stay readable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = .38f), RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 5.dp),
        )
        content()
    }
}

@Composable
private fun WidgetChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (selected) colors.primaryContainer.copy(alpha = .55f) else Color.Transparent,
                RoundedCornerShape(13.dp),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = colors.onSurface,
        )
    }
}

/** This preview updates before Apply and is scoped to the selected widget only. */
@Composable
private fun WidgetAppearancePreview(
    kind: WidgetKind,
    mode: WidgetThemeMode,
    style: WidgetVisualStyle,
    opacityPercent: Int,
    appearance: AppearanceSettings,
) {
    val context = LocalContext.current
    val colors = deriveWidgetPalette(context, appearance, mode, style)
        .copy(opacityPercent = opacityPercent)
    val surface = Color(colors.surfaceColor)
    val foreground = Color(colors.primary)
    val muted = Color(colors.secondary)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .18f),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(68.dp)
                    .background(surface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (kind == WidgetKind.VINYL) {
                    Box(
                        Modifier.size(52.dp)
                            .background(foreground.copy(alpha = opacityPercent / 100f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Album,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = Color(colors.background),
                        )
                    }
                    Text("Vinyl record", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Box(
                        Modifier.size(43.dp).background(muted.copy(alpha = .65f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Album, contentDescription = null, tint = foreground)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Now playing", color = foreground, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("Artist", color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = foreground)
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = foreground)
                }
            }
        }
    }
}
