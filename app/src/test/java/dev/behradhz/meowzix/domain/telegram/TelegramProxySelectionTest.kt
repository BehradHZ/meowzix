package dev.behradhz.meowzix.domain.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramProxySelectionTest {
    @Test fun pastedLinkForSavedEndpointIsNotSuggestedAgain() {
        val saved = TelegramProxyItem(12, TelegramProxyKind.MTPROTO, "proxy.example.org", 443, false)
        val link = TelegramProxyLinkParser.parse(
            "https://t.me/proxy?server=PROXY.EXAMPLE.ORG&port=443&secret=0123456789abcdef0123456789abcdef",
        )!!
        assertTrue(saved.matchesEndpoint(link))
        assertFalse(saved.matchesEndpoint(link.copy(port = 444)))
    }

    @Test fun differentProxyKindsWithSameHostAndPortAreDistinct() {
        val socks = TelegramProxyItem(1, TelegramProxyKind.SOCKS5, "example.org", 1080, false)
        val mtproto = TelegramProxyDraft(TelegramProxyKind.MTPROTO, "example.org", 1080,
            secret = "0123456789abcdef0123456789abcdef")
        assertFalse(socks.matchesEndpoint(mtproto))
    }

    @Test fun latencySortingPutsKnownLowerPingsFirst() {
        val proxies = listOf(
            TelegramProxyItem(1, TelegramProxyKind.SOCKS5, "one", 8080, false, latencyMs = null),
            TelegramProxyItem(2, TelegramProxyKind.SOCKS5, "two", 8080, false, latencyMs = 140),
            TelegramProxyItem(3, TelegramProxyKind.SOCKS5, "three", 8080, false, latencyMs = 37),
        )
        val sorted = rankTelegramProxiesByLatency(proxies)
        assertEquals(listOf(3, 2, 1), sorted.map { it.id })
    }
}
