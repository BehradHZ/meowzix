package dev.behradhz.meowzix.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode

/** Widget instances are independent even when multiple copies of the same type exist. */
enum class WidgetKind { VINYL, PLAYBACK }
// MINIMAL remains for Playback widgets; Vinyl always uses the actual record.
enum class WidgetVisualStyle { CLASSIC, MINIMAL }

internal fun widgetStyleFor(kind: WidgetKind, requested: WidgetVisualStyle): WidgetVisualStyle =
    if (kind == WidgetKind.VINYL) WidgetVisualStyle.CLASSIC else requested

internal fun widgetThemeFor(kind: WidgetKind, requested: WidgetThemeMode): WidgetThemeMode =
    // All four Vinyl color modes (including black-and-white) are now supported.
    requested

data class WidgetInstance(
    val id: Int,
    val kind: WidgetKind,
    val theme: WidgetThemeMode,
    val style: WidgetVisualStyle,
    val opacityPercent: Int = 100,
)

object WidgetInstancePreferences {
    private const val PREFS = "meowzix_widget_instance_appearance"
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun theme(context: Context, widgetId: Int): WidgetThemeMode {
        val stored = prefs(context).getString("theme_$widgetId", null)
        val requested = WidgetThemeMode.entries.firstOrNull { it.name == stored } ?: WidgetThemeMode.APP
        val kind = widgetKind(context, widgetId)
        return kind?.let { widgetThemeFor(it, requested) } ?: requested
    }

    fun style(context: Context, widgetId: Int): WidgetVisualStyle {
        val stored = prefs(context).getString("style_$widgetId", null)
        val requested = WidgetVisualStyle.entries.firstOrNull { it.name == stored } ?: WidgetVisualStyle.CLASSIC
        val kind = widgetKind(context, widgetId)
        return kind?.let { widgetStyleFor(it, requested) } ?: requested
    }

    private fun widgetKind(context: Context, id: Int): WidgetKind? {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider
        return when (provider?.className) {
            VinylWidgetProvider::class.java.name -> WidgetKind.VINYL
            PlaybackWidgetProvider::class.java.name -> WidgetKind.PLAYBACK
            else -> null
        }
    }

    /** 0 = fully transparent background, 100 = opaque; individual widgets differ. */
    fun opacity(context: Context, widgetId: Int): Int =
        prefs(context).getInt("opacity_$widgetId", 100).coerceIn(0, 100)

    fun installed(context: Context): List<WidgetInstance> {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { kind ->
            val provider = if (kind == WidgetKind.VINYL)
                VinylWidgetProvider::class.java else PlaybackWidgetProvider::class.java
            manager.getAppWidgetIds(ComponentName(context, provider)).map { id ->
                WidgetInstance(id, kind, theme(context, id), style(context, id), opacity(context, id))
            }
        }.sortedWith(compareBy(WidgetInstance::kind, WidgetInstance::id))
    }

    fun setTheme(context: Context, kind: WidgetKind, id: Int, value: WidgetThemeMode) {
        if (installed(context).none { it.id == id && it.kind == kind }) return
        prefs(context).edit().putString("theme_$id", widgetThemeFor(kind, value).name).apply()
        refresh(context, kind, id)
    }

    fun setStyle(context: Context, kind: WidgetKind, id: Int, value: WidgetVisualStyle) {
        if (installed(context).none { it.id == id && it.kind == kind }) return
        prefs(context).edit().putString("style_$id", widgetStyleFor(kind, value).name).apply()
        refresh(context, kind, id)
    }

    fun setOpacity(context: Context, kind: WidgetKind, id: Int, percent: Int) {
        if (installed(context).none { it.id == id && it.kind == kind }) return
        prefs(context).edit().putInt("opacity_$id", percent.coerceIn(0, 100)).apply()
        refresh(context, kind, id)
    }

    /** Save all controls together to avoid three consecutive launcher redraws. */
    fun saveAppearance(
        context: Context,
        kind: WidgetKind,
        id: Int,
        theme: WidgetThemeMode,
        style: WidgetVisualStyle,
        opacityPercent: Int,
    ) {
        if (installed(context).none { it.id == id && it.kind == kind }) return
        prefs(context).edit()
            .putString("theme_$id", widgetThemeFor(kind, theme).name)
            .putString("style_$id", widgetStyleFor(kind, style).name)
            .putInt("opacity_$id", opacityPercent.coerceIn(0, 100))
            .apply()
        refresh(context, kind, id)
    }

    fun remove(context: Context, ids: IntArray) {
        val editor = prefs(context).edit()
        ids.forEach { id ->
            editor.remove("theme_$id")
            editor.remove("style_$id")
            editor.remove("opacity_$id")
        }
        editor.apply()
    }

    private fun refresh(context: Context, kind: WidgetKind, id: Int) {
        val provider = if (kind == WidgetKind.VINYL)
            VinylWidgetProvider::class.java else PlaybackWidgetProvider::class.java
        context.sendBroadcast(Intent(context, provider).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
        })
    }
}
