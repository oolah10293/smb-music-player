package com.smbmusic.player

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import com.smbmusic.player.smb.SmbClient
import com.smbmusic.player.smb.SmbDataSource
import com.smbmusic.player.storage.CredentialStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var smb: SmbClient
    private lateinit var executor: ExecutorService

    private val handler = Handler(Looper.getMainLooper())
    private var recovering = false
    private var rebuildingBuffer = false
    private var retryIndex = 0
    private var resumePositionMs = 0L
    private var resumeMediaIndex = 0
    private var resumeShouldPlay = true
    private var recoveryUrl = ""
    private val retryScheduleMs = longArrayOf(1_000, 2_000, 5_000, 10_000, 15_000)

    override fun onCreate() {
        super.onCreate()

        smb = SmbClient(CredentialStore(this))
        executor = Executors.newSingleThreadExecutor()

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

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            // The original test device is an older phone and v0.1 paused when the screen slept.
            // NETWORK wake mode holds both CPU and Wi-Fi locks while actively playing/buffering.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                beginOutageRecovery(error)
            }
        })

        session = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {}
        ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    private fun beginOutageRecovery(error: PlaybackException) {
        val mediaItem = player.currentMediaItem ?: return

        // If a second error happens while rebuilding the recovery buffer, retain the
        // original intent to resume rather than treating our forced pause as a user pause.
        val shouldResume = if (recovering) resumeShouldPlay else player.playWhenReady

        recovering = true
        rebuildingBuffer = false
        retryIndex = 0
        resumePositionMs = player.currentPosition.coerceAtLeast(0L)
        resumeMediaIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        resumeShouldPlay = shouldResume
        recoveryUrl = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            .ifBlank { mediaItem.mediaId }

        player.pause()
        handler.removeCallbacksAndMessages(RETRY_TOKEN)
        handler.removeCallbacksAndMessages(BUFFER_TOKEN)
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!recovering || recoveryUrl.isBlank()) return
        val delay = retryScheduleMs[minOf(retryIndex, retryScheduleMs.lastIndex)]
        retryIndex++
        handler.postAtTime(
            { retrySmb() },
            RETRY_TOKEN,
            SystemClock.uptimeMillis() + delay
        )
    }

    private fun retrySmb() {
        val url = recoveryUrl
        executor.execute {
            val ok = smb.probeFile(url)
            handler.post {
                if (!recovering || recoveryUrl != url) return@post
                if (ok) {
                    beginRecoveryBufferRebuild()
                } else {
                    scheduleRetry()
                }
            }
        }
    }

    private fun beginRecoveryBufferRebuild() {
        rebuildingBuffer = true
        handler.removeCallbacksAndMessages(RETRY_TOKEN)
        handler.removeCallbacksAndMessages(BUFFER_TOKEN)

        // Do not immediately blast a few hundred milliseconds of audio and stall again.
        // Hold playback while ExoPlayer banks a real safety margin first.
        player.playWhenReady = false
        player.seekTo(resumeMediaIndex, resumePositionMs)
        player.prepare()
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
        if (!recovering || !rebuildingBuffer) return
        if (player.playerError != null) {
            // prepare() normally clears the previous error immediately, but do not let a
            // stale error value strand the recovery state machine with no future check.
            scheduleRecoveryBufferCheck()
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

        if (bufferedAhead >= required || bufferedToEnd || player.playbackState == Player.STATE_ENDED) {
            recovering = false
            rebuildingBuffer = false
            retryIndex = 0
            recoveryUrl = ""
            if (resumeShouldPlay) player.play()
        } else {
            scheduleRecoveryBufferCheck()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep an active playback session alive if the UI is swiped away.
        // MediaLibraryService owns the foreground-service lifetime while playback is ongoing.
        if (!isPlaybackOngoing()) stopSelf()
    }

    override fun onDestroy() {
        recovering = false
        rebuildingBuffer = false
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        session.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        private val RETRY_TOKEN = Any()
        private val BUFFER_TOKEN = Any()

        // Large streaming buffer for unreliable cellular/Tailscale paths.
        private const val MIN_BUFFER_MS = 120_000
        private const val MAX_BUFFER_MS = 600_000
        private const val TARGET_BUFFER_BYTES = 32 * 1024 * 1024
        private const val START_PLAYBACK_BUFFER_MS = 3_000
        private const val RECOVERY_RESUME_BUFFER_MS = 20_000
        private const val BUFFER_CHECK_INTERVAL_MS = 250L
        private const val END_BUFFER_SLOP_MS = 500L
    }
}
