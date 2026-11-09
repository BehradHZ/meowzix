package dev.behradhz.meowzix.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode

/** Widget instances are independent even when multiple copies of the same type exist. */
enum class WidgetKind { VINYL, PLAYBACK }
enum class WidgetVisualStyle { CLASSIC, MINIMAL }

data class WidgetInstance(
    val id: Int,
    val kind: WidgetKind,
    val theme: WidgetThemeMode,
    val style: WidgetVisualStyle,
)

object WidgetInstancePreferences {
    private const val PREFS = "meowzix_widget_instance_appearance"
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun theme(context: Context, widgetId: Int): WidgetThemeMode {
        val stored = prefs(context).getString("theme_$widgetId", null)
        return WidgetThemeMode.entries.firstOrNull { it.name == stored } ?: WidgetThemeMode.APP
    }

    fun style(context: Context, widgetId: Int): WidgetVisualStyle {
        val stored = prefs(context).getString("style_$widgetId", null)
        return WidgetVisualStyle.entries.firstOrNull { it.name == stored } ?: WidgetVisualStyle.CLASSIC
    }

    fun installed(context: Context): List<WidgetInstance> {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { kind ->
            val provider = if (kind == WidgetKind.VINYL)
                VinylWidgetProvider::class.java else PlaybackWidgetProvider::class.java
            manager.getAppWidgetIds(ComponentName(context, provider)).map { id ->
                WidgetInstance(id, kind, theme(context, id), style(context, id))
            }
        }.sortedWith(compareBy(WidgetInstance::kind, WidgetInstance::id))
    }

    fun setTheme(context: Context, kind: WidgetKind, id: Int, value: WidgetThemeMode) {
        if (installed(context).none { it.id == id && it.kind == kind }) return
        prefs(context).edit().putString("theme_$id", value.name).apply()
        refresh(context, kind, id)
    }

    fun setStyle(context: Context, kind: WidgetKind, id: Int, value: WidgetVisualStyle) {
        if (installed(context).none { it.id == id && it.kind == kind }) return
        prefs(context).edit().putString("style_$id", value.name).apply()
        refresh(context, kind, id)
    }

    fun remove(context: Context, ids: IntArray) {
        val editor = prefs(context).edit()
        ids.forEach { id ->
            editor.remove("theme_$id")
            editor.remove("style_$id")
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
