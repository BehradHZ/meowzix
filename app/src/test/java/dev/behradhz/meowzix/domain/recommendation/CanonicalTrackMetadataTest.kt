package dev.behradhz.meowzix.domain.recommendation

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class CanonicalTrackMetadataTest {
    private val song = UUID(0L, 5L)

    @Test fun unicodeTextIsNormalizedAndDoesNotUseFileNamesOrExternalLabels() {
        val item = CanonicalTrackMetadata.fromLibrary(
            song, "  Café—دوست 2026  ", "  The ARTIST  ", "ÁLBUM: Vol. 2",
            225_000L, 1_700_000_000_000L,
        )
        assertEquals(song, item.trackId)
        assertEquals(listOf("café", "دوست", "2026"), item.titleTokens)
        assertEquals(listOf("the", "artist"), item.artistTokens)
        assertEquals(listOf("álbum", "vol", "2"), item.albumTokens)
        assertEquals(225_000L, item.durationMs)
        assertNotNull(item.addedAt)
    }

    @Test fun missingValuesStayMissingAndVocabularyIsBounded() {
        val absent = CanonicalTrackMetadata.fromLibrary(song, null, "", null, -10, null)
        assertTrue(absent.titleTokens.isEmpty())
        assertTrue(absent.artistTokens.isEmpty())
        assertTrue(absent.albumTokens.isEmpty())
        assertNull(absent.durationMs)
        assertNull(absent.addedAt)

        val long = (1..300).joinToString(" ") { "token$it" }
        val bounded = CanonicalTrackMetadata.fromLibrary(song, long, null, null, 0, 0)
        assertEquals(CanonicalTrackMetadata.MAX_TOKENS, bounded.titleTokens.size)
        assertTrue(bounded.titleTokens.all { it.length <= 48 })
    }

    @Test fun equivalentCanonicalMetadataUsesSameTokensAcrossSources() {
        val first = CanonicalTrackMetadata.fromLibrary(song, "Hello WORLD", "Artist", "Album", 10_000, 99)
        val second = CanonicalTrackMetadata.fromLibrary(song, "hello world", "ARTIST", "ALBUM", 10_000, 99)
        assertEquals(first, second)
    }
}
