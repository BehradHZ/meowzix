package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulePlaylistCodecTest {
    @Test fun `round trips typed rules without executable query text`() {
        val rules = listOf(
            PlaylistRule(RuleKind.FAVORITE),
            PlaylistRule(RuleKind.ARTIST_IS, "کیوسک | Kiosk"),
            PlaylistRule(RuleKind.ADDED_WITHIN_DAYS, "30"),
        )
        assertEquals(rules, RulePlaylistCodec.decode(RulePlaylistCodec.encode(rules)))
    }

    @Test fun `unknown versions fail closed`() {
        assertTrue(RulePlaylistCodec.decode("v99\nFAVORITE\t").isEmpty())
    }

    @Test fun `invalid rule rows are ignored`() {
        assertEquals(
            listOf(PlaylistRule(RuleKind.FAVORITE)),
            RulePlaylistCodec.decode("v1\nDROP_TABLE\teA\nFAVORITE\t\n"),
        )
    }
}
