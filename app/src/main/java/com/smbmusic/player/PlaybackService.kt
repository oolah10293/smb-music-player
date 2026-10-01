package com.smbmusic.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import com.smbmusic.player.house.HouseConnection
import com.smbmusic.player.house.HouseRuntime
import com.smbmusic.player.house.HouseApi
import com.smbmusic.player.house.HouseEndpoint
import com.smbmusic.player.house.HouseLibraryMapping
import com.smbmusic.player.house.HouseLibraryPaths
import com.smbmusic.player.house.PlaybackTransitionPolicy
import com.smbmusic.player.smb.SmbClient
import com.smbmusic.player.smb.SmbDataSource
import com.smbmusic.player.storage.CredentialStore
import com.smbmusic.player.storage.StandaloneSessionStore
import com.smbmusic.player.storage.StandaloneSnapshot
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var house: HouseRuntime? = null
    private var released = false
    private var selectionEpoch = 0L
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var smb: SmbClient
    private lateinit var executor: ExecutorService
    private lateinit var connectivityManager: ConnectivityManager

    private val handler = Handler(Looper.getMainLooper())

    private var recovering = false
    private var rebuildingBuffer = false
    private var recoveryPaused = false
    private var recoveryGeneration = 0
    private var retryIndex = 0
    private var retryDueAtMs = 0L
    private var resumePositionMs = 0L
    private var resumeMediaIndex = 0
    private var resumeShouldPlay = true
    private var recoveryUrl = ""

    private var probeInFlight = false
    private var probeAgainRequested = false
    private var activeProbeId = 0
    private var activeProbeFuture: Future<*>? = null

    private var rebuildStartedAtMs = 0L
    private var lastBufferProgressAtMs = 0L
    private var lastBufferedPositionMs = 0L

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastPublishedRecoverySignature = ""

    private var fadeGeneration = 0
    private var fadeArmed = false
    private var skipNextUserFade = false

    private val retryScheduleMs = longArrayOf(1_000, 2_000, 5_000, 10_000, 15_000)

    // All Player access and mode decisions run on the main looper. Workers only receive
    // immutable snapshots and use this generation to reject stale network results/writes.
    private val modeGeneration = AtomicLong(0)
    private val transitions = Executors.newSingleThreadExecutor()
    private lateinit var retained: StandaloneSessionStore
    private lateinit var audio: AudioManager
    private var bluetoothConnected = false
    private var explicitlyStopped = false
    private var houseProbeInFlight = false
    private var modeProbeAgainRequested = false
    private var lastHouseProbeAt = -5_000L
    private var lastPersistAt = 0L
    private var transitionStatus: String? = null
    private var pendingHandoffId: String? = null
    private var pendingEndpoint: HouseEndpoint? = null
    private var pendingStopRequested = false
    private var handoffWorking = false
    private var handoffTerminal = false
    private val temporaryLeases = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun bluetoothOutputs(): Set<Int> = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.isSink && it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID) }
        .map { it.id }.toSet()
    private val audioDevices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = bluetoothChanged()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = bluetoothChanged()
    }

    private fun bluetoothChanged() {
        if (released) return
        val previous = bluetoothConnected
        bluetoothConnected = bluetoothOutputs().isNotEmpty()
        if (house != null || previous == bluetoothConnected) return
        if (!bluetoothConnected) {
            player.pause()
            if (recovering) pauseRecovery()
            saveStandalone()
            if (keepStandaloneReady()) updateStandaloneStandbyNotification()
        } else if (canBluetoothResume()) resumeStandalone()
    }

    private fun canBluetoothResume(): Boolean = PlaybackTransitionPolicy.bluetoothResume(
        bluetoothConnected, player.mediaItemCount > 0, explicitlyStopped, pendingHandoffId != null)

    private fun resumeStandalone() {
        if (released || house != null || pendingHandoffId != null || explicitlyStopped) return
        if (recovering) {
            resumeShouldPlay = true
            recoveryPaused = false
            requestImmediateRecoveryProbe()
        } else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
            player.play()
        }
    }

    private fun standaloneSnapshot(): StandaloneSnapshot = StandaloneSnapshot(
        (0 until player.mediaItemCount).map { player.getMediaItemAt(it) },
        player.currentMediaItemIndex.coerceAtLeast(0),
        (if (recovering) resumePositionMs else player.currentPosition).coerceAtLeast(0),
        player.shuffleModeEnabled, explicitlyStopped)

    private fun saveStandalone() {
        if (!::retained.isInitialized || !::player.isInitialized || released) return
        runCatching { retained.save(standaloneSnapshot()) }
        lastPersistAt = SystemClock.uptimeMillis()
    }

    private fun transitionMessage(message: String?) {
        transitionStatus = message
        lastPublishedRecoverySignature = ""
        publishRecoveryStatus(if (recoveryPaused) RECOVERY_PHASE_PAUSED else RECOVERY_PHASE_IDLE)
        if (pendingHandoffId != null && ::session.isInitialized && !released) updateTransitionNotification()
    }

    private fun scheduleModeMonitor() {
        handler.postAtTime({
            if (!released) { monitorMode(); scheduleModeMonitor() }
        }, MODE_TOKEN, SystemClock.uptimeMillis() + 2_000)
    }

    private fun monitorMode() {
        if (released) return
        val runtime = house
        if (runtime != null) {
            // Failed /state, /health, Snapcast, or heartbeat requests are never proof of
            // leaving home. Only loss of the qualified physical route changes mode.
            if (!HouseConnection.isPresent(this, runtime.api.endpoint)) leaveHouse(runtime)
            return
        }
        if (SystemClock.uptimeMillis() - lastPersistAt >= 5_000 && pendingHandoffId == null) saveStandalone()
        if (handoffWorking || handoffTerminal || houseProbeInFlight ||
            SystemClock.uptimeMillis() - lastHouseProbeAt < 5_000) return
        val generation = modeGeneration.get()
        houseProbeInFlight = true
        lastHouseProbeAt = SystemClock.uptimeMillis()
        executor.execute {
            val endpoint = runCatching { HouseConnection.probe(this) }.getOrNull()
            handler.post {
                houseProbeInFlight = false
                if (!released && house == null && generation == modeGeneration.get() && endpoint != null &&
                    HouseConnection.isPresent(this, endpoint)) houseFound(endpoint)
                if (!released && modeProbeAgainRequested) {
                    modeProbeAgainRequested = false
                    lastHouseProbeAt = -5_000L
                    monitorMode()
                }
            }
        }
    }

    private fun enterHouse(endpoint: HouseEndpoint) {
        if (released || !HouseConnection.isPresent(this, endpoint)) return
        modeGeneration.incrementAndGet()
        cancelRecovery()
        cancelFade(resetToFull = true)
        player.pause()
        // A later cold start in HOUSE must not resurrect an old private queue.
        explicitlyStopped = true
        saveStandalone()
        player.stop()
        lateinit var runtime: HouseRuntime
        runtime = HouseRuntime(this, endpoint) {
            if (!released && house === runtime) {
                session.setSessionExtras(runtime.extras())
                updateHouseNotification()
            }
        }
        house = runtime
        HouseConnection.publish(selectionEpoch, endpoint)
        session.setPlayer(runtime.player)
        session.setSessionExtras(runtime.extras())
        updateHouseNotification()
        runtime.start()
    }

    private fun leaveHouse(runtime: HouseRuntime) {
        val departure = runtime.departureSnapshot()
        val metadata = runtime.state.tracks.firstOrNull { it.file == departure?.file }
        val wasStopped = runtime.state.transport == "stop"
        modeGeneration.incrementAndGet()
        cancelRecovery()
        cancelFade(resetToFull = true)
        runtime.close()
        house = null
        HouseConnection.publish(selectionEpoch, null)
        player.pause()
        player.clearMediaItems()
        val url = departure?.let { HouseLibraryPaths.smb(HouseLibraryMapping.root(this), it.file) }
        explicitlyStopped = wasStopped
        if (departure != null && url != null) {
            val item = MediaItem.Builder().setMediaId(url).setUri(url)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(metadata?.title?.takeIf { it.isNotBlank() } ?: departure.file.substringAfterLast('/'))
                    .setArtist(metadata?.artist).setAlbumTitle(metadata?.album).build()).build()
            // The approved departure carries the heard track, not an unapproved copy of
            // the entire HOUSE queue into a new private playlist.
            player.setMediaItem(item, departure.positionMs)
        }
        session.setPlayer(player)
        runtime.player.release()
        lastPublishedRecoverySignature = ""
        transitionMessage(if (departure != null && url == null)
            "SMB — set HOUSE music root on SMB to continue this song away from home" else null)
        if (departure != null && url != null && PlaybackTransitionPolicy.continueDeparture(
                departure.shouldPlay, bluetoothConnected, explicitlyStopped)) resumeStandalone()
        saveStandalone()
        // Media3 recreates the standalone playback notification when ExoPlayer starts.
        getSystemService(NotificationManager::class.java).cancel(2401)
        if (keepStandaloneReady()) updateStandaloneStandbyNotification()
        else if (!player.playWhenReady) stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun houseFound(endpoint: HouseEndpoint) {
        if (released || house != null || !HouseConnection.isPresent(this, endpoint)) return
        val unresolved = pendingHandoffId
        if (unresolved != null) {
            pendingEndpoint = endpoint
            resolveHandoff(endpoint, unresolved)
            return
        }
        val playing = player.playWhenReady || (recovering && resumeShouldPlay && !recoveryPaused)
        if (!playing || explicitlyStopped || player.mediaItemCount == 0) {
            enterHouse(endpoint)
            return
        }
        // Freeze the heard position before reserving/committing. From this point a failed
        // transfer is silent and reviewable, never simultaneous private and HOUSE music.
        val snapshot = standaloneSnapshot()
        player.pause()
        cancelRecovery()
        saveStandalone()
        val musicRoot = HouseLibraryMapping.root(this)
        val tracks = snapshot.items.map { item -> HouseLibraryPaths.relative(musicRoot,
            item.localConfiguration?.uri?.toString() ?: item.mediaId) }
        val id = UUID.randomUUID().toString()
        pendingHandoffId = id
        pendingEndpoint = endpoint
        handoffTerminal = false
        if (runCatching { retained.saveHandoff(id) }.isFailure) {
            handoffTerminal = true
            transitionMessage("Return home paused — could not retain transfer state; use Stop transfer")
            return
        }
        if (tracks.any { it == null }) {
            // No request was sent. Keep a durable unresolved marker so a restart cannot
            // accidentally adopt a default queue after silently abandoning this session.
            handoffTerminal = true
            transitionMessage("Return home paused — set HOUSE music root on SMB; use Stop transfer in the notification")
            return
        }
        val generation = modeGeneration.incrementAndGet()
        handoffWorking = true
        transitionMessage("Returning home — transferring current playlist")
        transitions.execute {
            val api = HouseApi(this, endpoint)
            val identity = handoffIdentity(id)
            var response: JSONObject? = null
            var failure: String? = null
            try {
                checkTransition(generation, endpoint)
                response = api.post("/session/handoff/prepare", identity)
                if (handoffStatus(response) == "reserved") {
                    checkTransition(generation, endpoint)
                    val lease = api.post("/controllers/attach", JSONObject()
                        .put("controllerId", HouseConnection.deviceId(this))
                        .put("rendererId", HouseConnection.deviceId(this) + "-audio")
                        .put("outputMuted", true).put("outputReady", false)).getString("leaseId")
                    temporaryLeases[id] = lease
                    checkTransition(generation, endpoint)
                    response = api.post("/session/handoff/commit", handoffIdentity(id)
                        .put("tracks", JSONArray(tracks)).put("startIndex", snapshot.index)
                        .put("positionSeconds", snapshot.positionMs / 1000.0)
                        .put("shuffle", snapshot.shuffle).put("repeat", true))
                }
            } catch (error: Exception) {
                failure = error.message
                // A timeout may follow a completed commit. Read its receipt, never replay
                // queue replacement or invent a new ID from a stale saved snapshot.
                response = runCatching { api.post("/session/handoff/status", identity) }.getOrNull()
            }
            handler.post { finishHandoff(endpoint, id, generation, response, failure) }
        }
    }

    private fun checkTransition(generation: Long, endpoint: HouseEndpoint) {
        check(!released && modeGeneration.get() == generation && HouseConnection.isPresent(this, endpoint)) {
            "Playback transfer cancelled or home network lost"
        }
    }

    private fun handoffIdentity(id: String) = JSONObject()
        .put("controllerId", HouseConnection.deviceId(this)).put("handoffId", id)
    private fun handoffStatus(response: JSONObject?): String = response?.optJSONObject("handoff")?.optString("status").orEmpty()

    private fun resolveHandoff(endpoint: HouseEndpoint, id: String) {
        if (handoffWorking || handoffTerminal) return
        if (pendingStopRequested) { cancelHandoff(stopTransferredSession = true); return }
        handoffWorking = true
        val generation = modeGeneration.get()
        transitions.execute {
            var failure: String? = null
            val response = try { HouseApi(this, endpoint).post("/session/handoff/status", handoffIdentity(id)) }
                catch (error: Exception) { failure = error.message; null }
            handler.post { finishHandoff(endpoint, id, generation, response, failure) }
        }
    }

    private fun finishHandoff(endpoint: HouseEndpoint, id: String, generation: Long,
                              response: JSONObject?, failure: String?) {
        if (released || pendingHandoffId != id || generation != modeGeneration.get()) return
        handoffWorking = false
        val status = handoffStatus(response)
        if (PlaybackTransitionPolicy.mayAdoptHandoff(status) && HouseConnection.isPresent(this, endpoint)) {
            retained.clearHandoff()
            pendingHandoffId = null
            pendingEndpoint = null
            temporaryLeases.remove(id) // Runtime replaces this exact controller's temporary lease.
            transitionMessage(null)
            enterHouse(endpoint)
        } else {
            handoffTerminal = status in setOf("failed", "expired", "cancelled", "unknown", "reserved")
            transitionMessage(when (status) {
                "failed" -> "Return home paused — transfer failed; use Stop transfer in the notification"
                "unknown" -> "Return home paused — server has no transfer receipt; use Stop transfer in the notification"
                "expired", "cancelled", "reserved" -> "Return home paused — transfer incomplete; use Stop transfer in the notification"
                else -> "Return home paused — checking transfer status${failure?.let { ": $it" }.orEmpty()}"
            })
        }
    }

    private fun cancelHandoff(stopTransferredSession: Boolean) {
        val id = pendingHandoffId ?: return
        val endpoint = pendingEndpoint
        modeGeneration.incrementAndGet()
        pendingStopRequested = stopTransferredSession
        handoffWorking = endpoint != null
        handoffTerminal = false
        if (stopTransferredSession) {
            runCatching { retained.saveHandoff(id, stopRequested = true) }
            transitionMessage("Return home paused — cancelling transfer")
        } else {
            pendingHandoffId = null
            pendingEndpoint = null
            retained.clearHandoff()
            transitionMessage(null)
        }
        if (endpoint != null) transitions.execute {
            val api = HouseApi(this, endpoint)
            // Serialized after any commit, so an explicit Stop wins even if that write's
            // response was delayed. Quit only cancels/detaches and leaves other rooms alone.
            val cancelled = runCatching { api.post("/session/handoff/cancel", handoffIdentity(id)) }.getOrNull()
            val status = handoffStatus(cancelled)
            var stopped = true
            var ambiguousStop = false
            if (stopTransferredSession && status in setOf("committed", "failed")) {
                // Send Stop only once. An ambiguous response remains paused for explicit
                // user review rather than automatically stopping a later HOUSE session.
                stopped = runCatching {
                    check(retained.markStopSent(id)) { "Stop already attempted or transfer closed" }
                    api.post("/stop")
                }.isSuccess
                ambiguousStop = !stopped
            }
            temporaryLeases.remove(id)?.let { lease -> runCatching {
                api.post("/controllers/detach", JSONObject().put("controllerId", HouseConnection.deviceId(this)).put("leaseId", lease))
            } }
            handler.post {
                if (!released && pendingHandoffId == id && pendingStopRequested) {
                    handoffWorking = false
                    if (cancelled != null && stopped) {
                        pendingHandoffId = null
                        pendingEndpoint = null
                        pendingStopRequested = false
                        retained.clearHandoff()
                        transitionMessage("SMB — playback stopped")
                        clearStoppedNotification()
                    } else {
                        handoffTerminal = ambiguousStop
                        transitionMessage("Return home paused — Stop could not be confirmed; Quit to close the phone")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        selectionEpoch = HouseConnection.epoch

        val initialHouse = HouseConnection.current
        HouseConnection.publish(selectionEpoch, null)
        retained = StandaloneSessionStore(this)
        pendingHandoffId = retained.handoffId()
        pendingStopRequested = retained.handoffStopRequested()
        handoffTerminal = retained.handoffStopSent()
        audio = getSystemService(AudioManager::class.java)
        bluetoothConnected = bluetoothOutputs().isNotEmpty()

        smb = SmbClient(CredentialStore(this))
        // A timed-out jcifs attempt must not permanently block every later recovery probe.
        // Cached workers let a later attempt proceed even if an old blocked call is slow to die.
        executor = Executors.newCachedThreadPool()

        val mediaSourceFactory = DefaultMediaSourceFactory(SmbDataSource.Factory(smb))
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))

        // v0.3+ is deliberately aggressive. When the network is good, bank a large
        // amount of compressed audio so rural tower handoffs/dead spots do not matter.
        // A normal song will often be buffered in full; long tracks are capped at 10 min.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                START_PLAYBACK_BUFFER_MS,
                RECOVERY_RESUME_BUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(TARGET_BUFFER_BYTES)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            // v0.3.6: explicit music attributes + audio-focus handling. This is proven to
            // establish the vehicle audio path directly and must remain enabled.
            .setAudioAttributes(audioAttributes, true)
            // The original test device is an older phone and v0.1 paused when the screen slept.
            // NETWORK wake mode holds both CPU and Wi-Fi locks while actively playing/buffering.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ALL
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (house == null && pendingHandoffId == null && !released) beginOutageRecovery(error)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // A deliberate Next/Previous/new selection invalidates recovery work for the
                // former song. Queue replacements are also handled in SessionCallback.
                if (recovering && mediaItem != null && mediaItem.mediaId != recoveryUrl) {
                    cancelRecovery()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                // Repeat All is a fixed standalone-player policy. External controllers may
                // request another mode, but the player immediately restores the approved mode.
                if (repeatMode != Player.REPEAT_MODE_ALL) {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    fadeArmed = false
                    cancelFade(resetToFull = true)

                    if (
                        recovering &&
                        (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG)
                    ) {
                        pauseRecovery()
                    }
                    return
                }
                if (house == null && pendingHandoffId == null) {
                    getSystemService(NotificationManager::class.java).cancel(2401)
                }

                // Fade only when a real user/controller requests playback. Automatic track
                // transitions do not change playWhenReady, and outage recovery is explicitly
                // exempted below so the proven recovery path is not altered.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    if (skipNextUserFade) {
                        skipNextUserFade = false
                        fadeArmed = false
                        player.volume = 1f
                    } else {
                        fadeArmed = true
                        cancelFade(resetToFull = false)
                        // Set the stream low immediately so a buffered resume cannot produce a
                        // full-volume first sample before the playback-state callback arrives.
                        player.volume = FADE_START_VOLUME
                        if (player.isPlaying) startFadeIn()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && fadeArmed) startFadeIn()
            }
        })

        session = MediaLibrarySession.Builder(
            this,
            player,
            SessionCallback()
        ).build()

        retained.load()?.let { saved ->
            explicitlyStopped = saved.explicitlyStopped
            player.setMediaItems(saved.items, saved.index, saved.positionMs)
            player.shuffleModeEnabled = saved.shuffle
        }
        publishRecoveryStatus(RECOVERY_PHASE_IDLE)
        if (pendingHandoffId != null) transitionMessage(if (handoffTerminal)
            "Return home paused — Stop outcome uncertain; Quit to close the phone" else "Return home paused — checking retained transfer")
        registerNetworkCallback()
        audio.registerAudioDeviceCallback(audioDevices, handler)
        if (initialHouse != null && HouseConnection.isPresent(this, initialHouse)) {
            // Treat a retained session exactly like a live one when Bluetooth was already on.
            if (canBluetoothResume()) resumeStandalone()
            houseFound(initialHouse)
        } else {
            if (canBluetoothResume()) resumeStandalone()
            monitorMode()
        }
        scheduleModeMonitor()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        if (released) null else session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (released) return
        if (house != null) updateHouseNotification()
        else if (pendingHandoffId != null) updateTransitionNotification()
        else if (keepStandaloneReady()) updateStandaloneStandbyNotification()
        else super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun keepStandaloneReady(): Boolean = !released && house == null && pendingHandoffId == null &&
        ::player.isInitialized && player.mediaItemCount > 0 && !explicitlyStopped &&
        !player.playWhenReady && !bluetoothConnected

    private fun clearStoppedNotification() {
        if (house == null && pendingHandoffId == null && explicitlyStopped) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).cancel(2401)
        }
    }

    private fun updateStandaloneStandbyNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("house", "House music", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, NowPlayingActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val quit = PendingIntent.getService(this, 2403, Intent(this, PlaybackService::class.java).setAction(ACTION_QUIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "house").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(player.mediaMetadata.title ?: "SMB Music — paused")
            .setContentText("Bluetooth disconnected — session retained")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Quit", quit).build()
        ServiceCompat.startForeground(this, 2401, notification,
            if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
    }

    private fun updateTransitionNotification() {
        if (released) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("house", "House music", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, NowPlayingActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2402,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP_TRANSFER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "house").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("SMB Music — returning home")
            .setContentText(transitionStatus ?: "Checking playlist transfer")
            .setStyle(NotificationCompat.BigTextStyle().bigText(transitionStatus))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop transfer", stop).build()
        ServiceCompat.startForeground(this, 2401, notification,
            if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
    }

    private fun updateHouseNotification() {
        if (released) return
        val runtime = house ?: return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("house", "House music", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, NowPlayingActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String) = PendingIntent.getService(this, name.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val playing = runtime.state.transport == "play"
        val notification = NotificationCompat.Builder(this, "house").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(runtime.player.mediaMetadata.title ?: "SMB Music — HOUSE")
            .setContentText(runtime.extras().getString(HouseRuntime.EXTRA_STATUS)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(HOUSE_PREVIOUS))
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play", action(HOUSE_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, "Next", action(HOUSE_NEXT))
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2)).build()
        // Connected-device foreground lifetime keeps muted/background controllers present too.
        ServiceCompat.startForeground(this, 2401, notification,
            if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) {
            releasePlayback(explicitQuit = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).cancel(2401)
            stopSelf()
            return START_NOT_STICKY
        }
        if (released) return START_NOT_STICKY
        if (intent?.action == ACTION_STOP_TRANSFER) {
            explicitlyStopped = true
            player.pause()
            player.stop()
            cancelRecovery()
            cancelHandoff(stopTransferredSession = true)
            saveStandalone()
            clearStoppedNotification()
            return START_NOT_STICKY
        }
        house?.let { runtime ->
            when (intent?.action) {
                HOUSE_PLAY_PAUSE -> if (runtime.state.transport == "play") runtime.player.pause() else runtime.player.play()
                HOUSE_NEXT -> runtime.player.seekToNextMediaItem()
                HOUSE_PREVIOUS -> runtime.player.seekToPreviousMediaItem()
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            if (controller.packageName == packageName) HouseRuntime.CUSTOM_COMMANDS.forEach {
                commands.add(SessionCommand(it, Bundle.EMPTY))
            }
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands.build())
            if (controller.packageName == GARMIN_CONNECT_PACKAGE) {
                result.setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            }
            return result.build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> =
            house?.custom(customCommand, args)
                ?: Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))

        @Suppress("DEPRECATION")
        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo,
                                            playerCommand: Int): Int {
            // An ambiguous commit must not let a Bluetooth or UI Play restart private audio.
            // Stop remains available and cancels the pending transfer explicitly.
            if (house == null && pendingHandoffId != null && playerCommand != Player.COMMAND_STOP &&
                playerCommand in TRANSFER_COMMANDS) return SessionResult.RESULT_ERROR_INVALID_STATE
            return SessionResult.RESULT_SUCCESS
        }

        override fun onPlayerInteractionFinished(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            playerCommands: Player.Commands
        ) {
            if (house != null || released) return
            if (playerCommands.contains(Player.COMMAND_STOP)) {
                explicitlyStopped = true
                modeGeneration.incrementAndGet()
                cancelHandoff(stopTransferredSession = true)
                player.pause()
            } else if (playerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS) ||
                       (playerCommands.contains(Player.COMMAND_PLAY_PAUSE) && player.playWhenReady)) {
                explicitlyStopped = false
                transitionStatus = null
            }
            // A replacement queue or explicit Stop makes all recovery callbacks for the old
            // request stale. The newly requested queue is allowed to establish its own state.
            if (
                recovering &&
                (playerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS) ||
                    playerCommands.contains(Player.COMMAND_STOP))
            ) {
                cancelRecovery()
            } else if (recovering && playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
                if (player.playWhenReady) {
                    // During recovery, Play means "keep trying now". Hold the player silent until
                    // SMB is verified and the useful recovery buffer has been rebuilt.
                    resumeShouldPlay = true
                    recoveryPaused = false
                    player.pause()
                    requestImmediateRecoveryProbe()
                } else {
                    // Pause is authoritative even though the player was already internally
                    // paused while waiting for SMB.
                    pauseRecovery()
                }
            }

            if (player.repeatMode != Player.REPEAT_MODE_ALL) {
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
            saveStandalone()
            clearStoppedNotification()
        }
    }

    private fun startFadeIn() {
        fadeArmed = false
        val generation = ++fadeGeneration
        handler.removeCallbacksAndMessages(FADE_TOKEN)
        player.volume = FADE_START_VOLUME
        val startedAt = SystemClock.uptimeMillis()

        fun step() {
            if (generation != fadeGeneration || !player.playWhenReady) return
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val fraction = (elapsed.toFloat() / FADE_DURATION_MS).coerceIn(0f, 1f)
            player.volume = FADE_START_VOLUME + (1f - FADE_START_VOLUME) * fraction
            if (fraction < 1f) {
                handler.postAtTime(
                    { step() },
                    FADE_TOKEN,
                    SystemClock.uptimeMillis() + FADE_STEP_MS
                )
            } else {
                player.volume = 1f
            }
        }

        step()
    }

    private fun cancelFade(resetToFull: Boolean) {
        fadeGeneration++
        handler.removeCallbacksAndMessages(FADE_TOKEN)
        if (resetToFull && ::player.isInitialized) player.volume = 1f
    }

    private fun beginOutageRecovery(error: PlaybackException) {
        val mediaItem = player.currentMediaItem ?: return

        // A late loader error after an explicit Pause/Stop must not create a brand-new
        // automatic-resume request. Errors that occur inside an existing recovery session
        // still return to the retry path below.
        if (!recovering && !player.playWhenReady) return

        // If a second error happens while rebuilding the recovery buffer, retain the
        // original intent to resume rather than treating our forced pause as a user pause.
        val shouldResume = if (recovering) resumeShouldPlay else player.playWhenReady

        recoveryGeneration++
        cancelActiveProbe()
        clearRecoveryCallbacks()

        recovering = true
        rebuildingBuffer = false
        recoveryPaused = false
        retryIndex = 0
        resumePositionMs = player.currentPosition.coerceAtLeast(0L)
        resumeMediaIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        resumeShouldPlay = shouldResume
        recoveryUrl = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            .ifBlank { mediaItem.mediaId }

        player.pause()
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!recovering || recoveryPaused || recoveryUrl.isBlank()) return

        handler.removeCallbacksAndMessages(RETRY_TOKEN)
        handler.removeCallbacksAndMessages(STATUS_TOKEN)
        handler.removeCallbacksAndMessages(NETWORK_RETRY_TOKEN)

        val delay = retryScheduleMs[minOf(retryIndex, retryScheduleMs.lastIndex)]
        retryIndex++
        retryDueAtMs = SystemClock.uptimeMillis() + delay
        publishRecoveryStatus(
            phase = RECOVERY_PHASE_WAITING,
            retryInMs = delay
        )
        scheduleRetryStatusTick()
        handler.postAtTime(
            { retrySmb() },
            RETRY_TOKEN,
            retryDueAtMs
        )
    }

    private fun scheduleRetryStatusTick() {
        handler.postAtTime(
            {
                if (!recovering || recoveryPaused || rebuildingBuffer || probeInFlight) {
                    return@postAtTime
                }
                val remaining = (retryDueAtMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
                publishRecoveryStatus(
                    phase = RECOVERY_PHASE_WAITING,
                    retryInMs = remaining
                )
                if (remaining > 0L) scheduleRetryStatusTick()
            },
            STATUS_TOKEN,
            SystemClock.uptimeMillis() + STATUS_TICK_MS
        )
    }

    private fun retrySmb() {
        if (!recovering || recoveryPaused || rebuildingBuffer || recoveryUrl.isBlank()) return
        if (probeInFlight) {
            probeAgainRequested = true
            return
        }

        handler.removeCallbacksAndMessages(RETRY_TOKEN)
        handler.removeCallbacksAndMessages(STATUS_TOKEN)
        handler.removeCallbacksAndMessages(NETWORK_RETRY_TOKEN)

        val url = recoveryUrl
        val generation = recoveryGeneration
        val probeId = ++activeProbeId
        probeInFlight = true
        probeAgainRequested = false
        publishRecoveryStatus(RECOVERY_PHASE_PROBING)

        activeProbeFuture = executor.submit {
            val ok = smb.probeFile(url)
            handler.post {
                if (
                    !recovering ||
                    recoveryPaused ||
                    generation != recoveryGeneration ||
                    probeId != activeProbeId ||
                    recoveryUrl != url
                ) {
                    return@post
                }

                handler.removeCallbacksAndMessages(PROBE_TIMEOUT_TOKEN)
                probeInFlight = false
                activeProbeFuture = null

                if (ok) {
                    beginRecoveryBufferRebuild()
                } else if (probeAgainRequested) {
                    probeAgainRequested = false
                    handler.post { retrySmb() }
                } else {
                    scheduleRetry()
                }
            }
        }

        handler.postAtTime(
            { handleProbeTimeout(generation, probeId) },
            PROBE_TIMEOUT_TOKEN,
            SystemClock.uptimeMillis() + PROBE_ATTEMPT_TIMEOUT_MS
        )
    }

    private fun handleProbeTimeout(generation: Int, probeId: Int) {
        if (
            !recovering ||
            recoveryPaused ||
            generation != recoveryGeneration ||
            probeId != activeProbeId ||
            !probeInFlight
        ) {
            return
        }

        // A single timed-out attempt is not the recovery session. Invalidate its late result,
        // interrupt it where jcifs permits, and schedule another attempt.
        activeProbeId++
        probeInFlight = false
        activeProbeFuture?.cancel(true)
        activeProbeFuture = null
        val retryImmediately = probeAgainRequested
        probeAgainRequested = false
        if (retryImmediately) handler.post { retrySmb() } else scheduleRetry()
    }

    private fun beginRecoveryBufferRebuild() {
        rebuildingBuffer = true
        recoveryPaused = false
        handler.removeCallbacksAndMessages(RETRY_TOKEN)
        handler.removeCallbacksAndMessages(STATUS_TOKEN)
        handler.removeCallbacksAndMessages(NETWORK_RETRY_TOKEN)
        handler.removeCallbacksAndMessages(BUFFER_TOKEN)

        // Do not immediately blast a few hundred milliseconds of audio and stall again.
        // Hold playback while ExoPlayer banks a real safety margin first.
        player.playWhenReady = false
        player.seekTo(resumeMediaIndex, resumePositionMs)
        player.prepare()

        rebuildStartedAtMs = SystemClock.uptimeMillis()
        lastBufferProgressAtMs = rebuildStartedAtMs
        lastBufferedPositionMs = resumePositionMs
        publishRecoveryStatus(
            phase = RECOVERY_PHASE_REBUILDING,
            bufferedAheadMs = 0L,
            requiredBufferMs = RECOVERY_RESUME_BUFFER_MS.toLong()
        )
        scheduleRecoveryBufferCheck(0L)
    }

    private fun scheduleRecoveryBufferCheck(delayMs: Long = BUFFER_CHECK_INTERVAL_MS) {
        handler.postAtTime(
            { checkRecoveryBuffer() },
            BUFFER_TOKEN,
            SystemClock.uptimeMillis() + delayMs
        )
    }

    private fun checkRecoveryBuffer() {
        if (!recovering || recoveryPaused || !rebuildingBuffer) return

        val now = SystemClock.uptimeMillis()
        if (player.playerError != null) {
            // prepare() may expose the previous error for a brief moment. Give it a short grace
            // period, then return to the SMB-probe phase rather than spinning forever.
            if (now - rebuildStartedAtMs < STALE_ERROR_GRACE_MS) {
                scheduleRecoveryBufferCheck()
            } else {
                restartRecoveryAfterRebuildFailure()
            }
            return
        }

        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val bufferedPosition = player.bufferedPosition.coerceAtLeast(currentPosition)
        val bufferedAhead = bufferedPosition - currentPosition
        val duration = player.duration
        val remaining = if (duration != C.TIME_UNSET && duration >= currentPosition) {
            duration - currentPosition
        } else {
            C.TIME_UNSET
        }
        val required = if (remaining != C.TIME_UNSET) {
            minOf(RECOVERY_RESUME_BUFFER_MS.toLong(), remaining)
        } else {
            RECOVERY_RESUME_BUFFER_MS.toLong()
        }
        val bufferedToEnd = duration != C.TIME_UNSET && bufferedPosition >= duration - END_BUFFER_SLOP_MS

        if (bufferedPosition >= lastBufferedPositionMs + MIN_BUFFER_PROGRESS_MS) {
            lastBufferedPositionMs = bufferedPosition
            lastBufferProgressAtMs = now
        }

        publishRecoveryStatus(
            phase = RECOVERY_PHASE_REBUILDING,
            bufferedAheadMs = bufferedAhead,
            requiredBufferMs = required
        )

        if (bufferedAhead >= required || bufferedToEnd || player.playbackState == Player.STATE_ENDED) {
            completeRecovery()
        } else if (now - lastBufferProgressAtMs >= REBUILD_NO_PROGRESS_TIMEOUT_MS) {
            // Closing the current source via stop() ensures a genuinely stuck refill does not
            // strand the recovery state. The queue remains intact and the saved position is used
            // on the next prepare.
            restartRecoveryAfterRebuildFailure()
        } else {
            scheduleRecoveryBufferCheck()
        }
    }

    private fun completeRecovery() {
        clearRecoveryCallbacks()
        cancelActiveProbe()

        recovering = false
        rebuildingBuffer = false
        recoveryPaused = false
        retryIndex = 0
        retryDueAtMs = 0L
        recoveryUrl = ""
        publishRecoveryStatus(RECOVERY_PHASE_IDLE)

        player.repeatMode = Player.REPEAT_MODE_ALL
        if (resumeShouldPlay) {
            // Recovery resume is automatic, not an explicit user Play action.
            skipNextUserFade = true
            player.play()
        }
    }

    private fun restartRecoveryAfterRebuildFailure() {
        if (!recovering || recoveryPaused) return
        recoveryGeneration++
        rebuildingBuffer = false
        handler.removeCallbacksAndMessages(BUFFER_TOKEN)
        runCatching { player.stop() }
        scheduleRetry()
    }

    private fun pauseRecovery() {
        if (!recovering) return

        resumePositionMs = player.currentPosition.coerceAtLeast(resumePositionMs)
        resumeMediaIndex = player.currentMediaItemIndex.coerceAtLeast(resumeMediaIndex)
        resumeShouldPlay = false
        recoveryPaused = true
        rebuildingBuffer = false
        recoveryGeneration++
        clearRecoveryCallbacks()
        cancelActiveProbe()
        runCatching { player.stop() }
        publishRecoveryStatus(RECOVERY_PHASE_PAUSED)
    }

    private fun cancelRecovery() {
        if (!recovering && !rebuildingBuffer && !recoveryPaused) return

        recoveryGeneration++
        clearRecoveryCallbacks()
        cancelActiveProbe()
        recovering = false
        rebuildingBuffer = false
        recoveryPaused = false
        retryIndex = 0
        retryDueAtMs = 0L
        recoveryUrl = ""
        publishRecoveryStatus(RECOVERY_PHASE_IDLE)
    }

    private fun clearRecoveryCallbacks() {
        handler.removeCallbacksAndMessages(RETRY_TOKEN)
        handler.removeCallbacksAndMessages(BUFFER_TOKEN)
        handler.removeCallbacksAndMessages(STATUS_TOKEN)
        handler.removeCallbacksAndMessages(PROBE_TIMEOUT_TOKEN)
        handler.removeCallbacksAndMessages(NETWORK_RETRY_TOKEN)
    }

    private fun cancelActiveProbe() {
        activeProbeId++
        probeInFlight = false
        probeAgainRequested = false
        activeProbeFuture?.cancel(true)
        activeProbeFuture = null
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun changed() = handler.post {
                if (!released) {
                    requestImmediateRecoveryProbe()
                    if (house != null) monitorMode()
                    else {
                        // A fresh physical route bypasses the old five-second probe timer.
                        // If an away probe is already in flight, remember to probe again.
                        lastHouseProbeAt = -5_000L
                        if (houseProbeInFlight) modeProbeAgainRequested = true
                        handler.removeCallbacksAndMessages(MODE_NETWORK_TOKEN)
                        handler.postAtTime({ monitorMode() }, MODE_NETWORK_TOKEN,
                            SystemClock.uptimeMillis() + 200)
                    }
                }
            }
            override fun onAvailable(network: Network) { changed() }
            override fun onLost(network: Network) { changed() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { changed() }
            override fun onLinkPropertiesChanged(network: Network, links: LinkProperties) { changed() }
        }
        runCatching {
            // Default-network callbacks can describe only the VPN while the physical Wi-Fi
            // arrives/leaves underneath it. Observe all physical networks, including cellular.
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
            networkCallback = callback
        }
    }

    private fun requestImmediateRecoveryProbe() {
        if (!recovering || recoveryPaused || rebuildingBuffer || recoveryUrl.isBlank()) return

        if (probeInFlight) {
            probeAgainRequested = true
            return
        }

        // Connectivity callbacks can arrive in bursts. Debounce them, then bring the next real
        // SMB probe forward. Network availability itself is never treated as SMB success.
        handler.removeCallbacksAndMessages(NETWORK_RETRY_TOKEN)
        handler.removeCallbacksAndMessages(RETRY_TOKEN)
        handler.removeCallbacksAndMessages(STATUS_TOKEN)
        retryDueAtMs = 0L
        publishRecoveryStatus(
            phase = RECOVERY_PHASE_WAITING,
            retryInMs = NETWORK_EVENT_DEBOUNCE_MS
        )
        handler.postAtTime(
            { retrySmb() },
            NETWORK_RETRY_TOKEN,
            SystemClock.uptimeMillis() + NETWORK_EVENT_DEBOUNCE_MS
        )
    }

    private fun publishRecoveryStatus(
        phase: String,
        retryInMs: Long = 0L,
        bufferedAheadMs: Long = 0L,
        requiredBufferMs: Long = 0L
    ) {
        if (!::session.isInitialized || house != null || released) return

        // Round rapidly changing values to whole seconds so a 250 ms buffer check does not flood
        // every controller with redundant Binder updates.
        val roundedRetry = if (retryInMs <= 0L) 0L else ((retryInMs + 999L) / 1000L) * 1000L
        val roundedBuffered = if (bufferedAheadMs <= 0L) 0L else (bufferedAheadMs / 1000L) * 1000L
        val roundedRequired = if (requiredBufferMs <= 0L) 0L else ((requiredBufferMs + 999L) / 1000L) * 1000L
        val signature = "$phase|$roundedRetry|$roundedBuffered|$roundedRequired|$transitionStatus"
        if (signature == lastPublishedRecoverySignature) return
        lastPublishedRecoverySignature = signature

        session.setSessionExtras(
            Bundle().apply {
                putBoolean(HouseRuntime.EXTRA_HOUSE, false)
                putString(SESSION_EXTRA_TRANSITION_STATUS, transitionStatus)
                putString(SESSION_EXTRA_RECOVERY_PHASE, phase)
                putLong(SESSION_EXTRA_RETRY_IN_MS, roundedRetry)
                putLong(SESSION_EXTRA_BUFFERED_AHEAD_MS, roundedBuffered)
                putLong(SESSION_EXTRA_REQUIRED_BUFFER_MS, roundedRequired)
            }
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (released || house != null) return
        // Keep an active playback or pending recovery session alive if the UI is swiped away.
        if (!isPlaybackOngoing() && !recovering && pendingHandoffId == null &&
            (explicitlyStopped || player.mediaItemCount == 0)) stopSelf()
    }

    /** Runs immediately on explicit Quit, even while an Activity still binds the service. */
    private fun releasePlayback(explicitQuit: Boolean = false) {
        if (released) return
        if (explicitQuit) {
            explicitlyStopped = true
            cancelHandoff(stopTransferredSession = false)
        }
        if (house == null || explicitQuit) saveStandalone()
        released = true
        modeGeneration.incrementAndGet()
        cancelRecovery()
        cancelFade(resetToFull = false)
        handler.removeCallbacksAndMessages(null)
        networkCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        networkCallback = null
        audio.unregisterAudioDeviceCallback(audioDevices)
        val runtime = house
        house = null
        runtime?.close() // Detach local controller only; other rooms continue.
        session.release()
        runtime?.player?.release()
        player.release()
        executor.shutdownNow()
        // Finish any serialized cancel behind a possibly completed commit; never interrupt it
        // at an unknown write boundary and then automatically restart the retained SMB queue.
        transitions.shutdown()
        HouseConnection.clear(selectionEpoch)
    }

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }

    companion object {
        const val ACTION_QUIT = "com.smbmusic.player.QUIT"
        private const val ACTION_STOP_TRANSFER = "com.smbmusic.player.STOP_TRANSFER"
        private const val HOUSE_PLAY_PAUSE = "house.notification.playPause"
        private const val HOUSE_NEXT = "house.notification.next"
        private const val HOUSE_PREVIOUS = "house.notification.previous"
        private val RETRY_TOKEN = Any()
        private val BUFFER_TOKEN = Any()
        private val STATUS_TOKEN = Any()
        private val PROBE_TIMEOUT_TOKEN = Any()
        private val NETWORK_RETRY_TOKEN = Any()
        private val FADE_TOKEN = Any()

        private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"

        const val SESSION_EXTRA_TRANSITION_STATUS = "com.smbmusic.player.transition.STATUS"
        private val MODE_TOKEN = Any()
        private val MODE_NETWORK_TOKEN = Any()
        private val TRANSFER_COMMANDS = setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE,
            Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_REPEAT_MODE)
        const val SESSION_EXTRA_RECOVERY_PHASE = "com.smbmusic.player.recovery.PHASE"
        const val SESSION_EXTRA_RETRY_IN_MS = "com.smbmusic.player.recovery.RETRY_IN_MS"
        const val SESSION_EXTRA_BUFFERED_AHEAD_MS = "com.smbmusic.player.recovery.BUFFERED_AHEAD_MS"
        const val SESSION_EXTRA_REQUIRED_BUFFER_MS = "com.smbmusic.player.recovery.REQUIRED_BUFFER_MS"

        const val RECOVERY_PHASE_IDLE = "idle"
        const val RECOVERY_PHASE_WAITING = "waiting"
        const val RECOVERY_PHASE_PROBING = "probing"
        const val RECOVERY_PHASE_REBUILDING = "rebuilding"
        const val RECOVERY_PHASE_PAUSED = "paused"

        // Large streaming buffer for unreliable cellular/Tailscale paths.
        private const val MIN_BUFFER_MS = 120_000
        private const val MAX_BUFFER_MS = 600_000
        private const val TARGET_BUFFER_BYTES = 32 * 1024 * 1024
        private const val START_PLAYBACK_BUFFER_MS = 3_000
        private const val RECOVERY_RESUME_BUFFER_MS = 20_000
        private const val BUFFER_CHECK_INTERVAL_MS = 250L
        private const val END_BUFFER_SLOP_MS = 500L

        // Recovery hardening. These protect the overall session without declaring a slow but
        // progressing transfer dead.
        private const val STATUS_TICK_MS = 1_000L
        private const val NETWORK_EVENT_DEBOUNCE_MS = 750L
        private const val PROBE_ATTEMPT_TIMEOUT_MS = 30_000L
        private const val STALE_ERROR_GRACE_MS = 2_000L
        private const val REBUILD_NO_PROGRESS_TIMEOUT_MS = 45_000L
        private const val MIN_BUFFER_PROGRESS_MS = 250L

        // Softens explicit Play/Resume when the phone's media volume is already high.
        private const val FADE_DURATION_MS = 1_500L
        private const val FADE_STEP_MS = 50L
        private const val FADE_START_VOLUME = 0.04f
    }
}
