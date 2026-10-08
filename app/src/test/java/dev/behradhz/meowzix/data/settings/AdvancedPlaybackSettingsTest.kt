package dev.behradhz.meowzix.data.settings

import dev.behradhz.meowzix.domain.settings.PlaybackPreferenceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdvancedPlaybackSettingsTest {
    @Test
    fun advancedPlaybackDefaultsAreSafeAndNeutral() {
        val settings = PlaybackPreferenceSettings()
        assertFalse(settings.loudnessNormalizationEnabled)
        assertEquals(0, settings.crossfadeDurationSeconds)
    }
}
