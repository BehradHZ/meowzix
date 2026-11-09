package dev.behradhz.meowzix.feature.profile

import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.LauncherIconColorSource
import dev.behradhz.meowzix.ui.theme.LauncherIconManager
import dev.behradhz.meowzix.ui.theme.LauncherIconPalette
import dev.behradhz.meowzix.ui.theme.LauncherIconVariant
import dev.behradhz.meowzix.ui.theme.selectLauncherIconForSource
import java.util.Locale

/**
 * An AndroidView is intentional: painterResource cannot draw AdaptiveIconDrawable.
 * Display the exact packaged adaptive icon, not a recolored mock-up.
 */
@Composable
internal fun LauncherIconPreviews(
    appearance: AppearanceSettings,
    onChoose: (LauncherIconColorSource) -> Unit,
) {
    val context = LocalContext.current
    val supportsWallpaper = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val wallpaperAccent = if (supportsWallpaper) dynamicLightColorScheme(context).primaryContainer.toArgb()
        else 0xFFE5948A.toInt()
    val old = remember(context) { LauncherIconManager.activeVariant(context) }
    // Use the light full-color artwork even in dark mode, as selected in Appearance.
    val app = selectLauncherIconForSource(
        LauncherIconColorSource.APP_THEME, appearance.colorTheme, appearance.customColorSeed,
        false, wallpaperAccent,
    )
    val wallpaper = selectLauncherIconForSource(
        LauncherIconColorSource.WALLPAPER, appearance.colorTheme, appearance.customColorSeed,
        false, wallpaperAccent,
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Launcher icon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LauncherIconTile("Old", "Current", old, null, true, Modifier.weight(1f))
            LauncherIconTile(
                "New", "App theme", app, { onChoose(LauncherIconColorSource.APP_THEME) },
                true, Modifier.weight(1f),
            )
            LauncherIconTile(
                "Wallpaper", "System", wallpaper, { onChoose(LauncherIconColorSource.WALLPAPER) },
                supportsWallpaper, Modifier.weight(1f),
            )
        }
        if (appearance.colorTheme.name == "CUSTOM" || appearance.colorTheme.name == "WALLPAPER") {
            Text(
                "Custom RGB uses the closest packaged icon. Wallpaper uses the Android Material You palette; themed icons depend on your launcher.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LauncherIconTile(
    title: String,
    sourceLabel: String,
    variant: LauncherIconVariant,
    onChoose: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier,
) {
    val iconId = launcherIconResource(variant)
    val backgroundArgb = colorResource(launcherBackgroundResource(variant)).toArgb()
    val hex = String.format(Locale.ROOT, "#%06X", backgroundArgb and 0xFFFFFF)
    val borderColor = if (onChoose == null) MaterialTheme.colorScheme.outlineVariant
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f), RoundedCornerShape(17.dp))
            .border(1.dp, borderColor, RoundedCornerShape(17.dp))
            .then(if (onChoose != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onChoose)
                else Modifier)
            .testTag("launcher-icon-" + title.lowercase(Locale.ROOT).replace(' ', '-'))
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AndroidView(
            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { it.setImageResource(iconId) },
            modifier = Modifier.size(76.dp),
        )
        Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text(sourceLabel, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(if (enabled) hex else "Android 12+", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Resource IDs are shared by PackageManager's aliases and the previews. */
internal fun launcherIconResource(variant: LauncherIconVariant): Int =
    when (variant.palette) {
        LauncherIconPalette.MEOWZIX -> if (variant.dark) R.mipmap.ic_launcher_meowzix_dark else R.mipmap.ic_launcher
        LauncherIconPalette.CORAL -> if (variant.dark) R.mipmap.ic_launcher_coral_dark else R.mipmap.ic_launcher_coral_light
        LauncherIconPalette.AMBER -> if (variant.dark) R.mipmap.ic_launcher_amber_dark else R.mipmap.ic_launcher_amber_light
        LauncherIconPalette.OCEAN -> if (variant.dark) R.mipmap.ic_launcher_ocean_dark else R.mipmap.ic_launcher_ocean_light
        LauncherIconPalette.MINT -> if (variant.dark) R.mipmap.ic_launcher_mint_dark else R.mipmap.ic_launcher_mint_light
        LauncherIconPalette.VIOLET -> if (variant.dark) R.mipmap.ic_launcher_violet_dark else R.mipmap.ic_launcher_violet_light
        LauncherIconPalette.ROSE -> if (variant.dark) R.mipmap.ic_launcher_rose_dark else R.mipmap.ic_launcher_rose_light
        LauncherIconPalette.WALLPAPER -> if (variant.dark) R.mipmap.ic_launcher_wallpaper_dark else R.mipmap.ic_launcher_wallpaper_light
    }

internal fun launcherBackgroundResource(variant: LauncherIconVariant): Int =
    when (variant.palette) {
        LauncherIconPalette.MEOWZIX -> if (variant.dark) R.color.launcher_icon_bg_meowzix_dark else R.color.ic_launcher_background
        LauncherIconPalette.CORAL -> if (variant.dark) R.color.launcher_icon_bg_coral_dark else R.color.launcher_icon_bg_coral_light
        LauncherIconPalette.AMBER -> if (variant.dark) R.color.launcher_icon_bg_amber_dark else R.color.launcher_icon_bg_amber_light
        LauncherIconPalette.OCEAN -> if (variant.dark) R.color.launcher_icon_bg_ocean_dark else R.color.launcher_icon_bg_ocean_light
        LauncherIconPalette.MINT -> if (variant.dark) R.color.launcher_icon_bg_mint_dark else R.color.launcher_icon_bg_mint_light
        LauncherIconPalette.VIOLET -> if (variant.dark) R.color.launcher_icon_bg_violet_dark else R.color.launcher_icon_bg_violet_light
        LauncherIconPalette.ROSE -> if (variant.dark) R.color.launcher_icon_bg_rose_dark else R.color.launcher_icon_bg_rose_light
        LauncherIconPalette.WALLPAPER -> if (variant.dark) R.color.launcher_icon_bg_wallpaper_dark else R.color.launcher_icon_bg_wallpaper_light
    }
