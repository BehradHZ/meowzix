package dev.behradhz.meowzix.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import dev.behradhz.meowzix.ui.theme.MeowzixTheme

/**
 * Android launcher "Widget settings" / Reconfigure action. The AppWidgetManager
 * provides a bound ID: never accept an arbitrary external ID or configure another app.
 * Each widget saves its own theme and visual style independently.
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

        // Preserve the existing widget if the user cancels reconfiguration.
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        setContent {
            MeowzixTheme {
                var theme by remember(widgetId) {
                    mutableStateOf(WidgetInstancePreferences.theme(this, widgetId))
                }
                var style by remember(widgetId) {
                    mutableStateOf(WidgetInstancePreferences.style(this, widgetId))
                }

                Column(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text("Widget settings", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (kind == WidgetKind.VINYL) "Vinyl record · This widget only"
                        else "Playback controls · This widget only",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Color theme", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    WidgetOption("Follow app theme", theme == WidgetThemeMode.APP) {
                        theme = WidgetThemeMode.APP
                    }
                    WidgetOption("Follow wallpaper", theme == WidgetThemeMode.WALLPAPER) {
                        theme = WidgetThemeMode.WALLPAPER
                    }
                    WidgetOption("Nothing monochrome", theme == WidgetThemeMode.NOTHING) {
                        theme = WidgetThemeMode.NOTHING
                    }
                    Text("Visual style", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    WidgetOption(
                        if (kind == WidgetKind.VINYL) "Vinyl disc" else "Glass",
                        style == WidgetVisualStyle.CLASSIC,
                    ) { style = WidgetVisualStyle.CLASSIC }
                    WidgetOption(
                        if (kind == WidgetKind.VINYL) "Cover circle" else "Minimal",
                        style == WidgetVisualStyle.MINIMAL,
                    ) { style = WidgetVisualStyle.MINIMAL }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = ::finish, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                WidgetInstancePreferences.setTheme(this@WidgetConfigurationActivity, kind, widgetId, theme)
                                WidgetInstancePreferences.setStyle(this@WidgetConfigurationActivity, kind, widgetId, style)
                                setResult(
                                    Activity.RESULT_OK,
                                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                                )
                                finish()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Apply") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected = selected, onClick = onClick)
        androidx.compose.material3.TextButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
        ) {
            Text(label, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
