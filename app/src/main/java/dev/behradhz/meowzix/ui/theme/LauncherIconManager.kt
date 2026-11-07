package dev.behradhz.meowzix.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Switches only launcher aliases; MainActivity itself stays enabled for shortcuts,
 * notifications and external intents. Launcher icon caching is launcher-dependent.
 */
internal object LauncherIconManager {
    fun apply(context: Context, selected: LauncherIconVariant) {
        val pm = context.packageManager
        val variants = LauncherIconPalette.entries.flatMap { palette ->
            listOf(LauncherIconVariant(palette, false), LauncherIconVariant(palette, true))
        }
        val changes = variants.mapNotNull { variant ->
            val component = ComponentName(context.packageName, context.packageName + "." + variant.aliasName)
            val status = pm.getComponentEnabledSetting(component)
            val enabled = when (status) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                    variant.palette == LauncherIconPalette.MEOWZIX && !variant.dark
                else -> false
            }
            val shouldEnable = variant == selected
            if (enabled == shouldEnable) null else {
                component to if (shouldEnable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        }
        if (changes.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The launcher never observes an intermediate state with no enabled entry.
            pm.setComponentEnabledSettings(changes.map { (component, state) ->
                PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            })
        } else {
            // Before API 33, enable the replacement before disabling the previous icon.
            changes.sortedByDescending { (_, state) ->
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }.forEach { (component, state) ->
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
