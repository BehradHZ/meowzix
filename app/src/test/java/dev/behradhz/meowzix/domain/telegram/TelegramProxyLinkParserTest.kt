package dev.behradhz.meowzix.domain.telegram

import java.util.Base64
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


    @Test fun base64MtprotoSecretsAreNormalizedForTdlibInBothImportPaths() {
        val rawSecret = ByteArray(16) { it.toByte() }
        val expectedHex = "000102030405060708090a0b0c0d0e0f"
        val urlEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSecret)
        val padded = Base64.getUrlEncoder().encodeToString(rawSecret)
        listOf(urlEncoded, padded).forEach { secret ->
            val link = "https://t.me/proxy?server=proxy.example.org&port=8443&secret=$secret"
            val direct = TelegramProxyLinkParser.parse(link)
            val clipboard = TelegramProxyLinkParser.findIn("Copied: ($link)")
            assertNotNull("Direct paste must recognize $secret", direct)
            assertNotNull("Clipboard scan must recognize $secret", clipboard)
            assertEquals(TelegramProxyKind.MTPROTO, direct?.kind)
            assertEquals("proxy.example.org", direct?.server)
            assertEquals(8443, direct?.port)
            assertEquals(expectedHex, direct?.secret)
            assertEquals(direct, clipboard)
        }
        // The manual MTProto form shares the same normalization as the link parser.
        assertEquals(expectedHex, TelegramProxyDraft(
            TelegramProxyKind.MTPROTO, "proxy.example.org", 8443, secret = urlEncoded,
        ).validated().secret)
    }

    @Test fun standardBase64AndMarkdownEscapedAmpersandsAreAccepted() {
        val rawSecret = ByteArray(16) { 0xfb.toByte() }
        val standard = Base64.getEncoder().encodeToString(rawSecret)
        val expected = "fb".repeat(16)
        assertEquals(expected, TelegramProxyLinkParser.parse(
            "tg://proxy?server=proxy.example.org&port=443&secret=$standard",
        )?.secret)

        val urlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSecret)
        assertEquals(expected, TelegramProxyLinkParser.findIn(
            "https://t.me/proxy?server=proxy.example.org\\&port=443\\&secret=$urlSafe",
        )?.secret)
        // Previously accepted hexadecimal secrets must retain their value.
        assertEquals("ab".repeat(16), TelegramProxyLinkParser.parse(
            "https://t.me/proxy?server=proxy.example.org&port=443&secret=${"AB".repeat(16)}",
        )?.secret)
    }

    @Test fun malformedOrShortBase64SecretsRemainInvalid() {
        assertNull(TelegramProxyLinkParser.findIn(
            "https://t.me/proxy?server=proxy.example.org&port=443&secret=QUJDRA",
        ))
        assertNull(TelegramProxyLinkParser.parse(
            "https://t.me/proxy?server=proxy.example.org&port=443&secret=${"a".repeat(22)}!",
        ))
        assertNull(TelegramProxyLinkParser.parse(
            "https://t.me/proxy?server=proxy.example.org&port=443&secret=${"a".repeat(342)}",
        ))
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
