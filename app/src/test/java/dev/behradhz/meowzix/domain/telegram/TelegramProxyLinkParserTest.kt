package dev.behradhz.meowzix.domain.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class TelegramProxyLinkParserTest {
    @Test fun parsesTelegramMtprotoAndSocksLinks() {
        val mt = TelegramProxyLinkParser.parse(
            "https://t.me/proxy?server=proxy.example.org&port=443&secret=" + "ab".repeat(16),
        )
        assertEquals(TelegramProxyKind.MTPROTO, mt?.kind)
        assertEquals(443, mt?.port)
        assertEquals("ab".repeat(16), mt?.secret)
        val socks = TelegramProxyLinkParser.parse(
            "tg://socks?server=127.0.0.1&port=1080&user=hello%20world&pass=a%26b",
        )
        assertEquals(TelegramProxyKind.SOCKS5, socks?.kind)
        assertEquals("hello world", socks?.username)
        assertEquals("a&b", socks?.password)
    }

    @Test fun detectsLinkInClipboardTextWithoutImportingItAutomatically() {
        assertNotNull(TelegramProxyLinkParser.findIn("Try this: t.me/proxy?server=one.example&port=443&secret=" + "FF".repeat(16)))
        assertNull(TelegramProxyLinkParser.findIn("https://example.com/proxy?server=x&port=443&secret=" + "ab".repeat(16)))
    }


    @Test fun extractsFullyQualifiedTelegramLinksFromClipboardText() {
        val secret = "ab".repeat(16)
        val mtprotoLinks = listOf(
            "https://t.me/proxy?server=mt.example&port=443&secret=$secret",
            "https://telegram.me/proxy?server=mt.example&port=443&secret=$secret",
            "http://www.telegram.me/proxy?server=mt.example&port=443&secret=$secret",
            "tg://proxy?server=mt.example&port=443&secret=$secret",
            "www.t.me/proxy?server=mt.example&port=443&secret=$secret",
        )
        mtprotoLinks.forEach { link ->
            val parsed = TelegramProxyLinkParser.findIn("Copied Telegram link: ($link).")
            assertNotNull("Could not extract $link", parsed)
            assertEquals(TelegramProxyKind.MTPROTO, parsed?.kind)
            assertEquals("mt.example", parsed?.server)
            assertEquals(443, parsed?.port)
            assertEquals(secret, parsed?.secret)
        }

        val socks = TelegramProxyLinkParser.findIn(
            "https://t.me/socks?server=socks.example&port=1080&user=hello%20world&pass=a%26b",
        )
        assertEquals(TelegramProxyKind.SOCKS5, socks?.kind)
        assertEquals("socks.example", socks?.server)
        assertEquals("hello world", socks?.username)
        assertEquals("a&b", socks?.password)
    }

    @Test fun ignoresMalformedCandidatesAndLookalikeHosts() {
        val secret = "ab".repeat(16)
        val invalid = "tg://proxy?server=invalid.example&port=0&secret=$secret"
        val valid = "https://t.me/proxy?server=valid.example&port=443&secret=$secret"
        assertEquals("valid.example", TelegramProxyLinkParser.findIn("$invalid\n$valid")?.server)

        assertNull(TelegramProxyLinkParser.findIn(
            "https://not-t.me/proxy?server=x&port=443&secret=$secret",
        ))
        assertNull(TelegramProxyLinkParser.findIn(
            "https://t.me.evil.example/proxy?server=x&port=443&secret=$secret",
        ))
        assertNull(TelegramProxyLinkParser.findIn(
            "https://example.com/t.me/proxy?server=x&port=443&secret=$secret",
        ))
    }

    @Test fun invalidProxyDataNeverPassesValidation() {
        assertNull(TelegramProxyLinkParser.parse("tg://proxy?server=x&port=0&secret=" + "ab".repeat(16)))
        assertNull(TelegramProxyLinkParser.parse("https://t.me/proxy?server=x&port=443&secret=NOT-HEX"))
        assertNull(TelegramProxyLinkParser.parse("tg://socks?server=a%2Fb&port=1080"))
        assertNull(TelegramProxyLinkParser.parse("tg://proxy?server=host&port=65536&secret=" + "ab".repeat(16)))
    }

    @Test fun manualHttpProxyAndPastedUrlShareValidation() {
        assertEquals("proxy.example", TelegramProxyDraft(
            TelegramProxyKind.HTTP, " proxy.example ", 8080, "alice", "password",
        ).validated().server)
        assertEquals(TelegramProxyKind.SOCKS5, TelegramProxyLinkParser.parse(
            "https://telegram.me/socks?server=host.example&port=1080",
        )?.kind)
    }
}
