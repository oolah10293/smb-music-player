package com.housemusic.player.house

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** A configured target exists offline; only a verified target has a network/address. */
data class HouseEndpoint(val host: String, val network: Network? = null, val address: InetAddress? = null) {
    val httpHost: String get() = (address?.hostAddress ?: host).let { if (it.contains(':')) "[$it]" else it }
}

/** The current verified HOUSE connection; no playback-mode selection is involved. */
object HouseConnection {
    private val connection = HouseConnectionState<HouseEndpoint>()
    val current: HouseEndpoint? get() = connection.endpoint
    val epoch: Long get() = connection.epoch
    internal fun beginSession(): Long = connection.beginSession()
    internal fun publish(expectedEpoch: Long, endpoint: HouseEndpoint?) = connection.publish(expectedEpoch, endpoint)
    internal fun clear(expectedEpoch: Long) = connection.clear(expectedEpoch)

    fun preferences(context: Context) = context.getSharedPreferences("house_connection", Context.MODE_PRIVATE)
    fun host(context: Context): String = preferences(context).getString("host", "").orEmpty()
    fun configuredEndpoint(context: Context): HouseEndpoint = HouseEndpoint(host(context))
    fun saveHost(context: Context, host: String) {
        require(host.isEmpty() || host.matches(Regex("[A-Za-z0-9.-]+"))) { "Enter a LAN host name or IPv4 address, without a port or path." }
        if (host != this.host(context)) connection.beginSession()
        preferences(context).edit().putString("host", host).apply()
    }
    @Synchronized fun deviceId(context: Context): String {
        // Device identity must not be cloned onto another phone by Android backup.
        val file = java.io.File(context.noBackupFilesDir, "house-device-id")
        if (file.exists()) return file.readText().trim()
        return ("phone-" + UUID.randomUUID()).also { file.writeText(it) }
    }

    /** Physical evidence is independent of whichever network Android uses for packets. */
    fun isPresent(context: Context, endpoint: HouseEndpoint): Boolean {
        val network = endpoint.network ?: return false
        val address = endpoint.address ?: return false
        return qualifies(context.getSystemService(ConnectivityManager::class.java), network, address)
    }

    private fun qualifies(manager: ConnectivityManager, network: Network, address: InetAddress): Boolean {
        val caps = manager.getNetworkCapabilities(network) ?: return false
        val links = manager.getLinkProperties(network) ?: return false
        return HouseNetworkPolicy.qualifies(
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN), address,
            links.routes.map { route ->
                // hasGateway() became public in API 29; getType() in API 33.
                // Use public API 21 gateway/link-address evidence on older phones.
                val unicast = if (Build.VERSION.SDK_INT >= 33) {
                    route.type == RouteInfo.RTN_UNICAST
                } else {
                    links.linkAddresses.any { local ->
                        HouseNetworkPolicy.hasMatchingLinkPrefix(route.destination.address,
                            route.destination.prefixLength, local.address, local.prefixLength)
                    }
                }
                HouseLanRoute(route.destination.address, route.destination.prefixLength,
                    HouseNetworkPolicy.hasGateway(route.gateway), unicast)
            })
    }

    /** Worker thread only. Qualify a direct physical route, then verify through normal routing. */
    fun probe(context: Context, host: String = host(context)): HouseEndpoint? {
        if (host.isBlank()) return null
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
        for (network in manager.allNetworks) {
            for (address in addresses) {
                if (!qualifies(manager, network, address)) continue
                try {
                    Socket().use { socket ->
                        socket.soTimeout = 1500
                        socket.connect(InetSocketAddress(address, 6600), 1500)
                        val greeting = StringBuilder()
                        val input = socket.getInputStream()
                        while (greeting.length < 128) {
                            val c = input.read()
                            if (c < 0 || c == 10) break
                            greeting.append(c.toChar())
                        }
                        if (greeting.startsWith("OK MPD ")) {
                            val candidate = HouseEndpoint(host, network, address)
                            // A coincidentally matching subnet alone is not HOUSE identity.
                            HouseApi(context, candidate).get("/health")
                            if (isPresent(context, candidate)) return candidate
                        }
                    }
                } catch (_: Exception) { /* Try the next real LAN, never a VPN. */ }
            }
        }
        return null
    }
}
