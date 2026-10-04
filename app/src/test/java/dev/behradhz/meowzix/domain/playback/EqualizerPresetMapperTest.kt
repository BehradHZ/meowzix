package dev.behradhz.meowzix.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetMapperTest {
    @Test
    fun `flat preset is zero across actual device frequencies`() {
        listOf(60, 120, 250, 1_000, 4_000, 12_000).forEach { hz ->
            assertEquals(0f, EqualizerPresetMapper.targetDb(EqualizerPreset.FLAT, hz), 0f)
        }
    }

    @Test
    fun `bass vocal and bright profiles emphasize distinct ranges`() {
        val bassLow = EqualizerPresetMapper.targetDb(EqualizerPreset.BASS, 80)
        val bassHigh = EqualizerPresetMapper.targetDb(EqualizerPreset.BASS, 10_000)
        val vocalMid = EqualizerPresetMapper.targetDb(EqualizerPreset.VOCAL, 1_500)
        val vocalLow = EqualizerPresetMapper.targetDb(EqualizerPreset.VOCAL, 80)
        val brightHigh = EqualizerPresetMapper.targetDb(EqualizerPreset.BRIGHT, 10_000)
        val brightLow = EqualizerPresetMapper.targetDb(EqualizerPreset.BRIGHT, 80)

        assertTrue(bassLow > bassHigh)
        assertTrue(vocalMid > vocalLow)
        assertTrue(brightHigh > brightLow)
    }

    @Test
    fun `gain mapping respects device floor and conservative positive headroom`() {
        assertEquals(-8f, EqualizerPresetMapper.clamp(-20f, -8f, 12f), 0f)
        assertEquals(6f, EqualizerPresetMapper.clamp(10f, -12f, 12f), 0f)
        assertEquals(4f, EqualizerPresetMapper.clamp(10f, -12f, 4f), 0f)
    }
}
