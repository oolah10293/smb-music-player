package com.smbmusic.player.radio

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.RouteInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

/** Read-only station cache refresh. Never registers a HOUSE controller or touches its session. */
class HouseStationRefresh private constructor(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("house_radio_sync", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val store = RadioStore(context)
    private val owners = mutableSetOf<Any>()
    private val listeners = mutableSetOf<() -> Unit>()
    private var executor = Executors.newSingleThreadExecutor()
    private var registered = false
    private var generation = 0L
    private var serial = 0L
    private var inFlight: Long? = null
    private var lastAttempt = -MIN_GAP_MS
    var status: String = "House stations refresh automatically at home"
        private set
    private val periodic = Runnable { requestRefresh() }
    private val changed = Runnable { requestRefresh(force = true) }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = networkChanged()
        override fun onLost(network: Network) = networkChanged()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = networkChanged()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = networkChanged()
    }

    // These lifecycle and UI entry points are called on the main thread.
    fun acquire(owner: Any) {
        if (!owners.add(owner)) return
        if (owners.size == 1) {
            generation++
            if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
            registered = runCatching {
                manager.registerNetworkCallback(NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(), callback)
                true
            }.onFailure { android.util.Log.w(TAG, "LAN callback unavailable: ${it.javaClass.simpleName}") }.getOrDefault(false)
        }
        requestRefresh(force = true)
    }

    fun release(owner: Any) {
        owners.remove(owner)
        if (owners.isNotEmpty()) return
        generation++; inFlight = null
        handler.removeCallbacks(periodic); handler.removeCallbacks(changed)
        if (registered) runCatching { manager.unregisterNetworkCallback(callback) }
        registered = false
        executor.shutdownNow()
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    fun host(): String {
        if (prefs.contains("host")) return prefs.getString("host", "").orEmpty()
        // An upgrade may retain the old combined app's local House setting.
        val old = context.getSharedPreferences("house_connection", Context.MODE_PRIVATE).getString("host", "").orEmpty()
        if (old.isNotBlank()) return old
        // The project's House Pi also serves Samba. This is a candidate, not proof of home presence.
        val smb = context.getSharedPreferences("smb_music_settings", Context.MODE_PRIVATE).getString("address", "").orEmpty()
        return runCatching { URI(smb).host.orEmpty() }.getOrDefault("")
    }

    fun saveHost(raw: String) {
        val value = raw.trim()
        require(value.isEmpty() || value.matches(Regex("[A-Za-z0-9.-]+"))) {
            "Enter the Pi's LAN address or hostname, without a port or path."
        }
        check(prefs.edit().putString("host", value).commit()) { "Could not save House address." }
        generation++
        requestRefresh(force = true)
    }

    private fun networkChanged() {
        handler.post {
            if (owners.isNotEmpty()) {
                generation++
                handler.removeCallbacks(changed)
                handler.postDelayed(changed, 500)
            }
        }
    }

    fun requestRefresh(force: Boolean = false) {
        if (owners.isEmpty() || inFlight != null) return
        handler.removeCallbacks(periodic)
        val host = host()
        if (host.isBlank()) {
            publish("House stations: tap to set the Pi's LAN address")
            handler.postDelayed(periodic, INTERVAL_MS)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastAttempt < MIN_GAP_MS) {
            handler.postDelayed(periodic, MIN_GAP_MS - (now - lastAttempt))
            return
        }
        lastAttempt = now
        val epoch = generation
        val request = ++serial
        inFlight = request
        executor.execute {
            var target: Target? = null
            val result = runCatching {
                target = findTarget(host)
                target?.let { fetch(it.address) }
            }
            handler.post {
                if (inFlight != request) return@post
                inFlight = null
                if (owners.isEmpty()) return@post
                if (epoch != generation || host != host()) {
                    requestRefresh(force = true)
                    return@post
                }
                val selected = target
                val rows = result.getOrNull()
                if (rows != null && selected != null && qualifies(selected.network, selected.address)) {
                    runCatching {
                        store.refreshHouse(rows)
                        prefs.edit().putLong("lastRefresh", System.currentTimeMillis()).apply()
                    }.onSuccess { publish("House stations refreshed · saved for away use") }
                        .onFailure { publish("Could not save House stations · keeping saved list") }
                } else if (selected == null && result.isSuccess) {
                    publish("House LAN not detected · using saved stations")
                } else {
                    publish("House unavailable · keeping saved stations; will retry")
                }
                handler.postDelayed(periodic, INTERVAL_MS)
            }
        }
    }

    private fun publish(value: String) {
        status = value
        listeners.toList().forEach { it() }
    }

    private data class Target(val network: Network, val address: InetAddress)
    private fun findTarget(host: String): Target? {
        val networks = manager.allNetworks.filter { network ->
            manager.getNetworkCapabilities(network)?.let { caps ->
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } == true
        }
        if (networks.isEmpty()) {
            android.util.Log.i(TAG, "No physical LAN candidate")
            return null
        }
        for (address in InetAddress.getAllByName(host)) {
            for (network in networks) if (qualifies(network, address)) return Target(network, address)
        }
        android.util.Log.i(TAG, "Physical LAN present; configured server has no qualifying direct route")
        return null
    }

    private fun qualifies(network: Network, address: InetAddress): Boolean {
        val caps = manager.getNetworkCapabilities(network) ?: return false
        val links = manager.getLinkProperties(network) ?: return false
        val routes = links.routes.map { route ->
            val unicast = if (Build.VERSION.SDK_INT >= 33) route.type == RouteInfo.RTN_UNICAST
            else links.linkAddresses.any { local -> StationLanPolicy.hasMatchingLinkPrefix(
                route.destination.address, route.destination.prefixLength, local.address, local.prefixLength) }
            StationLanRoute(route.destination.address, route.destination.prefixLength,
                StationLanPolicy.hasGateway(route.gateway), unicast)
        }
        val qualified = StationLanPolicy.qualifies(
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN), caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
            address, routes)
        android.util.Log.i(TAG, "LAN check: vpn=${caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)} " +
            "notVpn=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)} " +
            "routes=${routes.size} direct=${routes.count { !it.hasGateway && it.unicast }} match=$qualified")
        return qualified
    }

    private fun fetch(address: InetAddress): List<HouseStationBookmark> {
        val host = address.hostAddress!!.let { if (it.contains(':')) "[$it]" else it }
        // Match the field-tested House routing: qualify a physical LAN, then use normal routing.
        val connection = URL("http://$host:8787/radio/stations").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2000; connection.readTimeout = 2500
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode == 200) { "House unavailable" }
            val bytes = connection.inputStream.use { it.readBytesBounded(1024 * 1024) }
            return HouseStationSnapshot.parse(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val count = read(buffer)
            if (count < 0) break
            check(out.size() + count <= limit) { "Station list too large" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "HouseStationRefresh"
        private const val INTERVAL_MS = 60_000L
        private const val MIN_GAP_MS = 5_000L
        @Volatile private var instance: HouseStationRefresh? = null
        fun get(context: Context): HouseStationRefresh = instance ?: synchronized(this) {
            instance ?: HouseStationRefresh(context).also { instance = it }
        }
    }
}
