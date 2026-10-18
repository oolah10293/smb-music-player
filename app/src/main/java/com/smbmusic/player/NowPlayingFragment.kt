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
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.smbmusic.player.media.AudioFormats

@UnstableApi
class NowPlayingFragment : SmbPageFragment(R.layout.activity_now_playing, 1) {
    private lateinit var playerView: PlayerView
    private lateinit var controlsPlayerView: PlayerView
    private lateinit var titleText: TextView
    private lateinit var albumArtistText: TextView
    private lateinit var albumText: TextView
    private lateinit var playbackStatus: TextView
    private lateinit var queueSortButton: Button

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var queueSortMode = SortMode.NAME_ASC
    private var recoveryStatus = RecoveryStatus.idle()
    private var resumeStatus = ""

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
            if (isFinishing || isDestroyed) return
            resumeStatus = extras.getString(PlaybackService.SESSION_EXTRA_RESUME_STATUS).orEmpty()
            recoveryStatus = RecoveryStatus.from(extras)
            updateStatus()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = findViewById(R.id.playerView)
        controlsPlayerView = findViewById(R.id.controlsPlayerView)
        titleText = findViewById(R.id.nowPlayingText)
        albumArtistText = findViewById(R.id.albumArtistText)
        albumText = findViewById(R.id.albumText)
        playbackStatus = findViewById(R.id.playbackStatus)
        playbackStatus.setOnLongClickListener { PlaybackDiagnostics.show(pageContext); true }
        queueSortButton = findViewById(R.id.queueSortButton)

        findViewById<Button>(R.id.quitButton).setOnClickListener {
            quitCleanly()
        }

        queueSortMode = SortModeStore.load(pageContext)
        updateSortButton()
        findViewById<Button>(R.id.radioPlayPause).setOnClickListener {
            controller?.let { if (it.sessionExtras.getBoolean("wanted")) it.pause() else it.play() }
        }
        queueSortButton.setOnClickListener {
            if (controller?.sessionExtras?.getBoolean(PlaybackService.EXTRA_RADIO) == true) {
                controller?.sendCustomCommand(androidx.media3.session.SessionCommand(PlaybackService.COMMAND_RESUME_PLAYLIST, Bundle.EMPTY), Bundle.EMPTY)
                return@setOnClickListener
            }
            val requestedMode = queueSortMode.next()
            if (sortCurrentQueue(requestedMode)) {
                queueSortMode = requestedMode
                SortModeStore.save(pageContext, requestedMode)
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
        val sharedMode = SortModeStore.load(pageContext)
        if (sharedMode != queueSortMode) {
            queueSortMode = sharedMode
            if (::queueSortButton.isInitialized) updateSortButton()
        }
    }

    private fun connectController() {
        val token = SessionToken(pageContext, ComponentName(pageContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(pageContext, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture.addListener(
            {
                if (isFinishing || isDestroyed) return@addListener
                try {
                    val mediaController = controllerFuture.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)

                    // Artwork and controls intentionally share the same MediaController.
                    // The first PlayerView is artwork-only; the second uses PlayerView's
                    // own Media3 controller UI, just like v0.2.
                    playerView.player = mediaController
                    controlsPlayerView.player = mediaController
                    controlsPlayerView.showController()

                    resumeStatus = mediaController.sessionExtras.getString(PlaybackService.SESSION_EXTRA_RESUME_STATUS).orEmpty()
                    recoveryStatus = RecoveryStatus.from(mediaController.sessionExtras)
                    updateMetadata(mediaController.mediaMetadata)
                    queueSortMode = SortModeStore.load(pageContext)
                    updateSortButton()
                    updateStatus()
                } catch (e: Exception) {
                    playbackStatus.text = "Playback service failed: ${friendlyError(e)}"
                }
            },
            ContextCompat.getMainExecutor(pageContext)
        )
    }

    private fun sortCurrentQueue(mode: SortMode): Boolean {
        val mediaController = controller ?: return false
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
        val extras = controller?.sessionExtras
        val radio = extras?.getBoolean(PlaybackService.EXTRA_RADIO) == true
        queueSortButton.text = if (radio) "Resume Playlist" else queueSortMode.label
        queueSortButton.isEnabled = !radio || extras?.getBoolean("resumePlaylist") == true
    }

    private fun quitCleanly() {
        // Service-owned Quit closes the reader and saves explicit stopped intent before
        // both Activities leave; ordinary Back/Browse keeps playback running.
        playerView.player = null
        controlsPlayerView.player = null
        pageContext.startService(Intent(pageContext, PlaybackService::class.java).setAction(PlaybackService.ACTION_QUIT))
        requireActivity().finishAffinity()
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        if (controller?.sessionExtras?.getBoolean(PlaybackService.EXTRA_RADIO) == true) return
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
        val radio = mediaController.sessionExtras.getBoolean(PlaybackService.EXTRA_RADIO)
        findViewById<View>(R.id.fileMetadata).visibility = if (radio) View.GONE else View.VISIBLE
        playerView.visibility = if (radio) View.GONE else View.VISIBLE
        controlsPlayerView.visibility = if (radio) View.GONE else View.VISIBLE
        findViewById<View>(R.id.radioDetailsScroll).visibility = if (radio) View.VISIBLE else View.GONE
        findViewById<View>(R.id.radioTransport).visibility = if (radio) View.VISIBLE else View.GONE
        updateSortButton()
        if (radio) {
            RadioNowPlayingView.bind(requireView(), mediaController.sessionExtras)
            playbackStatus.text = "LIVE · " + mediaController.sessionExtras.getString("radioStatus", "Connecting")
            findViewById<Button>(R.id.radioPlayPause).text = if (mediaController.sessionExtras.getBoolean("wanted")) "Pause" else "Play"
            return
        }
        updateMetadata(mediaController.mediaMetadata)
        playbackStatus.text = if (resumeStatus.isNotBlank() && recoveryStatus.phase == PlaybackService.RECOVERY_PHASE_IDLE) resumeStatus else when (recoveryStatus.phase) {
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
                !mediaController.playWhenReady && mediaController.playbackState == Player.STATE_IDLE -> "Paused"
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

    override fun onDestroyView() {
        playerView.player = null
        controlsPlayerView.player = null
        controller?.removeListener(playerListener)
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        controller = null
        super.onDestroyView()
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
