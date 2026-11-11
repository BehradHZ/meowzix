package dev.behradhz.meowzix.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
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
import dev.behradhz.meowzix.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

/**
 * Android launcher Reconfigure route, scoped to a validated bound widget ID.
 * The configuration UI uses the app's actual Material 3 surface and system-bar
 * appearance; relying on Theme.Meowzix (a light Android window theme) alone made
 * labels and status-bar icons dark-on-dark.
 */
@OptIn(markerClass = [UnstableApi::class])
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
                var lastPlayed by remember(widgetId) {
                    mutableStateOf(PlaybackWidgetSnapshotStore.loadLastPlayed(applicationContext))
                }
                // The recent-track cache survives app restarts. For fresh installs,
                // consult the active session without starting playback.
                LaunchedEffect(widgetId) {
                    if (kind != WidgetKind.VINYL) return@LaunchedEffect
                    var controller: MediaController? = null
                    var future: ListenableFuture<MediaController>? = null
                    try {
                        val connecting = MediaController.Builder(
                            this@WidgetConfigurationActivity,
                            SessionToken(
                                this@WidgetConfigurationActivity,
                                ComponentName(this@WidgetConfigurationActivity, PlaybackService::class.java),
                            ),
                        ).buildAsync()
                        future = connecting
                        val connected = withTimeout(3_000L) { connecting.await() }
                        controller = connected
                        val active = PlaybackWidgetState.fromPlayer(connected)
                        if (active.hasTrack && (active.isPlaying || !lastPlayed.hasTrack)) {
                            lastPlayed = active
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        // The persisted last-played track remains available offline.
                    } finally {
                        if (controller != null) controller.release()
                        else future?.let(MediaController::releaseFuture)
                    }
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
                    lastPlayed = lastPlayed,
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
    lastPlayed: PlaybackWidgetState,
    onThemeChange: (WidgetThemeMode) -> Unit,
    onStyleChange: (WidgetVisualStyle) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Configuration is edge-to-edge: the header and fixed action bar need safe insets.
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
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

                WidgetAppearancePreview(kind, theme, style, opacityPercent, appearance, lastPlayed)

                SettingsGroup("Color theme") {
                    if (kind == WidgetKind.VINYL) {
                        WidgetChoice("App accent · Monochrome", theme == WidgetThemeMode.APP) {
                            onThemeChange(WidgetThemeMode.APP)
                        }
                        WidgetChoice("Wallpaper accent · Monochrome", theme == WidgetThemeMode.WALLPAPER) {
                            onThemeChange(WidgetThemeMode.WALLPAPER)
                        }
                        WidgetChoice("Full color · Original artwork", theme == WidgetThemeMode.FULL_COLOR) {
                            onThemeChange(WidgetThemeMode.FULL_COLOR)
                        }
                    } else {
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
                }
                if (kind == WidgetKind.PLAYBACK) {
                    SettingsGroup("Visual style") {
                        WidgetChoice("Glass", style == WidgetVisualStyle.CLASSIC) {
                            onStyleChange(WidgetVisualStyle.CLASSIC)
                        }
                        WidgetChoice("Minimal", style == WidgetVisualStyle.MINIMAL) {
                            onStyleChange(WidgetVisualStyle.MINIMAL)
                        }
                    }
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

/** Preview and launcher widget render from the same VinylRecordArtwork implementation. */
@Composable
private fun WidgetAppearancePreview(
    kind: WidgetKind,
    mode: WidgetThemeMode,
    style: WidgetVisualStyle,
    opacityPercent: Int,
    appearance: AppearanceSettings,
    lastPlayed: PlaybackWidgetState,
) {
    val context = LocalContext.current
    val palette = deriveWidgetPalette(context, appearance, mode, style)
        .copy(opacityPercent = opacityPercent)
    val foreground = Color(palette.primary)
    val muted = Color(palette.secondary)
    val source by produceState<Bitmap?>(
        initialValue = null, key1 = kind, key2 = lastPlayed.artworkRef,
    ) {
        if (kind == WidgetKind.VINYL) {
            value = VinylRecordArtwork.prepare(context, lastPlayed.artworkRef)
        }
    }
    val vinylImage by produceState<Bitmap?>(
        initialValue = null,
        key1 = source,
        key2 = palette.accent,
        key3 = palette.vinylColorMode,
    ) {
        value = withContext(Dispatchers.Default) {
            source?.let { VinylRecordArtwork.themedFrame(it, palette) }
        }
    }
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
                "LIVE PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (kind == WidgetKind.VINYL) {
                // No fake circle or painted background: this is the same 288px
                // record bitmap sent to the launcher, at the requested opacity.
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .height(216.dp)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = .32f),
                            RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    vinylImage?.let { image ->
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "Vinyl widget preview of last played track",
                            modifier = Modifier.size(196.dp).alpha(opacityPercent / 100f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Text(
                    lastPlayed.title ?: "No recently played track",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    lastPlayed.artist ?: if (lastPlayed.hasTrack) "Unknown artist"
                        else "Play a song to preview its actual cover artwork.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(68.dp)
                        .background(Color(palette.surfaceColor), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(43.dp).background(muted.copy(alpha = .65f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Album, contentDescription = null, tint = foreground)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(lastPlayed.title ?: "Now playing", color = foreground, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(lastPlayed.artist ?: "Artist", color = muted,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = foreground)
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = foreground)
                }
            }
        }
    }
}
