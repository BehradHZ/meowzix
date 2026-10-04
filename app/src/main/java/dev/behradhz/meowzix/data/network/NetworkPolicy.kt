package dev.behradhz.meowzix.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkUse { USER_REQUEST, PREFETCH }

@Singleton
class NetworkPolicy @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun blockReason(settings: NetworkPlaybackSettings, use: NetworkUse): String? {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val networkReason = NetworkPolicyRules.blockReason(
            settings = settings,
            use = use,
            hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            metered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true,
        )
        if (networkReason != null) return networkReason

        if (use == NetworkUse.PREFETCH && isLowSpace()) {
            return "Prefetch is suspended while storage is low."
        }
        return null
    }

    private fun isLowSpace(): Boolean {
        val stats = StatFs(context.filesDir.absolutePath)
        val threshold = maxOf(LOW_SPACE_MIN_BYTES, (stats.totalBytes * LOW_SPACE_PERCENT) / 100L)
        return stats.availableBytes < threshold
    }

    private companion object {
        const val LOW_SPACE_PERCENT = 5L
        const val LOW_SPACE_MIN_BYTES = 256L * 1024L * 1024L
    }
}

internal object NetworkPolicyRules {
    fun blockReason(
        settings: NetworkPlaybackSettings,
        use: NetworkUse,
        hasInternet: Boolean,
        metered: Boolean,
    ): String? {
        if (settings.offlineMode) return "Offline mode is enabled."
        if (!hasInternet) return "No network connection."
        if (settings.wifiOnlyDownloads && metered) return "Waiting for an unmetered network."
        if (use == NetworkUse.PREFETCH && metered && !settings.prefetchOnMetered) {
            return "Prefetch is disabled on metered networks."
        }
        return null
    }
}
