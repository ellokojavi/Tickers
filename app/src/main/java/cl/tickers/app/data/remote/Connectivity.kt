package cl.tickers.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import cl.tickers.app.domain.engine.NetworkStatus

/**
 * Reads the device's network state.
 *
 * A failed request looks the same whether the phone is in airplane mode or the
 * exchanges are down. This is what tells those apart, so the screen can say
 * something useful instead of listing four hostname errors.
 *
 * The web app has no equivalent: a browser can say it has no network but cannot
 * tell a captive portal from a working one, so UNVALIDATED is Android-only and
 * the shared classifier simply never returns NO_INTERNET over there.
 */
object Connectivity {

    fun status(context: Context): NetworkStatus {
        // If the service is somehow unavailable, assume online: this check must
        // never be the reason a fetch does not happen.
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkStatus.ONLINE
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkStatus.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkStatus.NONE
        }
        // VALIDATED means Android probed the network and got a real answer. It is
        // missing on a captive portal, and for a beat right after a network comes
        // up, so UNVALIDATED still gets a fetch attempt: it only changes the
        // wording of a failure.
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            NetworkStatus.ONLINE
        } else {
            NetworkStatus.UNVALIDATED
        }
    }
}
