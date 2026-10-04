package com.housemusic.player.house

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.housemusic.player.HouseDiagnostics
import com.housemusic.player.SortMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Service-owned HOUSE control/audio lifetime; Activities are only observers/controllers. */
@UnstableApi
class HouseRuntime(private val context: Context, endpoint: HouseEndpoint, private val changed: () -> Unit) : HousePlaybackState {
    private val connectionEpoch = HouseConnection.beginSession()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val bluetooth = HouseBluetoothPolicy(bluetoothDevices())
    private val outputPolicy = HouseOutputPolicy(bluetooth.connected)
    val api = HouseApi(context, endpoint)
    @Volatile override var state = HouseState()
        private set
    @Volatile override var connected = false
        private set
    @Volatile var muted = outputPolicy.muted
        private set
    @Volatile var status = if (endpoint.host.isBlank()) "HOUSE — set server address" else "HOUSE — connecting"
        private set
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val controls = Executors.newSingleThreadScheduledExecutor()
    private val presence = Executors.newSingleThreadScheduledExecutor()
    private val deviceId = HouseConnection.deviceId(context)
    private val rendererId = "$deviceId-audio"
    private var leaseId: String? = null // Only accessed by the presence executor.
    private var sequence = 0L
    @Volatile private var registered = false
    @Volatile private var homePresent = HouseConnection.isPresent(context, endpoint)
    override val canControl: Boolean get() = homePresent && connected && registered && state.ready &&
        !closed.get() && HouseConnection.epoch == connectionEpoch
    private val generation = AtomicLong(0)
    private val attachmentGeneration = AtomicLong(0)
    private val recoveryQueued = AtomicBoolean(false)
    private val networkRetryToken = Any()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = networkChanged()
        override fun onLost(network: Network) = networkChanged()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = networkChanged()
        override fun onLinkPropertiesChanged(network: Network, links: LinkProperties) = networkChanged()
    }

    private fun networkChanged(probeNow: Boolean = true) {
        main.post {
            if (closed.get()) return@post
            val present = HouseConnection.isPresent(context, api.endpoint)
            if (present != homePresent) {
                homePresent = present
                generation.incrementAndGet()
                if (!present) {
                    connected = false
                    HouseConnection.publish(connectionEpoch, null)
                    receiver.close()
                }
                outputChanged()
            }
            if (probeNow) requestNetworkRecovery()
        }
    }

    /** Coalesce callback bursts, then probe now instead of waiting for the heartbeat tick. */
    private fun requestNetworkRecovery() {
        main.removeCallbacksAndMessages(networkRetryToken)
        main.postAtTime({
            if (closed.get() || !recoveryQueued.compareAndSet(false, true)) return@postAtTime
            presence.execute {
                try {
                    recoverNetwork(forceProbe = true)
                    if (!closed.get()) {
                        renew()
                        if (!closed.get()) runCatching { controls.execute { refresh() } }
                    }
                } finally { recoveryQueued.set(false) }
            }
        }, networkRetryToken, android.os.SystemClock.uptimeMillis() + 200)
    }
    private var connectionRevision = 0L // Main thread; advanced only after a successful identity probe.
    private var queueAttachment = -1L
    private var queue: List<HouseTrack> = emptyList()
    private var queueVersion = -1
    private var defaultFolder = ""
    @Volatile private var commandError: String? = null
    private var pendingCommand: SettableFuture<SessionResult>? = null
    private var focusAllowed = false
    private var requestFocusWhenReady = !muted
    private fun bluetoothDevices(): Set<Int> = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.isSink && it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID) }
        .map { it.id }.toSet()
    @Volatile private var bluetoothConnected = bluetooth.connected
    private val audioDevices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = bluetoothChanged()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = bluetoothChanged()
    }
    private fun bluetoothChanged() {
        if (closed.get()) return
        val change = bluetooth.update(bluetoothDevices())
        bluetoothConnected = bluetooth.connected
        outputPolicy.updateRoute(bluetoothConnected, change == BluetoothOutputChange.UNMUTE)
        when (change) {
            BluetoothOutputChange.UNMUTE -> setMuted(false)
            BluetoothOutputChange.MUTE -> setMuted(true)
            BluetoothOutputChange.UNCHANGED -> { muted = outputPolicy.muted; reconcileOutput() }
        }
    }
    private fun syncKey(bluetooth: Boolean) = if (bluetooth) "sync_bluetooth_ms" else "sync_phone_ms"
    private fun requestedOffset(bluetooth: Boolean = bluetoothConnected): Int =
        HouseConnection.preferences(context).getInt(syncKey(bluetooth), 0).coerceIn(-1000, 1000)
    private val receiver = SnapcastReceiver(context) { main.post { reconcileOutput() } }
    val player = HousePlayer(this)
    private val wake = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HouseMusic:controller")
    @Suppress("DEPRECATION")
    private val wifi = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HouseMusic:network")
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener({ change ->
            if (closed.get()) return@setOnAudioFocusChangeListener
            focusAllowed = !muted && change == AudioManager.AUDIOFOCUS_GAIN
            if (!focusAllowed) receiver.close()
            reconcileOutput()
        }, main).build()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Close before Android can fall back to the phone speaker. No MPD command.
            setMuted(true)
        }
    }

    fun start() {
        if (closed.get()) return
        wake.acquire()
        wifi.acquire()
        ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        audio.registerAudioDeviceCallback(audioDevices, main)
        connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(), networkCallback)
        presence.scheduleWithFixedDelay({ renew() }, 0, 5, TimeUnit.SECONDS)
        controls.scheduleWithFixedDelay({ refresh() }, 0, 2, TimeUnit.SECONDS)
    }

    fun extras(): Bundle = Bundle().apply {
        putBoolean(EXTRA_HOUSE, true)
        putBoolean(EXTRA_MUTED, muted)
        putString(EXTRA_STATUS, commandError ?: status)
        putString(EXTRA_DEFAULT, if (connected) defaultFolder else "")
        putBoolean(EXTRA_CONNECTED, connected && registered && homePresent)
        putLong(EXTRA_CONNECTION_REVISION, connectionRevision)
        putBoolean(EXTRA_BLUETOOTH, bluetoothConnected)
        putInt(EXTRA_SYNC_OFFSET, requestedOffset())
        putInt(EXTRA_SYNC_APPLIED, receiver.timing.effectiveOffset(requestedOffset()))
        receiver.timing.bufferMs?.let { putInt(EXTRA_STREAM_BUFFER, it) }
        putInt(EXTRA_SERVER_LATENCY, receiver.timing.serverLatencyMs)
    }

    private fun announce() {
        main.post {
            if (!closed.get()) {
                player.refresh()
                changed()
            }
        }
    }

    private fun renew() {
        if (closed.get()) return
        if (!HouseConnection.isPresent(context, api.endpoint)) {
            recoverNetwork()
            networkChanged(probeNow = false)
            if (!HouseConnection.isPresent(context, api.endpoint)) return
        }
        try {
            val lease = leaseId
            if (lease == null) {
                val response = api.post("/controllers/attach", JSONObject().put("controllerId", deviceId)
                    .put("rendererId", rendererId).put("outputMuted", muted).put("outputReady", receiver.ready))
                leaseId = response.getString("leaseId")
                sequence = 0
                attachmentGeneration.incrementAndGet()
                if (closed.get()) { detach(); return }
            } else {
                api.post("/controllers/heartbeat", JSONObject().put("controllerId", deviceId)
                    .put("leaseId", lease).put("sequence", ++sequence)
                    .put("outputMuted", muted).put("outputReady", receiver.ready))
            }
            // A transient heartbeat failure disables writes but need not expire
            // the lease. Any successful renewal restores controller authority.
            main.post {
                if (!closed.get()) { registered = true; reconcileOutput() }
            }
        } catch (e: Exception) {
            main.post { if (!closed.get()) { registered = false; reconcileOutput() } }
            if (e is HouseApiException && e.status == 409) {
                val expiredLease = leaseId != null
                leaseId = null
                if (expiredLease && !closed.get()) runCatching { presence.execute { renew() } }
            }
            // Attach may have failed before receiving its token. Only lifecycle calls retry.
            // Ownership is durable, so an already-audible receiver may survive control loss.
            recoverNetwork()
        }
    }

    private fun recoverNetwork(forceProbe: Boolean = false) {
        // A transient control failure does not change the configured server or packet routing.
        if (closed.get() || (!forceProbe && HouseConnection.isPresent(context, api.endpoint))) return
        val replacement = HouseConnection.probe(context, api.endpoint.host) ?: return
        if (closed.get()) return
        if (!HouseConnection.publish(connectionEpoch, replacement)) return
        if (replacement != api.endpoint) {
            generation.incrementAndGet()
            api.endpoint = replacement
        }
        main.post {
            if (!closed.get() && HouseConnection.epoch == connectionEpoch) {
                connectionRevision++
                homePresent = HouseConnection.isPresent(context, replacement)
                reconcileOutput()
            }
        }
    }

    private fun refresh() {
        if (closed.get()) return
        val endpoint = api.endpoint
        val epoch = generation.get()
        if (!HouseConnection.isPresent(context, endpoint)) { networkChanged(probeNow = false); return }
        try {
            var nextQueue = queue
            var response = api.get("/state")
            val version = response.getJSONObject("mpd").optInt("queueVersion", -1)
            val attachment = attachmentGeneration.get()
            // MPD can reuse revision numbers after its own restart. A new lease
            // must reread the queue even when the numeric revision matches.
            if (version != queueVersion || attachment != queueAttachment) {
                val rows = api.get("/queue").getJSONArray("queue")
                val after = api.get("/state")
                if (after.getJSONObject("mpd").optInt("queueVersion", -1) != version) return // concurrent queue edit; retry read, not a write
                nextQueue = HouseState.tracks(rows)
                response = after
            }
            val next = HouseState.parse(response, nextQueue)
            val folder = response.optJSONObject("sessionPolicy")?.optString("defaultFolder").orEmpty()
            if (closed.get() || epoch != generation.get() || endpoint != api.endpoint ||
                !HouseConnection.isPresent(context, endpoint)) return
            queue = nextQueue
            queueVersion = version
            queueAttachment = attachment
            main.post {
                if (!closed.get() && epoch == generation.get() && endpoint == api.endpoint &&
                    HouseConnection.isPresent(context, endpoint)) {
                    state = next
                    commandError = null
                    defaultFolder = folder
                    connected = true
                    HouseConnection.publish(connectionEpoch, endpoint)
                    if (!state.ready) receiver.close()
                    reconcileOutput()
                }
            }
        } catch (_: Exception) {
            if (closed.get() || endpoint != api.endpoint || epoch != generation.get()) return
            main.post {
                if (!closed.get() && epoch == generation.get() && endpoint == api.endpoint) {
                    connected = false
                    HouseConnection.publish(connectionEpoch, null)
                    generation.incrementAndGet()
                    outputChanged()
                }
            }
        }
    }

    fun setMuted(value: Boolean) {
        if (closed.get()) return
        outputPolicy.requestMute(value)
        muted = outputPolicy.muted
        requestFocusWhenReady = !muted
        if (muted) {
            receiver.close()
            focusAllowed = false
            audio.abandonAudioFocusRequest(focus)
        }
        reconcileOutput()
    }

    private fun reconcileOutput() {
        if (closed.get()) return
        if (requestFocusWhenReady && !muted && homePresent && connected && registered && state.ready) {
            requestFocusWhenReady = false
            focusAllowed = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (!homePresent || muted || !focusAllowed || !state.ready) receiver.close()
        else if (registered) receiver.start(api.endpoint, rendererId,
            receiver.timing.effectiveOffset(requestedOffset()))
        outputChanged()
    }

    private var outputSignature = ""
    private var lastDiagnosticState = ""
    private fun outputChanged() {
        if (closed.get()) return
        status = when {
            api.endpoint.host.isBlank() -> "HOUSE — set server address"
            !homePresent -> "HOUSE — home network unavailable; reconnecting"
            !connected -> "HOUSE — server unavailable; reconnecting"
            !state.ready -> "HOUSE — server starting"
            !registered -> "HOUSE — controller reconnecting"
            muted -> "HOUSE — output muted"
            !focusAllowed -> "HOUSE — output interrupted"
            !receiver.ready -> "HOUSE — ${receiver.error ?: "audio connecting"}"
            else -> "HOUSE — output on"
        }
        if (canControl && state.tracks.isNotEmpty() && state.transport == "stop") {
            status = "HOUSE — stopped; press Play"
        }
        val diagnosticState = "$status | ${state.transport}"
        if (diagnosticState != lastDiagnosticState) {
            lastDiagnosticState = diagnosticState
            HouseDiagnostics.record(context, diagnosticState)
        }
        val signature = "$muted|${receiver.ready}"
        if (signature != outputSignature) {
            outputSignature = signature
            presence.execute { renew() }
        }
        announce()
    }

    override fun command(path: String, body: JSONObject): ListenableFuture<SessionResult> {
        val result = SettableFuture.create<SessionResult>()
        if (!canControl || pendingCommand != null) {
            result.set(SessionResult(SessionError.ERROR_INVALID_STATE))
            return result
        }
        pendingCommand = result
        commandError = null
        outputPolicy.transportChanged()
        val epoch = generation.get()
        controls.execute {
            var code = SessionError.ERROR_INVALID_STATE
            try {
                if (canControl && epoch == generation.get()) {
                    api.post(path, body)
                    code = SessionResult.RESULT_SUCCESS
                }
            } catch (e: Exception) {
                // A failed response may follow an applied command. Refresh reads only.
                code = SessionError.ERROR_UNKNOWN
                commandError = "HOUSE — command failed; refreshing playback"
                HouseDiagnostics.record(context, "Command $path failed: ${e.javaClass.simpleName}" +
                    if (e is HouseApiException) " (${e.status}/${e.code})" else "")
                announce()
            } finally {
                // Force queue as well as state: MPD may have restarted with a reused revision.
                queueVersion = -1
                runCatching { refresh() }
                // refresh publishes its snapshot on main first. Complete only after that publication,
                // so Media3 removes the pending operation against the new authoritative snapshot.
                main.post {
                    pendingCommand = null
                    result.set(SessionResult(code))
                    if (!closed.get()) {
                        player.refresh()
                        changed()
                    }
                }
            }
        }
        return result
    }

    fun custom(sessionCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> = when (sessionCommand.customAction) {
        MUTE -> { setMuted(!muted); Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)) }
        SYNC -> {
            val offset = args.getInt("offsetMs", Int.MAX_VALUE)
            if (offset !in -1000..1000 || closed.get()) {
                Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
            } else {
                HouseConnection.preferences(context).edit()
                    .putInt(syncKey(args.getBoolean("bluetooth")), offset).apply()
                reconcileOutput()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        PLAY_LIST -> {
            val tracks = args.getStringArrayList("tracks").orEmpty()
            if (tracks.isEmpty()) Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
            else {
                command("/queue/replace", JSONObject().put("tracks", JSONArray(tracks))
                    .put("startIndex", 0).put("play", true).put("positionSeconds", 0))
            }
        }
        DEFAULT -> command("/settings", JSONObject().put("passiveDefaultFolder", args.getString("folder")))
        SORT -> {
            val mode = SortMode.fromStorage(args.getString("mode"))
            val snapshot = state
            val sorted = when (mode) {
                SortMode.NAME_ASC -> snapshot.tracks.sortedBy { it.file.substringAfterLast('/').lowercase() }
                SortMode.NAME_DESC -> snapshot.tracks.sortedByDescending { it.file.substringAfterLast('/').lowercase() }
                SortMode.DATE_ASC -> snapshot.tracks.sortedWith(compareBy<HouseTrack> { it.modified }.thenBy { it.file.lowercase() })
                SortMode.DATE_DESC -> snapshot.tracks.sortedWith(compareByDescending<HouseTrack> { it.modified }.thenBy { it.file.lowercase() })
            }
            val pivot = sorted.indexOfFirst { it.id == snapshot.songId }.coerceAtLeast(0)
            val ids = (sorted.drop(pivot) + sorted.take(pivot)).map { it.id }
            command("/queue/reorder", JSONObject().put("songIds", JSONArray(ids)).put("queueVersion", snapshot.queueVersion))
        }
        else -> Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }

    private fun detach() {
        val lease = leaseId ?: return
        runCatching { api.post("/controllers/detach", JSONObject().put("controllerId", deviceId).put("leaseId", lease)) }
        leaseId = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        HouseConnection.clear(connectionEpoch)
        generation.incrementAndGet()
        main.removeCallbacksAndMessages(null)
        // Clear the Media3 presentation now, even while Activities remain bound.
        connected = false
        registered = false
        muted = true
        requestFocusWhenReady = false
        state = HouseState()
        pendingCommand?.set(SessionResult(SessionError.ERROR_INVALID_STATE))
        pendingCommand = null
        commandError = null
        defaultFolder = ""
        status = "HOUSE — closed"
        player.refresh()
        receiver.close()
        audio.abandonAudioFocusRequest(focus)
        audio.unregisterAudioDeviceCallback(audioDevices)
        runCatching { context.unregisterReceiver(noisy) }
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        controls.shutdownNow()
        // Serialized after any in-flight attach; that late attach also observes closed.
        presence.execute { detach() }
        presence.shutdown()
        if (wake.isHeld) wake.release()
        if (wifi.isHeld) wifi.release()
    }

    companion object {
        const val EXTRA_HOUSE = "house"
        const val EXTRA_MUTED = "house.muted"
        const val EXTRA_STATUS = "house.status"
        const val EXTRA_DEFAULT = "house.default"
        const val EXTRA_CONNECTED = "house.connected"
        const val EXTRA_CONNECTION_REVISION = "house.connectionRevision"
        const val EXTRA_BLUETOOTH = "house.bluetooth"
        const val EXTRA_SYNC_OFFSET = "house.syncOffset"
        const val EXTRA_SYNC_APPLIED = "house.syncApplied"
        const val EXTRA_STREAM_BUFFER = "house.streamBuffer"
        const val EXTRA_SERVER_LATENCY = "house.serverLatency"
        const val MUTE = "house.toggleMute"
        const val SYNC = "house.sync"
        const val PLAY_LIST = "house.playList"
        const val SORT = "house.sort"
        const val DEFAULT = "house.defaultFolder"
        val CUSTOM_COMMANDS = listOf(MUTE, PLAY_LIST, SORT, DEFAULT, SYNC)
    }
}
