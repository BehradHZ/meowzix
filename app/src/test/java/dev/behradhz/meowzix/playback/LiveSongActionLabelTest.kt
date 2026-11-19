package dev.behradhz.meowzix.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LiveSongActionLabelTest {
    @Test
    fun progressStyleTextFallbackUsesRealTransportSymbolsInsteadOfChevrons() {
        assertEquals("⏮︎", liveSongActionLabel(LiveSongAction.PREVIOUS))
        assertEquals("⏸︎", liveSongActionLabel(LiveSongAction.PAUSE))
        assertEquals("⏭︎", liveSongActionLabel(LiveSongAction.NEXT))
        assertNotEquals("‹", liveSongActionLabel(LiveSongAction.PREVIOUS))
        assertNotEquals("Ⅱ", liveSongActionLabel(LiveSongAction.PAUSE))
        assertNotEquals("›", liveSongActionLabel(LiveSongAction.NEXT))
    }

    @Test
    fun auxiliaryActionsHaveRecognizableDistinctTextFallbacks() {
        val labels = listOf(
            LiveSongAction.FAVORITE,
            LiveSongAction.SHUFFLE,
            LiveSongAction.REPEAT,
        ).map(::liveSongActionLabel)
        assertEquals(3, labels.distinct().size)
        assertEquals(listOf("♥︎", "🔀︎", "🔁︎"), labels)
    }
}
