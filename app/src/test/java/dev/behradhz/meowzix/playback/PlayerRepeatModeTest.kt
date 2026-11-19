package dev.behradhz.meowzix.playback

import androidx.media3.common.Player
import dev.behradhz.meowzix.domain.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRepeatModeTest {
    @Test fun oneAlwaysRepeatsTheCurrentSong() {
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.ONE.toPlayerRepeatMode(false))
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.ONE.toPlayerRepeatMode(true))
    }

    @Test fun allRepeatsOnlyNonProgressiveWindow() {
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.ALL.toPlayerRepeatMode(false))
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.ALL.toPlayerRepeatMode(true))
    }

    @Test fun offNeverRepeats() {
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.OFF.toPlayerRepeatMode(false))
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.OFF.toPlayerRepeatMode(true))
    }
}
