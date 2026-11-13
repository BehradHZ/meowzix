package dev.behradhz.meowzix.domain.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramMusicSourceModeTest {
    @Test fun cyclesAcrossThreeModes() {
        assertEquals(TelegramMusicSourceMode.PLAYLIST_ONLY, TelegramMusicSourceMode.NONE.next())
        assertEquals(TelegramMusicSourceMode.LIBRARY, TelegramMusicSourceMode.PLAYLIST_ONLY.next())
        assertEquals(TelegramMusicSourceMode.NONE, TelegramMusicSourceMode.LIBRARY.next())
    }
}
