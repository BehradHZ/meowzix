package dev.behradhz.meowzix.feature.nowplaying

import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsVersion
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import dev.behradhz.meowzix.domain.lyrics.ParsedLyrics
import dev.behradhz.meowzix.domain.lyrics.TimedLyricLine
import dev.behradhz.meowzix.domain.lyrics.TrackLyrics
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayableLyricsTest {
    private val trackId = UUID.randomUUID()
    private fun state(parsed: ParsedLyrics): NowPlayingLyricsState = NowPlayingLyricsState(
        trackId = trackId,
        trackLyrics = TrackLyrics(
            trackId = trackId,
            selected = LyricsVersion(
                id = UUID.randomUUID(), trackId = trackId, sourceType = LyricsSourceType.USER_LRC,
                sourceLabel = null, rawText = "", parsed = parsed, selected = true,
                userSelected = true, userDelayMs = 0L, createdAtEpochMs = 1L, updatedAtEpochMs = 1L,
            ),
        ),
    )

    @Test fun placeholdersAndEmptyVersionsDoNotReservePreviewSpace() {
        assertFalse(NowPlayingLyricsState(trackId = trackId).hasDisplayableLyrics)
        assertFalse(state(ParsedLyrics(LyricsContentType.INSTRUMENTAL)).hasDisplayableLyrics)
        assertFalse(state(ParsedLyrics(LyricsContentType.INVALID)).hasDisplayableLyrics)
        assertFalse(state(ParsedLyrics(LyricsContentType.PLAIN, plainLines = listOf(" "))).hasDisplayableLyrics)
    }

    @Test fun realTimedOrPlainLyricsShowPreview() {
        assertTrue(state(ParsedLyrics(LyricsContentType.PLAIN, plainLines = listOf("Line"))).hasDisplayableLyrics)
        assertTrue(state(ParsedLyrics(
            LyricsContentType.TIMED,
            timedLines = listOf(TimedLyricLine(1000L, "Verse", 0)),
        )).hasDisplayableLyrics)
    }
}
