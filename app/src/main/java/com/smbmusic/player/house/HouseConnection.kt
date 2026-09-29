package com.smbmusic.player.house

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.InetSocketAddress
import java.util.UUID

data class HouseEndpoint(val host: String, val network: Network)

/** Process-local selection. A HOUSE outage is never permission to play SMB. */
object HouseConnection {
    @Volatile var current: HouseEndpoint? = null
    @Volatile var resolved = false

    fun preferences(context: Context) = context.getSharedPreferences("house_connection", Context.MODE_PRIVATE)
    fun host(context: Context): String = preferences(context).getString("host", "").orEmpty()
    fun saveHost(context: Context, host: String) {
        require(host.isEmpty() || host.matches(Regex("[A-Za-z0-9.-]+"))) { "Enter a LAN host name or IPv4 address, without a port or path." }
        preferences(context).edit().putString("host", host).apply()
    }
    @Synchronized fun deviceId(context: Context): String {
        // Device identity must not be cloned onto another phone by Android backup.
        val file = java.io.File(context.noBackupFilesDir, "house-device-id")
        if (file.exists()) return file.readText().trim()
        return ("phone-" + UUID.randomUUID()).also { file.writeText(it) }
    }

    /** Worker thread only. Explicit Network sockets ignore Tailscale/default VPN routing. */
    fun probe(context: Context): HouseEndpoint? {
        val host = host(context)
        if (host.isBlank()) return null
        val manager = context.getSystemService(ConnectivityManager::class.java)
        for (network in manager.allNetworks) {
            val caps = manager.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) ||
                !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue
            try {
                network.socketFactory.createSocket().use { socket ->
                    socket.soTimeout = 1500
                    val address = network.getAllByName(host).first()
                    socket.connect(InetSocketAddress(address, 6600), 1500)
                    val greeting = StringBuilder()
                    val input = socket.getInputStream()
                    while (greeting.length < 128) {
                        val c = input.read()
                        if (c < 0 || c == 10) break
                        greeting.append(c.toChar())
                    }
                    if (greeting.startsWith("OK MPD ")) return HouseEndpoint(host, network)
                }
            } catch (_: Exception) { /* Try the next real LAN, never a VPN. */ }
        }
        return null
    }
}
