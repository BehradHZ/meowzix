package dev.behradhz.meowzix.widget

import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetVinylAppearanceRulesTest {
    @Test fun vinylOnlyUsesTheRecordVisualStyle() {
        assertEquals(WidgetVisualStyle.CLASSIC, widgetStyleFor(WidgetKind.VINYL, WidgetVisualStyle.MINIMAL))
        assertEquals(WidgetVisualStyle.CLASSIC, widgetStyleFor(WidgetKind.VINYL, WidgetVisualStyle.CLASSIC))
        assertEquals(WidgetVisualStyle.MINIMAL, widgetStyleFor(WidgetKind.PLAYBACK, WidgetVisualStyle.MINIMAL))
    }

    @Test fun vinylSupportsBlackAndWhiteAlongsideOtherColorModes() {
        assertEquals(WidgetThemeMode.NOTHING, widgetThemeFor(WidgetKind.VINYL, WidgetThemeMode.NOTHING))
        assertEquals(WidgetThemeMode.NOTHING, widgetThemeFor(WidgetKind.PLAYBACK, WidgetThemeMode.NOTHING))
        assertEquals(WidgetThemeMode.FULL_COLOR, widgetThemeFor(WidgetKind.VINYL, WidgetThemeMode.FULL_COLOR))
        assertEquals(WidgetThemeMode.WALLPAPER, widgetThemeFor(WidgetKind.VINYL, WidgetThemeMode.WALLPAPER))
    }
}
