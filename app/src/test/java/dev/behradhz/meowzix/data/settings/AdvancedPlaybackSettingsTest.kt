package dev.behradhz.meowzix.data.settings

import dev.behradhz.meowzix.domain.settings.PlaybackPreferenceSettings
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.PlayerAccentSource
import dev.behradhz.meowzix.domain.settings.PlayerSeekBarStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import dev.behradhz.meowzix.domain.settings.SleepTimerPresetMode
import org.junit.Test

class AdvancedPlaybackSettingsTest {
    @Test
    fun advancedPlaybackDefaultsAreSafeAndNeutral() {
        val settings = PlaybackPreferenceSettings()
        assertFalse(settings.loudnessNormalizationEnabled)
        assertEquals(0, settings.crossfadeDurationSeconds)
        assertTrue(settings.sleepTimerFadeEnabled)
        assertEquals(SleepTimerPresetMode.DURATION, settings.sleepTimerLastMode)
        assertEquals(30, settings.sleepTimerLastMinutes)
    }

    @Test
    fun fullscreenPlayerKeepsExistingAppearanceUntilUserChoosesAStyle() {
        val appearance = AppearanceSettings()
        assertEquals(PlayerAccentSource.GLOBAL, appearance.playerAccentSource)
        assertEquals(PlayerSeekBarStyle.WAVEFORM, appearance.playerSeekBarStyle)
        assertFalse(PlaybackPreferenceSettings().resumeAfterExternalAudio)
    }
}
