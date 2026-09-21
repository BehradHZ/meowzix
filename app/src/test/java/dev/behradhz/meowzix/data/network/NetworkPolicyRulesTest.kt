package dev.behradhz.meowzix.data.network

import dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkPolicyRulesTest {
    @Test
    fun `offline mode blocks every automatic download`() {
        val settings = NetworkPlaybackSettings(offlineMode = true)

        assertNotNull(NetworkPolicyRules.blockReason(settings, NetworkUse.USER_REQUEST, true, false))
        assertNotNull(NetworkPolicyRules.blockReason(settings, NetworkUse.PREFETCH, true, false))
    }

    @Test
    fun `metered policy blocks prefetch but permits explicit play by default`() {
        val settings = NetworkPlaybackSettings(prefetchOnMetered = false)

        assertNull(NetworkPolicyRules.blockReason(settings, NetworkUse.USER_REQUEST, true, true))
        assertNotNull(NetworkPolicyRules.blockReason(settings, NetworkUse.PREFETCH, true, true))
    }

    @Test
    fun `wifi only blocks explicit download on metered network`() {
        val settings = NetworkPlaybackSettings(wifiOnlyDownloads = true)

        assertNotNull(NetworkPolicyRules.blockReason(settings, NetworkUse.USER_REQUEST, true, true))
    }

    @Test
    fun `missing network blocks download`() {
        assertNotNull(
            NetworkPolicyRules.blockReason(
                NetworkPlaybackSettings(),
                NetworkUse.USER_REQUEST,
                hasInternet = false,
                metered = true,
            ),
        )
    }
}
