package com.smbmusic.player

import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Metadata
import androidx.media3.common.MediaMetadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.exoplayer.source.ShuffleOrder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.smbmusic.player.radio.*
import com.smbmusic.player.media.SourceDataSource
import com.smbmusic.player.media.SourceLoadControl
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.smbmusic.player.smb.SmbClient
import com.smbmusic.player.storage.CredentialStore
import com.smbmusic.player.storage.StandaloneSessionStore
import com.smbmusic.player.storage.StandaloneSnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

@UnstableApi
class PlaybackService : MediaLibraryService() {
    @Volatile private var radio = false
    private val recoveryIntent = RecoveryIntent()
    private var wanted: Boolean
        get() = recoveryIntent.requested
        set(value) { recoveryIntent.requested = value }
    private var holdReason: String
        get() = recoveryIntent.hold
        set(value) { recoveryIntent.hold = value }
    private var requestingFocus = false
    private var radioResolveFuture: Future<*>? = null
    private var awaitingPlayback = false
    private var sourceSwitching = false
    private var restoreAutoplay = false
    private lateinit var wake: RecoveryWake
    private lateinit var radioStore: RadioStore
    private var station: RadioStation? = null
    private var savedPlaylist: StandaloneSnapshot? = null
    private val history = RadioHistory()
    private var radioSong = RadioSong("")
    private var broadcastName = ""
    private var radioQuality = ""
    private var radioError = ""
    private var radioGeneration = 0
    private var radioRetry = 0
    private var radioLastProgress = 0L
    private var radioLastPosition = 0L
    private var radioLastTick = 0L
    private var listeningMs = 0L
    private var recoveryLogAt = 0L
    private var released = false
    private var serviceStarted = false
    private var resumeRetryIndex = 0
    private var resumeStatus = ""
    private val resumeRetryDelays = longArrayOf(1_000, 2_000, 5_000, 10_000, 15_000)

    private val playbackIntent = StandalonePlaybackIntent()
    private lateinit var retained: StandaloneSessionStore
    private lateinit var audio: AudioManager
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

    private val audioDevices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = bluetoothChanged()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = bluetoothChanged()
    }

    private fun bluetoothAvailable(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { it.isSink && it.type in BLUETOOTH_OUTPUT_TYPES }

    private fun bluetoothChanged() {
        if (released) return
        when (playbackIntent.bluetoothChanged(bluetoothAvailable(), player.mediaItemCount > 0)) {
            StandalonePlaybackIntent.BluetoothAction.PAUSE -> {
                cancelPendingResume()
                PlaybackDiagnostics.record(this, "Bluetooth audio disconnected; retaining position")
                player.pause()
                if (!radio && recovering) pauseRecovery("output")
                holdReason = "output"
                if (radio) stopRadioWork()
                saveStandalone()
                updateStandbyNotification()
            }
            StandalonePlaybackIntent.BluetoothAction.RESUME -> resumeStandalone()
            StandalonePlaybackIntent.BluetoothAction.NONE -> Unit
        }
    }

    private fun resumeStandalone() {
        if (released || player.isPlaying || !playbackIntent.requestResume(player.mediaItemCount > 0)) return
        wanted = true
        holdReason = ""
        resumeRetryIndex = 0
        PlaybackDiagnostics.record(this, "Bluetooth audio available; resume requested")
        attemptPendingResume()
    }

    private fun attemptPendingResume() {
        if (released || !playbackIntent.canAttemptResume(player.mediaItemCount > 0)) return
        handler.removeCallbacksAndMessages(RESUME_RETRY_TOKEN)
        if (!bluetoothAvailable()) { bluetoothChanged(); return }
        setResumeStatus("Resuming Bluetooth playback…")
        // Promote the started playback service before requesting focus. A rejected promotion
        // leaves the intent pending; the system Bluetooth broadcast can retry under its grant.
        if (updateStandbyNotification("Resuming Bluetooth playback")) {
            if (!radio) TailscaleConnector.request(this, "Bluetooth resume")
            if (radio) { openLiveRadio(); return }
            if (recovering) {
                resumeShouldPlay = true
                recoveryPaused = false
                requestImmediateRecoveryProbe()
            } else {
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.play()
            }
        }
        // Cover asynchronous audio-focus rejection as well as foreground start failure.
        // Keep a bounded retry burst, then retain intent for the next connection/user event.
        if (playbackIntent.resumePending && resumeRetryIndex < resumeRetryDelays.size) {
            val delay = resumeRetryDelays[resumeRetryIndex++]
            handler.postAtTime({
                if (!recovering) attemptPendingResume()
            }, RESUME_RETRY_TOKEN, SystemClock.uptimeMillis() + delay)
        }
    }

    private fun cancelPendingResume() {
        playbackIntent.cancelResume()
        handler.removeCallbacksAndMessages(RESUME_RETRY_TOKEN)
        handler.removeCallbacksAndMessages(BLUETOOTH_ROUTE_TOKEN)
        setResumeStatus("")
    }

    private fun setResumeStatus(value: String) {
        if (resumeStatus == value) return
        resumeStatus = value
        if (::session.isInitialized && !released) {
            session.setSessionExtras(Bundle(session.sessionExtras).apply {
                putString(SESSION_EXTRA_RESUME_STATUS, value)
            })
        }
    }

    private fun checkBluetoothRoute(attempt: Int = 0) {
        if (released || playbackIntent.explicitlyStopped) return
        val alreadyPending = playbackIntent.resumePending
        bluetoothChanged()
        if (alreadyPending && playbackIntent.resumePending) attemptPendingResume()
        // ACL connection arrives before A2DP route readiness on many devices. Never play
        // merely because an accessory connected; wait for a real Android audio sink.
        if (!bluetoothAvailable() && attempt < 20) {
            handler.removeCallbacksAndMessages(BLUETOOTH_ROUTE_TOKEN)
            handler.postAtTime({ checkBluetoothRoute(attempt + 1) }, BLUETOOTH_ROUTE_TOKEN,
                SystemClock.uptimeMillis() + 500L)
        }
    }

    private fun saveStandalone() {
        if (released || !::retained.isInitialized || !::player.isInitialized) return
        runCatching {
            retained.save(snapshot().copy(radio = radio, stationId = station?.id.orEmpty(),
                stationName = station?.name.orEmpty(), playlist = savedPlaylist))
        }
    }

    private fun schedulePersistence() {
        handler.postAtTime({
            if (!released) {
                if (player.playWhenReady || recovering) saveStandalone()
                schedulePersistence()
            }
        }, PERSIST_TOKEN, SystemClock.uptimeMillis() + 5_000)
    }

    override fun onCreate() {
        super.onCreate()
        PlaybackDiagnostics.record(this, "Playback service created")

        wake = RecoveryWake(this)
        radioStore = RadioStore(this)
        history.restore(radioStore.previous())
        retained = StandaloneSessionStore(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        playbackIntent.restore(stopped = true, bluetoothAvailable = bluetoothAvailable())
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(PLAYBACK_NOTIFICATION_ID).setChannelId(PLAYBACK_CHANNEL)
            .setChannelName(R.string.app_name).build())

        smb = SmbClient(CredentialStore(this))
        // A timed-out jcifs attempt must not permanently block every later recovery probe.
        // Cached workers let a later attempt proceed even if an old blocked call is slow to die.
        executor = Executors.newCachedThreadPool()

        val mediaSourceFactory = DefaultMediaSourceFactory(SourceDataSource.Factory(smb))
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))

        // v0.3+ is deliberately aggressive. When the network is good, bank a large
        // amount of compressed audio so rural tower handoffs/dead spots do not matter.
        // A normal song will often be buffered in full; long tracks are capped at 10 min.
        val loadControl = SourceLoadControl({ radio })

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
                PlaybackDiagnostics.record(this@PlaybackService, "Player error: ${error.errorCodeName}")
                if (!released && !sourceSwitching) {
                    if (radio) { if (wanted && holdReason.isBlank()) scheduleRadioRetry(error.errorCodeName) }
                    else beginOutageRecovery(error)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // A deliberate Next/Previous/new selection invalidates recovery work for the
                // former song. Queue replacements are also handled in SessionCallback.
                if (!sourceSwitching && radio && mediaItem?.localConfiguration?.uri?.scheme == "smb") {
                    leaveRadioForNewPlaylist()
                }
                if (!radio && recovering && mediaItem != null && mediaItem.mediaId != recoveryUrl) {
                    cancelRecovery()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                // Repeat All is a fixed standalone-player policy. External controllers may
                // request another mode, but the player immediately restores the approved mode.
                if (!radio && repeatMode != Player.REPEAT_MODE_ALL) {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (released) return
                if (playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST &&
                    player.mediaItemCount > 0) playbackIntent.playbackRequested()
                if (!playWhenReady) {
                    if (playbackIntent.resumePending && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                        PlaybackDiagnostics.record(this@PlaybackService, "Bluetooth resume waiting for audio focus")
                        setResumeStatus("Bluetooth resume waiting for audio focus")
                    }
                    fadeArmed = false
                    cancelFade(resetToFull = true)

                    if (
                        !radio && recovering &&
                        (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG)
                    ) {
                        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                            holdReason = "output"; pauseRecovery("output")
                        } else if (awaitingPlayback && requestingFocus) {
                            PlaybackDiagnostics.record(this@PlaybackService, "Recovery resume waiting for focus")
                            scheduleAwaitingPlayback()
                        } else {
                            holdReason = "focus"; pauseRecovery("focus")
                        }
                    }
                    if (radio && !sourceSwitching && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS && awaitingPlayback && requestingFocus) {
                            scheduleRadioRetry("Waiting for audio focus")
                        } else { holdReason = "output/focus"; stopRadioWork(); publishRadio(); saveStandalone() }
                    }
                    return
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
                if (isPlaying && playbackIntent.resumePending) {
                    PlaybackDiagnostics.record(this@PlaybackService, "Bluetooth resume playing")
                    cancelPendingResume()
                }
                if (isPlaying && awaitingPlayback) {
                    PlaybackDiagnostics.record(this@PlaybackService, "Recovery playback confirmed")
                    awaitingPlayback = false
                    if (!radio) finishRecovery()
                    else { recovering = false; radioRetry = 0; radioError = ""; wake.cancel(); publishRadio() }
                }
                if (isPlaying && fadeArmed) startFadeIn()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (radio && !sourceSwitching) updateRadioMetadata(mediaMetadata.title?.toString().orEmpty(),
                    mediaMetadata.artist?.toString().orEmpty(), mediaMetadata.albumTitle?.toString().orEmpty())
            }
            override fun onMetadata(metadata: Metadata) {
                if (!radio || sourceSwitching) return
                for (i in 0 until metadata.length()) when (val entry = metadata[i]) {
                    is IcyInfo -> updateRadioMetadata(entry.title.orEmpty())
                    is IcyHeaders -> {
                        broadcastName = entry.name.orEmpty()
                        if (entry.bitrate > 0) radioQuality = "${entry.bitrate / 1000} kbps"
                        station?.let { radioStore.broadcastName(it.id, broadcastName) }
                        station = radioStore.stations().firstOrNull { it.id == station?.id } ?: station
                        publishRadio()
                    }
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (!radio) return
                tracks.groups.forEach { group ->
                    for (i in 0 until group.length) if (group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        val parts = mutableListOf<String>()
                        if (format.bitrate > 0) parts += "${format.bitrate / 1000} kbps"
                        format.sampleMimeType?.let { parts += it.removePrefix("audio/") }
                        if (format.sampleRate > 0) parts += "${format.sampleRate / 1000.0} kHz"
                        if (parts.isNotEmpty()) radioQuality = parts.joinToString(" · ")
                        format.metadata?.let { onMetadata(it) }
                    }
                }
                publishRadio()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (radio && state == Player.STATE_ENDED && wanted && holdReason.isBlank() && !sourceSwitching)
                    scheduleRadioRetry("Stream ended")
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                    events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) saveStandalone()
            }
        })

        session = MediaLibrarySession.Builder(
            this,
            object : ForwardingPlayer(player) {
                override fun play() = userPlay()
                override fun pause() = userPause()
                override fun setPlayWhenReady(playWhenReady: Boolean) { if (playWhenReady) userPlay() else userPause() }
                override fun stop() {
                    userPause(); playbackIntent.stop(); cancelRecovery(); player.stop(); saveStandalone()
                }
            },
            SessionCallback()
        ).build()

        retained.load()?.let { saved ->
            playbackIntent.restore(saved.explicitlyStopped, bluetoothAvailable())
            radio = saved.radio; savedPlaylist = saved.playlist
            wanted = saved.shouldPlay; restoreAutoplay = saved.shouldPlay
            if (saved.bluetoothRequired && !bluetoothAvailable()) holdReason = "output"
            if (radio) station = RadioStation(saved.stationId, saved.stationName,
                saved.items.first().localConfiguration!!.uri.toString())
            player.setMediaItems(saved.items, saved.index, if (radio) 0 else saved.positionMs)
            player.shuffleModeEnabled = saved.shuffle
            if (saved.shuffleOrder.isNotEmpty()) player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(saved.shuffleOrder.toIntArray(), 0))
            player.repeatMode = if (radio) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
        }
        publishRecoveryStatus(RECOVERY_PHASE_IDLE)
        registerNetworkCallback()
        audio.registerAudioDeviceCallback(audioDevices, handler)
        // Let a simultaneous Quit command run before considering a preconnected output.
        handler.post {
            if (!released) {
                if (restoreAutoplay && playbackIntent.canResume(player.mediaItemCount > 0)) resumeStandalone()
                else if (keepStandaloneReady()) updateStandbyNotification()
            }
        }
        schedulePersistence()
        scheduleRadioTick()
        publishRadio()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        if (released) null else session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (released) return
        if (playbackIntent.explicitlyStopped && !player.playWhenReady) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).cancel(PLAYBACK_NOTIFICATION_ID)
        } else if (keepStandaloneReady()) updateStandbyNotification()
        else super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun keepStandaloneReady(): Boolean = !released && ::player.isInitialized &&
        player.mediaItemCount > 0 && !playbackIntent.explicitlyStopped &&
        (!player.playWhenReady || playbackIntent.resumePending) &&
        (recovering || !playbackIntent.bluetoothConnected || playbackIntent.resumePending)

    /** Keep the retained, user-started session available through a Bluetooth disconnect/outage. */
    private fun updateStandbyNotification(status: String? = null): Boolean {
        if (released || !::session.isInitialized) return false
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(PLAYBACK_CHANNEL, "SMB Music",
            NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, NowPlayingActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String) = PendingIntent.getService(this, name.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, PLAYBACK_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(player.mediaMetadata.title ?: "SMB Music")
            .setContentText(status ?: if (recovering && !recoveryPaused)
                if (radio) "Reconnecting radio — session retained" else "Waiting for SMB — session retained" else "Paused — session retained")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_play, "Play", action(ACTION_PLAY))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Quit", action(ACTION_QUIT))
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1))
            .build()
        // The service is for SMB audio playback/recovery, not an unrelated connected device.
        return try {
            if (!serviceStarted) {
                ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java)
                    .setAction(ACTION_KEEP_ALIVE))
                serviceStarted = true
            }
            ServiceCompat.startForeground(this, PLAYBACK_NOTIFICATION_ID, notification,
                if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            true
        } catch (e: Exception) {
            PlaybackDiagnostics.record(this, "Playback foreground blocked: ${e.javaClass.simpleName}")
            setResumeStatus("Waiting for background playback eligibility…")
            false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) {
            releasePlayback(explicitQuit = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).cancel(PLAYBACK_NOTIFICATION_ID)
            stopSelf()
            return START_NOT_STICKY
        }
        if (released) {
            stopSelf(startId) // A queued Bluetooth wake after Quit must not leave a start pending.
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_KEEP_ALIVE) return START_STICKY
        if (intent?.action == ACTION_RECOVERY_WAKE) {
            serviceStarted = true
            updateStandbyNotification("Reconnecting playback")
            if (wanted && holdReason.isBlank() && !playbackIntent.explicitlyStopped) {
                if (radio) openLiveRadio()
                else if (recovering) requestImmediateRecoveryProbe()
                else if (bluetoothAvailable()) userPlay()
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_BLUETOOTH_CONNECTED) {
            serviceStarted = true
            if (player.isPlaying) {
                triggerNotificationUpdate()
                return START_NOT_STICKY
            }
            // Fulfil startForegroundService promptly, including a race with Stop/Quit.
            updateStandbyNotification()
            if (playbackIntent.explicitlyStopped || player.mediaItemCount == 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                checkBluetoothRoute()
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PLAY) { userPlay(); return START_STICKY }

        return super.onStartCommand(intent, flags, startId)
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Garmin Connect can see metadata/position through the default read-only access,
            // but its phone-music controls are a third-party controller. Grant that specific
            // package the standard player/session commands so Play/Pause, Previous/Next and
            // device-volume commands can reach the player. Trusted controllers such as Android
            // Auto keep Media3's normal default behavior.
            if (controller.packageName == GARMIN_CONNECT_PACKAGE) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                            .add(SessionCommand(COMMAND_RADIO_PLAY, Bundle.EMPTY))
                            .add(SessionCommand(COMMAND_RESUME_PLAYLIST, Bundle.EMPTY)).build()
                    )
                    .setAvailablePlayerCommands(
                        commandsForSource(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                    )
                    .build()
            }
            // Reproduce Media3's normal trusted/untrusted defaults for every other controller.
            val result = super.onConnect(session, controller)
            if (!result.isAccepted) return result
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(result.availableSessionCommands.buildUpon()
                    .add(SessionCommand(COMMAND_RADIO_PLAY, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_RESUME_PLAYLIST, Bundle.EMPTY)).build())
                .setAvailablePlayerCommands(commandsForSource(result.availablePlayerCommands)).build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
            command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (controller.packageName != packageName) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
            val ok = when (command.customAction) {
                COMMAND_RADIO_PLAY -> radioStore.stations().firstOrNull { it.id == args.getString("stationId") }
                    ?.let { playStation(it); true } ?: false
                COMMAND_RESUME_PLAYLIST -> restorePlaylist()
                else -> return super.onCustomCommand(session, controller, command, args)
            }
            return Futures.immediateFuture(SessionResult(if (ok) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_BAD_VALUE))
        }
        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, command: Int): Int {
            if (radio && command in RADIO_DISABLED_COMMANDS) return SessionResult.RESULT_ERROR_NOT_SUPPORTED
            return super.onPlayerCommandRequest(session, controller, command)
        }

        override fun onPlayerInteractionFinished(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            playerCommands: Player.Commands
        ) {
            if (released) return
            if (playerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                cancelPendingResume(); cancelRecovery()
                if (radio && player.currentMediaItem?.localConfiguration?.uri?.scheme == "smb") leaveRadioForNewPlaylist()
                playbackIntent.playbackRequested()
            }
            if (!radio && player.repeatMode != Player.REPEAT_MODE_ALL) player.repeatMode = Player.REPEAT_MODE_ALL

            saveStandalone()
            if (playbackIntent.explicitlyStopped) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                getSystemService(NotificationManager::class.java).cancel(PLAYBACK_NOTIFICATION_ID)
            }
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
        if (radio || sourceSwitching) return
        val mediaItem = player.currentMediaItem ?: return

        // A loader can report its error after Pause closed the source. That late result
        // must not restart probing or downloading until explicit Play/Bluetooth resume.
        if (recoveryPaused) return

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
        awaitingPlayback = false
        rebuildingBuffer = false
        recoveryPaused = false
        retryIndex = 0
        resumePositionMs = player.currentPosition.coerceAtLeast(0L)
        resumeMediaIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        resumeShouldPlay = shouldResume
        recoveryUrl = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            .ifBlank { mediaItem.mediaId }

        wanted = shouldResume
        wake.beginBurst()
        PlaybackDiagnostics.record(this, "SMB outage begin; ${wake.description()}")
        updateStandbyNotification()
        player.pause()
        saveStandalone()
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!recovering || recoveryPaused || recoveryUrl.isBlank()) return
        TailscaleConnector.request(this, "playback recovery")

        handler.removeCallbacksAndMessages(RETRY_TOKEN)
        handler.removeCallbacksAndMessages(STATUS_TOKEN)
        handler.removeCallbacksAndMessages(NETWORK_RETRY_TOKEN)

        val delay = retryScheduleMs[minOf(retryIndex, retryScheduleMs.lastIndex)]
        retryIndex++
        retryDueAtMs = SystemClock.elapsedRealtime() + delay
        wake.waiting(delay)
        val scheduledElapsed = SystemClock.elapsedRealtime()
        val scheduledUptime = SystemClock.uptimeMillis()
        publishRecoveryStatus(
            phase = RECOVERY_PHASE_WAITING,
            retryInMs = delay
        )
        scheduleRetryStatusTick()
        handler.postAtTime(
            {
                val sleep = (SystemClock.elapsedRealtime() - scheduledElapsed) - (SystemClock.uptimeMillis() - scheduledUptime)
                if (sleep > 1_000) PlaybackDiagnostics.record(this, "Recovery timer sleep delay=${sleep}ms")
                retrySmb()
            },
            RETRY_TOKEN,
            SystemClock.uptimeMillis() + delay
        )
    }

    private fun scheduleRetryStatusTick() {
        handler.postAtTime(
            {
                if (!recovering || recoveryPaused || rebuildingBuffer || probeInFlight) {
                    return@postAtTime
                }
                val remaining = (retryDueAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
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

        wake.work()
        val now = SystemClock.elapsedRealtime()
        if (now - recoveryLogAt >= 60_000) {
            PlaybackDiagnostics.record(this, "SMB probe; ${wake.description()}")
            recoveryLogAt = now
        }
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
        wake.work(60_000)
        PlaybackDiagnostics.record(this, "SMB reachable; refilling saved position")
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

        rebuildStartedAtMs = SystemClock.elapsedRealtime()
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

        val now = SystemClock.elapsedRealtime()
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
            wake.work(60_000)
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
        rebuildingBuffer = false
        awaitingPlayback = true
        clearRecoveryCallbacks()
        attemptRecoveryPlayback()
    }

    private fun attemptRecoveryPlayback() {
        if (!recovering || recoveryPaused || !wanted || holdReason.isNotBlank() || released) return
        if (playbackIntent.bluetoothConnected && !bluetoothAvailable()) { bluetoothChanged(); return }
        if (updateStandbyNotification("Resuming playback")) {
            skipNextUserFade = true
            requestRecoveryPlay()
        }
        if (awaitingPlayback) scheduleAwaitingPlayback()
    }

    private fun requestRecoveryPlay() {
        // Only a rejection of this request remains pending. A later genuine focus loss is a hold.
        requestingFocus = true
        try { player.play() } finally { requestingFocus = false }
    }

    private fun scheduleAwaitingPlayback() {
        handler.removeCallbacksAndMessages(AWAIT_TOKEN)
        wake.waiting(15_000)
        handler.postAtTime({ if (awaitingPlayback && !radio) attemptRecoveryPlayback() },
            AWAIT_TOKEN, SystemClock.uptimeMillis() + 5_000)
    }

    private fun finishRecovery() {
        wake.cancel()
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
        saveStandalone()
    }

    private fun restartRecoveryAfterRebuildFailure() {
        if (!recovering || recoveryPaused) return
        recoveryGeneration++
        rebuildingBuffer = false
        handler.removeCallbacksAndMessages(BUFFER_TOKEN)
        runCatching { player.stop() }
        scheduleRetry()
    }

    private fun pauseRecovery(reason: String = "user") {
        if (!recovering) return

        resumePositionMs = player.currentPosition.coerceAtLeast(resumePositionMs)
        resumeMediaIndex = player.currentMediaItemIndex.coerceAtLeast(resumeMediaIndex)
        if (reason == "user") { wanted = false; resumeShouldPlay = false }
        awaitingPlayback = false
        wake.cancel()
        PlaybackDiagnostics.record(this, "Recovery held: $reason")
        recoveryPaused = true
        rebuildingBuffer = false
        recoveryGeneration++
        clearRecoveryCallbacks()
        cancelActiveProbe()
        runCatching { player.stop() }
        publishRecoveryStatus(RECOVERY_PHASE_PAUSED)
        saveStandalone()
    }

    private fun cancelRecovery() {
        awaitingPlayback = false
        if (::wake.isInitialized) wake.cancel()
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
        handler.removeCallbacksAndMessages(AWAIT_TOKEN)
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
            override fun onAvailable(network: Network) {
                handler.post { requestImmediateRecoveryProbe() }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                handler.post { requestImmediateRecoveryProbe() }
            }
        }

        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    private fun requestImmediateRecoveryProbe() {
        if (released || !recoveryIntent.eligible(playbackIntent.explicitlyStopped)) return
        if (radio) {
            if (!player.isPlaying && (recovering || player.playerError != null || player.playbackState == Player.STATE_IDLE)) {
                handler.removeCallbacksAndMessages(RADIO_RETRY_TOKEN)
                val generation = radioGeneration
                handler.postAtTime({ if (generation == radioGeneration) openLiveRadio() }, RADIO_RETRY_TOKEN,
                    SystemClock.uptimeMillis() + NETWORK_EVENT_DEBOUNCE_MS)
            }
            return
        }
        if (awaitingPlayback) { attemptRecoveryPlayback(); return }
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
        if (released || !::session.isInitialized) return

        // Round rapidly changing values to whole seconds so a 250 ms buffer check does not flood
        // every controller with redundant Binder updates.
        val roundedRetry = if (retryInMs <= 0L) 0L else ((retryInMs + 999L) / 1000L) * 1000L
        val roundedBuffered = if (bufferedAheadMs <= 0L) 0L else (bufferedAheadMs / 1000L) * 1000L
        val roundedRequired = if (requiredBufferMs <= 0L) 0L else ((requiredBufferMs + 999L) / 1000L) * 1000L
        val signature = "$phase|$roundedRetry|$roundedBuffered|$roundedRequired"
        if (signature == lastPublishedRecoverySignature) return
        lastPublishedRecoverySignature = signature

        session.setSessionExtras(
            Bundle(session.sessionExtras).apply {
                putString(SESSION_EXTRA_RECOVERY_PHASE, phase)
                putLong(SESSION_EXTRA_RETRY_IN_MS, roundedRetry)
                putLong(SESSION_EXTRA_BUFFERED_AHEAD_MS, roundedBuffered)
                putLong(SESSION_EXTRA_REQUIRED_BUFFER_MS, roundedRequired)
            }
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (released) return
        // Keep an active playback or pending recovery session alive if the UI is swiped away.
        if (!isPlaybackOngoing() && !recovering && !keepStandaloneReady()) stopSelf()
    }

    /** Explicit Quit takes effect even while an Activity still holds its controller binding. */
    private fun releasePlayback(explicitQuit: Boolean = false) {
        if (released) return
        PlaybackDiagnostics.record(this, if (explicitQuit) "Quit; automatic resume disabled" else "Playback service destroyed")
        cancelPendingResume()
        if (explicitQuit) { wanted = false; playbackIntent.stop() }
        if (radio) { flushRadioListening(); history.finish(); radioStore.savePrevious(history.previous) }
        stopRadioWork()
        saveStandalone()
        released = true
        cancelRecovery()
        cancelFade(resetToFull = false)
        handler.removeCallbacksAndMessages(null)
        networkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        audio.unregisterAudioDeviceCallback(audioDevices)
        executor.shutdownNow()
        session.release()
        player.release()
    }

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }

    private fun snapshot(): StandaloneSnapshot {
        val order = mutableListOf<Int>()
        val timeline = player.currentTimeline
        if (!radio && player.shuffleModeEnabled && !timeline.isEmpty) {
            var index = timeline.getFirstWindowIndex(true)
            while (index != C.INDEX_UNSET && index !in order && order.size < player.mediaItemCount) {
                order += index
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
            }
        }
        return StandaloneSnapshot((0 until player.mediaItemCount).map { player.getMediaItemAt(it) },
            (if (recovering && !radio) resumeMediaIndex else player.currentMediaItemIndex).coerceAtLeast(0),
            (if (radio) 0L else if (recovering) resumePositionMs else player.currentPosition).coerceAtLeast(0),
            player.shuffleModeEnabled, playbackIntent.explicitlyStopped,
            wanted && holdReason.isBlank(), shuffleOrder = order,
            bluetoothRequired = playbackIntent.bluetoothConnected || holdReason == "output")
    }

    private fun userPlay() {
        if (released || player.mediaItemCount == 0) return
        recoveryIntent.play(); playbackIntent.playbackRequested(); cancelPendingResume()
        if (radio) openLiveRadio()
        else {
            TailscaleConnector.request(this, "media Play")
            if (recovering) {
                resumeShouldPlay = true; recoveryPaused = false
                if (awaitingPlayback) attemptRecoveryPlayback() else requestImmediateRecoveryProbe()
            } else if (updateStandbyNotification("Resuming playback")) {
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
                player.play()
            }
        }
        saveStandalone()
    }

    private fun userPause() {
        recoveryIntent.pause(); cancelPendingResume()
        if (radio) { stopRadioWork(); player.pause(); player.stop(); publishRadio() }
        else { player.pause(); if (recovering) pauseRecovery("user") }
        wake.cancel(); saveStandalone()
        PlaybackDiagnostics.record(this, "Explicit Pause; pending recovery cancelled")
    }

    private fun playStation(selected: RadioStation) {
        if (!radio && player.mediaItemCount > 0) savedPlaylist = snapshot()
        if (radio) { flushRadioListening(); history.finish(); radioStore.savePrevious(history.previous) }
        cancelRecovery(); cancelPendingResume(); stopRadioWork()
        sourceSwitching = true
        player.stop()
        radio = true; station = selected; wanted = true; holdReason = ""; radioRetry = 0
        playbackIntent.playbackRequested()
        player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_OFF
        player.setMediaItem(radioItem(selected))
        sourceSwitching = false
        refreshControllerCommands()
        saveStandalone()
        openLiveRadio()
    }

    private fun radioItem(station: RadioStation, mime: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId("radio:${station.id}").setUri(station.url).setMimeType(mime)
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(3_000).build())
        .setMediaMetadata(MediaMetadata.Builder().setDisplayTitle(station.name).build()).build()

    private fun openLiveRadio() {
        if (released || !radio || !wanted || holdReason.isNotBlank()) return
        val selected = station ?: return
        if (playbackIntent.bluetoothConnected && !bluetoothAvailable()) { bluetoothChanged(); return }
        handler.removeCallbacksAndMessages(RADIO_RETRY_TOKEN)
        val generation = ++radioGeneration
        sourceSwitching = true; player.stop(); sourceSwitching = false
        recovering = true; rebuildingBuffer = false; awaitingPlayback = false
        radioError = ""; radioSong = RadioSong(""); broadcastName = ""; radioQuality = ""
        history.update(null, selected.name, System.currentTimeMillis())
        radioLastProgress = SystemClock.elapsedRealtime(); radioLastTick = radioLastProgress; radioLastPosition = 0
        wake.work(45_000)
        if (!updateStandbyNotification("Connecting to radio")) {
            scheduleRadioRetry("Background playback unavailable"); return
        }
        publishRadio(); saveStandalone()
        radioResolveFuture?.cancel(true)
        radioResolveFuture = executor.submit {
            val result = runCatching { RadioStreamResolver.resolve(selected.url) }
            handler.post {
                if (released || !radio || !wanted || holdReason.isNotBlank() || generation != radioGeneration) return@post
                result.fold(onSuccess = { mime ->
                    sourceSwitching = true
                    player.setMediaItem(radioItem(selected, mime)); player.prepare()
                    sourceSwitching = false
                    awaitingPlayback = true
                    requestRecoveryPlay()
                    publishRadio()
                }, onFailure = { scheduleRadioRetry(if (it is IllegalArgumentException) "Unsupported station link" else "Station unavailable") })
            }
        }
    }

    private fun scheduleRadioRetry(error: String) {
        if (!radio || !wanted || holdReason.isNotBlank() || released) return
        radioResolveFuture?.cancel(true); radioResolveFuture = null
        radioGeneration++
        sourceSwitching = true; player.stop(); sourceSwitching = false
        recovering = true; awaitingPlayback = false; radioError = error.take(80)
        val delays = longArrayOf(1_000, 2_000, 5_000, 10_000, 15_000, 30_000, 60_000)
        val delay = delays[minOf(radioRetry++, delays.lastIndex)]
        val generation = radioGeneration
        handler.removeCallbacksAndMessages(RADIO_RETRY_TOKEN)
        handler.postAtTime({ if (generation == radioGeneration) openLiveRadio() }, RADIO_RETRY_TOKEN,
            SystemClock.uptimeMillis() + delay)
        wake.waiting(delay)
        radioLastProgress = SystemClock.elapsedRealtime() + delay
        publishRadio(); saveStandalone()
        if (radioRetry <= 2 || radioRetry % 5 == 0) PlaybackDiagnostics.record(this, "Radio retry: $error; delay=${delay}ms")
    }

    private fun stopRadioWork() {
        radioResolveFuture?.cancel(true); radioResolveFuture = null
        radioGeneration++
        handler.removeCallbacksAndMessages(RADIO_RETRY_TOKEN)
        if (::wake.isInitialized) wake.cancel()
        awaitingPlayback = false
        if (radio) { recovering = false; flushRadioListening(); history.finish(); radioStore.savePrevious(history.previous) }
    }

    private fun leaveRadioForNewPlaylist() {
        if (radio) stopRadioWork()
        radio = false; savedPlaylist = null; station = null; radioError = ""
        wanted = player.playWhenReady; holdReason = ""
        cancelRecovery()
        player.repeatMode = Player.REPEAT_MODE_ALL
        refreshControllerCommands(); publishRadio()
    }

    private fun restorePlaylist(): Boolean {
        val saved = savedPlaylist ?: return false
        if (!radio) return false
        stopRadioWork(); cancelRecovery(); cancelPendingResume()
        sourceSwitching = true; player.stop(); radio = false; station = null; savedPlaylist = null
        player.setMediaItems(saved.items, saved.index, saved.positionMs)
        player.shuffleModeEnabled = saved.shuffle
        if (saved.shuffleOrder.isNotEmpty()) player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(saved.shuffleOrder.toIntArray(), 0))
        player.repeatMode = Player.REPEAT_MODE_ALL
        recoveryIntent.afterPlaylistRestore(saved.shouldPlay)
        if (saved.bluetoothRequired && !bluetoothAvailable()) holdReason = "output"
        sourceSwitching = false
        playbackIntent.playbackRequested()
        refreshControllerCommands(); publishRadio(); saveStandalone()
        if (wanted && holdReason.isBlank()) userPlay()
        return true
    }

    private fun updateRadioMetadata(title: String, artist: String = "", album: String = "") {
        val parsed = RadioMetadata.parse(title, artist, album)
        if (parsed == radioSong) return
        radioSong = parsed
        history.update(parsed, station?.name.orEmpty(), System.currentTimeMillis())
        radioStore.savePrevious(history.previous)
        publishRadio()
    }

    private fun flushRadioListening() {
        station?.let { if (listeningMs > 0) radioStore.addListening(it.id, listeningMs) }
        listeningMs = 0
    }

    private fun scheduleRadioTick() {
        handler.postAtTime({
            if (released) return@postAtTime
            if (radio) {
                val now = SystemClock.elapsedRealtime()
                if (player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) radioLastProgress = now
                val freshStation = radioStore.stations().firstOrNull { it.id == station?.id }
                if (freshStation != null && freshStation.name != station?.name) { station = freshStation; publishRadio() }
                val position = player.currentPosition
                val advance = if (player.isPlaying) minOf((position - radioLastPosition).coerceAtLeast(0),
                    (now - radioLastTick).coerceIn(0, 5_000)) else 0L
                if (advance > 0) {
                    history.advance(advance); listeningMs += advance; radioLastProgress = now
                    if (listeningMs >= 30_000) flushRadioListening()
                }
                radioLastPosition = position; radioLastTick = now
                if (wanted && holdReason.isBlank() && player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE && now - radioLastProgress > 30_000) scheduleRadioRetry("Stream stopped advancing")
            }
            scheduleRadioTick()
        }, RADIO_TICK_TOKEN, SystemClock.uptimeMillis() + 1_000)
    }

    private fun commandsForSource(base: Player.Commands): Player.Commands = base.buildUpon().apply {
        if (radio) RADIO_DISABLED_COMMANDS.forEach { remove(it) }
    }.build()

    private fun refreshControllerCommands() {
        if (!::session.isInitialized) return
        session.connectedControllers.forEach { info ->
            val base = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            // Existing untrusted metadata-only controllers do not acquire control here.
            if (info.isTrusted || info.packageName == GARMIN_CONNECT_PACKAGE || info.packageName == packageName)
                session.setAvailableCommands(info, MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(COMMAND_RADIO_PLAY, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_RESUME_PLAYLIST, Bundle.EMPTY)).build(), commandsForSource(base))
        }
    }

    private fun publishRadio() {
        if (released || !::session.isInitialized) return
        val state = when {
            !wanted || holdReason.isNotBlank() -> "Paused"
            player.isPlaying -> "Live"
            radioError.isNotBlank() -> "Retrying — $radioError"
            else -> "Connecting"
        }
        session.setSessionExtras(Bundle(session.sessionExtras).apply {
            putBoolean(EXTRA_RADIO, radio); putBoolean("resumePlaylist", savedPlaylist != null)
            putBoolean("wanted", wanted && holdReason.isBlank())
            putString("radioStatus", state); putString("stationId", station?.id.orEmpty())
            putString("stationName", station?.name.orEmpty()); putString("radioTitle", radioSong.title)
            putString("radioArtist", radioSong.artist); putString("radioAlbum", radioSong.album)
            putString("broadcastName", broadcastName); putString("radioQuality", radioQuality)
            val last = history.previous
            putString("lastTitle", last?.song?.title.orEmpty()); putString("lastArtist", last?.song?.artist.orEmpty())
            putString("lastAlbum", last?.song?.album.orEmpty()); putString("lastStation", last?.station.orEmpty())
            putLong("lastTime", last?.startedAt ?: 0L)
        })
    }

    companion object {
        const val COMMAND_RADIO_PLAY = "com.smbmusic.player.RADIO_PLAY"
        const val COMMAND_RESUME_PLAYLIST = "com.smbmusic.player.RESUME_PLAYLIST"
        const val ACTION_RECOVERY_WAKE = "com.smbmusic.player.RECOVERY_WAKE"
        const val EXTRA_RADIO = "radio"
        private val AWAIT_TOKEN = Any()
        private val RADIO_RETRY_TOKEN = Any()
        private val RADIO_TICK_TOKEN = Any()
        private val RADIO_DISABLED_COMMANDS = setOf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
        const val ACTION_BLUETOOTH_CONNECTED = "com.smbmusic.player.BLUETOOTH_CONNECTED"
        private const val ACTION_KEEP_ALIVE = "com.smbmusic.player.KEEP_ALIVE"
        const val SESSION_EXTRA_RESUME_STATUS = "com.smbmusic.player.resume_status"
        private val RESUME_RETRY_TOKEN = Any()
        private val BLUETOOTH_ROUTE_TOKEN = Any()
        const val ACTION_QUIT = "com.smbmusic.player.QUIT"
        private const val ACTION_PLAY = "com.smbmusic.player.PLAY"
        private const val PLAYBACK_NOTIFICATION_ID = 2401
        private const val PLAYBACK_CHANNEL = "smb_playback"
        private val BLUETOOTH_OUTPUT_TYPES = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID)
        private val PERSIST_TOKEN = Any()
        private val RETRY_TOKEN = Any()
        private val BUFFER_TOKEN = Any()
        private val STATUS_TOKEN = Any()
        private val PROBE_TIMEOUT_TOKEN = Any()
        private val NETWORK_RETRY_TOKEN = Any()
        private val FADE_TOKEN = Any()

        private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"

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
