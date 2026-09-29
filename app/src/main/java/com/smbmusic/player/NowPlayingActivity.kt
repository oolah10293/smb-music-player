package com.smbmusic.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.common.util.RepeatModeUtil
import com.smbmusic.player.house.HouseRuntime
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.smbmusic.player.media.AudioFormats

@UnstableApi
class NowPlayingActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var controlsPlayerView: PlayerView
    private lateinit var titleText: TextView
    private lateinit var albumArtistText: TextView
    private lateinit var albumText: TextView
    private lateinit var playbackStatus: TextView
    private lateinit var queueSortButton: Button
    private lateinit var muteOutputButton: Button
    private val isHouse: Boolean get() = controller?.sessionExtras?.getBoolean(HouseRuntime.EXTRA_HOUSE) == true

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var queueSortMode = SortMode.NAME_ASC
    private var recoveryStatus = RecoveryStatus.idle()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateMetadata(controller?.mediaMetadata ?: mediaItem?.mediaMetadata)
            updateStatus()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateMetadata(mediaMetadata)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateStatus()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateStatus()
        }

        override fun onPlayerError(error: PlaybackException) {
            updateStatus()
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            recoveryStatus = RecoveryStatus.from(extras)
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)
        applySystemBarInsets()

        playerView = findViewById(R.id.playerView)
        controlsPlayerView = findViewById(R.id.controlsPlayerView)
        titleText = findViewById(R.id.nowPlayingText)
        albumArtistText = findViewById(R.id.albumArtistText)
        albumText = findViewById(R.id.albumText)
        playbackStatus = findViewById(R.id.playbackStatus)
        queueSortButton = findViewById(R.id.queueSortButton)
        muteOutputButton = findViewById(R.id.muteOutputButton)
        muteOutputButton.setOnClickListener {
            controller?.sendCustomCommand(SessionCommand(HouseRuntime.MUTE, Bundle.EMPTY), Bundle.EMPTY)
        }

        findViewById<Button>(R.id.browseButton).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.quitButton).setOnClickListener {
            quitCleanly()
        }

        queueSortMode = SortModeStore.load(this)
        updateSortButton()
        queueSortButton.setOnClickListener {
            val requestedMode = queueSortMode.next()
            if (sortCurrentQueue(requestedMode)) {
                queueSortMode = requestedMode
                SortModeStore.save(this, requestedMode)
                updateSortButton()
            }
        }

        // v0.2 used PlayerView's built-in controller. Keep that exact mechanism, but
        // on a separate PlayerView positioned below the artwork. Do not let the user
        // accidentally hide it and do not let it time out.
        controlsPlayerView.setControllerShowTimeoutMs(0)
        controlsPlayerView.setControllerHideOnTouch(false)
        controlsPlayerView.setShowRewindButton(false)
        controlsPlayerView.setShowFastForwardButton(false)
        controlsPlayerView.setShowPreviousButton(true)
        controlsPlayerView.setShowNextButton(true)
        controlsPlayerView.setShowShuffleButton(true)
        controlsPlayerView.showController()

        connectController()
    }

    override fun onResume() {
        super.onResume()
        val sharedMode = SortModeStore.load(this)
        if (sharedMode != queueSortMode) {
            queueSortMode = sharedMode
            if (::queueSortButton.isInitialized) updateSortButton()
        }
    }

    private fun applySystemBarInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(R.id.nowPlayingRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture.addListener(
            {
                try {
                    val mediaController = controllerFuture.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    if (!isHouse) mediaController.repeatMode = Player.REPEAT_MODE_ALL
                    controlsPlayerView.setRepeatToggleModes(if (isHouse) RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL else RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE)

                    // Artwork and controls intentionally share the same MediaController.
                    // The first PlayerView is artwork-only; the second uses PlayerView's
                    // own Media3 controller UI, just like v0.2.
                    playerView.player = mediaController
                    controlsPlayerView.player = mediaController
                    controlsPlayerView.showController()

                    recoveryStatus = RecoveryStatus.from(mediaController.sessionExtras)
                    updateMetadata(mediaController.mediaMetadata)
                    queueSortMode = SortModeStore.load(this)
                    updateSortButton()
                    updateStatus()
                } catch (e: Exception) {
                    playbackStatus.text = "Playback service failed: ${friendlyError(e)}"
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun sortCurrentQueue(mode: SortMode): Boolean {
        val mediaController = controller ?: return false
        if (isHouse) {
            val future = mediaController.sendCustomCommand(SessionCommand(HouseRuntime.SORT, Bundle.EMPTY), Bundle().apply { putString("mode", mode.name) })
            future.addListener({
                if (runCatching { future.get().resultCode == 0 }.getOrDefault(false)) {
                    queueSortMode = mode
                    SortModeStore.save(this, mode)
                    updateSortButton()
                } else playbackStatus.text = "HOUSE sort failed — refresh before trying again."
            }, ContextCompat.getMainExecutor(this))
            return false // Commit the shared sort setting only after acknowledgement.
        }
        val itemCount = mediaController.mediaItemCount
        mediaController.repeatMode = Player.REPEAT_MODE_ALL
        if (itemCount < 2) return true

        // Build the desired order in memory, then replace the queue once. The currently
        // playing song is rotated to queue item zero and the remainder wraps from there.
        val currentItem = mediaController.currentMediaItem ?: return false
        val currentMediaId = currentItem.mediaId
        val currentPositionMs = mediaController.currentPosition.coerceAtLeast(0L)
        val shouldPlay = mediaController.playWhenReady
        val shuffleEnabled = mediaController.shuffleModeEnabled

        val entries = (0 until itemCount).map { index ->
            QueueEntry.from(mediaController.getMediaItemAt(index))
        }

        val sorted = when (mode) {
            SortMode.NAME_ASC -> entries.sortedBy { it.sortName }
            SortMode.NAME_DESC -> entries.sortedByDescending { it.sortName }
            SortMode.DATE_DESC -> entries.sortedWith(
                compareByDescending<QueueEntry> { it.modified }.thenBy { it.sortName }
            )
            SortMode.DATE_ASC -> entries.sortedWith(
                compareBy<QueueEntry> { it.modified }.thenBy { it.sortName }
            )
        }

        val currentSortedIndex = sorted.indexOfFirst { it.mediaItem.mediaId == currentMediaId }
        if (currentSortedIndex < 0) return false

        val rotatedItems = rotateFrom(sorted, currentSortedIndex).map { it.mediaItem }
        mediaController.setMediaItems(rotatedItems, 0, currentPositionMs)
        mediaController.repeatMode = Player.REPEAT_MODE_ALL
        mediaController.shuffleModeEnabled = shuffleEnabled
        mediaController.prepare()
        if (shouldPlay) mediaController.play() else mediaController.pause()
        return true
    }

    private fun <T> rotateFrom(items: List<T>, startIndex: Int): List<T> {
        if (items.isEmpty() || startIndex <= 0) return items
        return items.drop(startIndex) + items.take(startIndex)
    }

    private fun updateSortButton() {
        queueSortButton.text = queueSortMode.label
    }

    private fun quitCleanly() {
        val mediaController = controller
        if (!isHouse) {
            mediaController?.stop()
            mediaController?.clearMediaItems()
        }
        playerView.player = null
        controlsPlayerView.player = null

        // Explicitly stop the foreground playback service. finishAffinity closes both
        // Browser and Now Playing so Quit means quit, while ordinary Back/Browse does not.
        stopService(Intent(this, PlaybackService::class.java))
        finishAffinity()
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        val itemMetadata = controller?.currentMediaItem?.mediaMetadata
        val filename = itemMetadata?.extras?.getString(MainActivity.EXTRA_FILENAME).orEmpty()

        // Player.mediaMetadata contains extractor metadata (ID3/MP4/etc.). The queue item itself
        // deliberately has no filename injected into its title field, so a real embedded title
        // wins. Filename is the final fallback only.
        val effectiveMetadata = metadata ?: controller?.mediaMetadata
        val title = clean(effectiveMetadata?.title)
            .ifBlank { clean(itemMetadata?.title) }
            .ifBlank { filename.takeIf { it.isNotBlank() }?.let(AudioFormats::titleFromFilename).orEmpty() }
            .ifBlank { "Nothing playing" }

        val albumArtist = clean(effectiveMetadata?.albumArtist)
            .ifBlank { clean(effectiveMetadata?.artist) }
            .ifBlank { clean(itemMetadata?.albumArtist) }
            .ifBlank { clean(itemMetadata?.artist) }

        val album = clean(effectiveMetadata?.albumTitle)
            .ifBlank { clean(itemMetadata?.albumTitle) }

        titleText.text = title
        setOptionalText(albumArtistText, albumArtist)
        setOptionalText(albumText, album)
    }

    private fun clean(value: CharSequence?): String = value?.toString()?.trim().orEmpty()

    private fun setOptionalText(view: TextView, value: String) {
        if (value.isBlank()) {
            view.text = ""
            view.visibility = View.GONE
        } else {
            view.text = value
            view.visibility = View.VISIBLE
        }
    }

    private fun updateStatus() {
        val mediaController = controller ?: return
        muteOutputButton.visibility = if (isHouse) View.VISIBLE else View.GONE
        if (isHouse) {
            muteOutputButton.text = if (mediaController.sessionExtras.getBoolean(HouseRuntime.EXTRA_MUTED, true)) "Unmute Output" else "Mute Output"
            playbackStatus.text = mediaController.sessionExtras.getString(HouseRuntime.EXTRA_STATUS, "HOUSE — connecting")
            return
        }
        playbackStatus.text = when (recoveryStatus.phase) {
            PlaybackService.RECOVERY_PHASE_WAITING -> {
                val seconds = ((recoveryStatus.retryInMs + 999L) / 1000L).coerceAtLeast(0L)
                if (seconds > 0L) "Waiting for SMB — retry in ${seconds}s" else "Waiting for SMB…"
            }
            PlaybackService.RECOVERY_PHASE_PROBING -> "Checking SMB…"
            PlaybackService.RECOVERY_PHASE_REBUILDING -> {
                val haveSeconds = recoveryStatus.bufferedAheadMs.coerceAtLeast(0L) / 1000L
                val needSeconds = recoveryStatus.requiredBufferMs.coerceAtLeast(0L) / 1000L
                if (haveSeconds <= 0L) {
                    "SMB restored — rebuilding buffer…"
                } else {
                    "Rebuilding buffer — $haveSeconds / ${needSeconds}s"
                }
            }
            PlaybackService.RECOVERY_PHASE_PAUSED -> "Paused"
            else -> when {
                mediaController.playerError != null -> "Reconnecting to SMB…"
                mediaController.playbackState == Player.STATE_BUFFERING -> "Buffering from SMB…"
                mediaController.playbackState == Player.STATE_READY && mediaController.isPlaying -> "Playing"
                mediaController.playbackState == Player.STATE_READY -> "Paused"
                mediaController.playbackState == Player.STATE_ENDED -> "Playlist finished"
                mediaController.mediaItemCount == 0 -> "Nothing queued"
                else -> "Opening SMB stream…"
            }
        }
    }

    private fun friendlyError(t: Throwable): String {
        var cur: Throwable? = t
        var last = t.message ?: t.javaClass.simpleName
        while (cur != null) {
            if (!cur.message.isNullOrBlank()) last = cur.message!!
            cur = cur.cause
        }
        return last.take(180)
    }

    override fun onDestroy() {
        playerView.player = null
        controlsPlayerView.player = null
        controller?.removeListener(playerListener)
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        controller = null
        super.onDestroy()
    }

    private data class RecoveryStatus(
        val phase: String,
        val retryInMs: Long,
        val bufferedAheadMs: Long,
        val requiredBufferMs: Long
    ) {
        companion object {
            fun idle(): RecoveryStatus = RecoveryStatus(
                phase = PlaybackService.RECOVERY_PHASE_IDLE,
                retryInMs = 0L,
                bufferedAheadMs = 0L,
                requiredBufferMs = 0L
            )

            fun from(extras: Bundle): RecoveryStatus = RecoveryStatus(
                phase = extras.getString(
                    PlaybackService.SESSION_EXTRA_RECOVERY_PHASE,
                    PlaybackService.RECOVERY_PHASE_IDLE
                ),
                retryInMs = extras.getLong(PlaybackService.SESSION_EXTRA_RETRY_IN_MS, 0L),
                bufferedAheadMs = extras.getLong(
                    PlaybackService.SESSION_EXTRA_BUFFERED_AHEAD_MS,
                    0L
                ),
                requiredBufferMs = extras.getLong(
                    PlaybackService.SESSION_EXTRA_REQUIRED_BUFFER_MS,
                    0L
                )
            )
        }
    }

    private data class QueueEntry(
        val mediaItem: MediaItem,
        val sortName: String,
        val modified: Long
    ) {
        companion object {
            fun from(item: MediaItem): QueueEntry {
                val extras = item.mediaMetadata.extras
                val filename = extras?.getString(MainActivity.EXTRA_FILENAME).orEmpty()
                val name = filename.ifBlank {
                    item.mediaMetadata.title?.toString().orEmpty().ifBlank { item.mediaId }
                }.lowercase()
                return QueueEntry(
                    mediaItem = item,
                    sortName = name,
                    modified = extras?.getLong(MainActivity.EXTRA_MODIFIED, 0L) ?: 0L
                )
            }
        }
    }
}
