package com.smbmusic.player.house

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
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
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.smbmusic.player.SortMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Service-owned HOUSE control/audio lifetime; Activities are only observers/controllers. */
@UnstableApi
class HouseRuntime(private val context: Context, endpoint: HouseEndpoint, private val changed: () -> Unit) {
    val api = HouseApi(context, endpoint)
    @Volatile var state = HouseState()
        private set
    @Volatile var connected = false
        private set
    @Volatile var muted = true
        private set
    @Volatile var status = "HOUSE — connecting"
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
    val canControl: Boolean get() = homePresent && connected && registered && state.ready && !closed.get()
    private val generation = AtomicLong(0)
    private val attachmentGeneration = AtomicLong(0)
    private val muteRevision = AtomicLong(0)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = networkChanged()
        override fun onLost(network: Network) = networkChanged()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = networkChanged()
        override fun onLinkPropertiesChanged(network: Network, links: LinkProperties) = networkChanged()
    }

    private fun networkChanged() {
        main.post {
            if (closed.get()) return@post
            val present = HouseConnection.isPresent(context, api.endpoint)
            if (present != homePresent) {
                homePresent = present
                generation.incrementAndGet()
                if (!present) {
                    connected = false
                    receiver.close()
                }
                outputChanged()
            }
        }
    }
    private var queueAttachment = -1L
    private var queue: List<HouseTrack> = emptyList()
    private var queueVersion = -1
    private var defaultFolder = ""
    @Volatile private var commandError: String? = null
    private var focusAllowed = false
    private val audio = context.getSystemService(AudioManager::class.java)
    private val receiver = SnapcastReceiver(context) { main.post { outputChanged() } }
    val player = HousePlayer(this)
    private val wake = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMBMusic:house-controller")
    @Suppress("DEPRECATION")
    private val wifi = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SMBMusic:house-network")
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener({ change ->
            focusAllowed = change == AudioManager.AUDIOFOCUS_GAIN
            if (!focusAllowed) receiver.close()
            reconcileOutput()
        }, main).build()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            focusAllowed = false
            receiver.close()
            audio.abandonAudioFocusRequest(focus)
            outputChanged()
        }
    }

    fun start() {
        wake.acquire()
        wifi.acquire()
        ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
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
        putString(EXTRA_DEFAULT, defaultFolder)
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
            networkChanged()
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
            registered = true
            main.post { reconcileOutput() }
        } catch (e: Exception) {
            registered = false
            if (e is HouseApiException && e.status == 409) leaseId = null
            // Attach may have failed before receiving its token. Only lifecycle calls retry.
            // Ownership is durable, so an already-audible receiver may survive control loss.
            recoverNetwork()
        }
    }

    private fun recoverNetwork() {
        // A failed server/heartbeat alone is not departure. Do not pin replacement traffic.
        if (closed.get() || HouseConnection.isPresent(context, api.endpoint)) return
        val replacement = HouseConnection.probe(context) ?: return
        if (closed.get()) return
        if (replacement != api.endpoint) {
            generation.incrementAndGet()
            api.endpoint = replacement
            HouseConnection.current = replacement
            main.post {
                if (!closed.get()) {
                    homePresent = HouseConnection.isPresent(context, replacement)
                    receiver.close()
                    reconcileOutput()
                }
            }
        }
    }

    private fun refresh() {
        if (closed.get()) return
        val endpoint = api.endpoint
        val epoch = generation.get()
        if (!HouseConnection.isPresent(context, endpoint)) { networkChanged(); return }
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
            state = next
            defaultFolder = folder
            connected = true
            main.post {
                if (!closed.get()) {
                    if (!state.ready) receiver.close()
                    reconcileOutput()
                }
            }
        } catch (_: Exception) {
            if (closed.get() || endpoint != api.endpoint || epoch != generation.get()) return
            connected = false
            generation.incrementAndGet()
            status = "HOUSE — server unavailable; reconnecting"
            announce()
        }
    }

    fun setMuted(value: Boolean) {
        if (closed.get() || (!value && (!homePresent || !connected || !state.ready))) return
        muteRevision.incrementAndGet()
        muted = value
        if (muted) {
            receiver.close()
            focusAllowed = false
            audio.abandonAudioFocusRequest(focus)
        } else {
            focusAllowed = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        reconcileOutput()
    }

    private fun reconcileOutput() {
        if (closed.get()) return
        if (!homePresent || muted || !focusAllowed || !state.ready) receiver.close()
        else if (registered) receiver.start(api.endpoint, rendererId)
        outputChanged()
    }

    private var outputSignature = ""
    private fun outputChanged() {
        if (closed.get()) return
        status = when {
            !homePresent -> "HOUSE — home network unavailable; reconnecting"
            !connected -> "HOUSE — server unavailable; reconnecting"
            !state.ready -> "HOUSE — server starting"
            !registered -> "HOUSE — controller reconnecting"
            muted -> "HOUSE — output muted"
            !focusAllowed -> "HOUSE — output interrupted"
            !receiver.ready -> "HOUSE — ${receiver.error ?: "audio connecting"}"
            else -> "HOUSE — output on"
        }
        val signature = "$muted|${receiver.ready}"
        if (signature != outputSignature) {
            outputSignature = signature
            presence.execute { renew() }
        }
        announce()
    }

    fun command(path: String, body: JSONObject = JSONObject(),
                playbackStart: HousePlaybackStart = HousePlaybackStart.NONE): ListenableFuture<SessionResult> {
        val result = SettableFuture.create<SessionResult>()
        if (!canControl) {
            result.set(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)); return result
        }
        commandError = null
        val epoch = generation.get()
        val outputEpoch = muteRevision.get()
        controls.execute {
            if (!canControl || epoch != generation.get()) {
                result.set(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)); return@execute
            }
            try {
                // Fresh reads precede the write. The periodic UI snapshot can be two seconds old,
                // and the post-command transport would always say play regardless of prior listeners.
                val before = if (muted && playbackStart != HousePlaybackStart.NONE) api.get("/state") else null
                val transport = before?.getJSONObject("mpd")?.getString("transport").orEmpty()
                val presence = if (playbackStart == HousePlaybackStart.QUEUE && transport == "play")
                    api.get("/controllers") else null
                val unmute = HouseOutputPolicy.shouldUnmute(playbackStart, muted, transport, presence, deviceId)
                if (!canControl || epoch != generation.get()) {
                    result.set(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)); return@execute
                }
                api.post(path, body)
                refresh()
                main.post {
                    // Never override a newer manual mute, a lost command response, or a departed session.
                    if (unmute && canControl && epoch == generation.get() && outputEpoch == muteRevision.get()) setMuted(false)
                    result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            } catch (e: Exception) {
                // The server may have applied a write whose response was lost. Never retry it.
                generation.incrementAndGet()
                commandError = "HOUSE — ${e.message ?: "command failed"}; refresh before retrying"
                refresh()
                announce()
                result.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return result
    }

    fun custom(sessionCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> = when (sessionCommand.customAction) {
        MUTE -> { setMuted(!muted); Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)) }
        PLAY_LIST -> {
            val tracks = args.getStringArrayList("tracks").orEmpty()
            if (tracks.isEmpty()) Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
            else {
                command("/queue/replace", JSONObject().put("tracks", JSONArray(tracks))
                    .put("startIndex", 0).put("play", true).put("positionSeconds", 0), HousePlaybackStart.QUEUE)
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
        else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
    }

    private fun detach() {
        val lease = leaseId ?: return
        runCatching { api.post("/controllers/detach", JSONObject().put("controllerId", deviceId).put("leaseId", lease)) }
        leaseId = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        receiver.close()
        audio.abandonAudioFocusRequest(focus)
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
        const val MUTE = "house.toggleMute"
        const val PLAY_LIST = "house.playList"
        const val SORT = "house.sort"
        const val DEFAULT = "house.defaultFolder"
        val CUSTOM_COMMANDS = listOf(MUTE, PLAY_LIST, SORT, DEFAULT)
    }
}
