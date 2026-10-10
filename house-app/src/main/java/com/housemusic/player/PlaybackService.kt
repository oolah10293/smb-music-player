package com.housemusic.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.housemusic.player.house.HouseConnection
import com.housemusic.player.house.HouseRuntime

/** One HOUSE controller/renderer lifetime, independent of any other music app. */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var house: HouseRuntime? = null
    private lateinit var session: MediaLibrarySession
    private var released = false
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val runtime = createRuntime()
        house = runtime
        session = MediaLibrarySession.Builder(this, runtime.player, SessionCallback())
            .setSessionActivity(openPlayer())
            .build()
        // The controller stays available during silence and network outages. Enter the
        // foreground before start() can request audio focus for preconnected Bluetooth.
        publishState()
        startRuntime(runtime)
    }

    private fun startRuntime(runtime: HouseRuntime) {
        // A cold start from a stale Quit notification must close before any attach work.
        main.post { if (!released && house === runtime) runtime.start() }
    }

    private fun createRuntime(): HouseRuntime = HouseRuntime(
        this, HouseConnection.configuredEndpoint(this)
    ) { publishState() }

    private fun publishState() {
        if (released || !this::session.isInitialized) return
        val runtime = house ?: return
        session.setSessionExtras(runtime.extras())
        updateHouseNotification()
    }

    /** Settings change replaces the HOUSE target without creating another media session. */
    private fun reconnect() {
        if (released) return
        val previous = house
        house = null
        previous?.close()
        val runtime = createRuntime()
        house = runtime
        session.setPlayer(runtime.player)
        previous?.player?.release()
        publishState()
        startRuntime(runtime)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        if (released || !this::session.isInitialized) null else session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (!released && this::session.isInitialized) updateHouseNotification()
    }

    private fun openPlayer(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, NowPlayingActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notificationAction(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun updateHouseNotification() {
        if (released || !this::session.isInitialized) return
        val runtime = house ?: return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "House Music", NotificationManager.IMPORTANCE_LOW)
        )
        val radio = runtime.state.isRadio
        val playing = if (radio) runtime.state.radioPlayIntent == "play" &&
            runtime.state.radioStatus in setOf("playing", "connecting", "retrying")
            else runtime.state.transport == "play"
        val outputStatus = runtime.extras().getString(HouseRuntime.EXTRA_STATUS)
        val liveStatus = when (runtime.state.radioStatus) {
            "paused" -> "Paused"
            "connecting" -> "Connecting"
            "retrying" -> "Reconnecting"
            "stopped" -> "Stopped"
            "error" -> "Station unavailable"
            else -> "Playing"
        }
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(runtime.player.mediaMetadata.title ?: "House Music")
            .setContentText(if (radio) "LIVE · $liveStatus · $outputStatus" else outputStatus)
            .setContentIntent(openPlayer())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (!radio) builder.addAction(android.R.drawable.ic_media_previous, "Previous", notificationAction(ACTION_PREVIOUS))
        builder.addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play", notificationAction(ACTION_PLAY_PAUSE))
        if (!radio) builder.addAction(android.R.drawable.ic_media_next, "Next", notificationAction(ACTION_NEXT))
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Quit", notificationAction(ACTION_QUIT))
        val compact = if (radio) intArrayOf(0, 1) else intArrayOf(0, 1, 2)
        val notification = builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session)
            .setShowActionsInCompactView(*compact)).build()
        // Connected-device lifetime also covers a muted phone that controls the rooms.
        // CHANGE_NETWORK_STATE supplies the connected-device foreground prerequisite.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) {
            releasePlayback()
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            stopSelf()
            return START_NOT_STICKY
        }
        if (released) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_RECONNECT -> reconnect()
            ACTION_PLAY_PAUSE -> house?.takeIf { it.canControl }?.let { runtime ->
                if (runtime.player.playWhenReady) runtime.player.pause() else runtime.player.play()
            }
            ACTION_NEXT -> house?.takeIf { it.canControl && it.state.canSkip }?.player?.seekToNextMediaItem()
            ACTION_PREVIOUS -> house?.takeIf { it.canControl && it.state.canSkip }?.player?.seekToPreviousMediaItem()
            // MediaLibraryService handles standard media-button/headset intents.
            else -> super.onStartCommand(intent, flags, startId)
        }
        return START_NOT_STICKY
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            if (controller.packageName == packageName) {
                HouseRuntime.CUSTOM_COMMANDS.forEach { commands.add(SessionCommand(it, Bundle.EMPTY)) }
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
            if (released) Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
            else house?.custom(customCommand, args)
                ?: Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Closing the UI does not detach a muted/background controller or stop room audio.
        // The explicit Quit action ends this phone's participation.
    }

    /** Quit releases immediately, including while an Activity still binds this service. */
    private fun releasePlayback() {
        if (released) return
        released = true
        main.removeCallbacksAndMessages(null)
        val runtime = house
        house = null
        runtime?.close() // Detach this phone only; the server retains authority over other rooms.
        if (this::session.isInitialized) session.release()
        runtime?.player?.release()
    }

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }

    companion object {
        const val ACTION_QUIT = "com.housemusic.player.QUIT"
        const val ACTION_RECONNECT = "com.housemusic.player.RECONNECT"
        private const val ACTION_PLAY_PAUSE = "com.housemusic.player.notification.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.housemusic.player.notification.NEXT"
        private const val ACTION_PREVIOUS = "com.housemusic.player.notification.PREVIOUS"
        private const val NOTIFICATION_CHANNEL = "house"
        private const val NOTIFICATION_ID = 2401
        private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
    }
}
