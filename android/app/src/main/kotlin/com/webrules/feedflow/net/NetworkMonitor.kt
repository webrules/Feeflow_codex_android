package com.webrules.feedflow.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Snapshot of the current connectivity state. */
data class NetworkStatus(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
)

/**
 * Observes connectivity using [ConnectivityManager] so background prefetch can be
 * gated to Wi-Fi only, matching the iOS NetworkMonitor behaviour.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun current(): NetworkStatus {
        val network = connectivityManager.activeNetwork ?: return NetworkStatus()
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkStatus()
        return capabilities.toStatus()
    }

    fun statusFlow(): Flow<NetworkStatus> = callbackFlow {
        trySend(current())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.toStatus())
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities.toStatus(): NetworkStatus {
        val connected = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val wifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return NetworkStatus(isConnected = connected, isWifi = wifi)
    }
}
