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
class NowPlayingActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var controlsPlayerView: PlayerView
    private lateinit var titleText: TextView
    private lateinit var albumArtistText: TextView
    private lateinit var albumText: TextView
    private lateinit var playbackStatus: TextView
    private lateinit var queueSortButton: Button

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var queueSortMode = QueueSortMode.NAME_ASC

    private val listener = object : Player.Listener {
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
            val position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            playbackStatus.text = "SMB lost at ${formatTime(position)} — reconnecting…"
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

        findViewById<Button>(R.id.browseButton).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.quitButton).setOnClickListener {
            quitCleanly()
        }

        queueSortButton.setOnClickListener {
            val requestedMode = queueSortMode.next()
            if (sortCurrentQueue(requestedMode)) {
                queueSortMode = requestedMode
                saveQueueSortMode(requestedMode)
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
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    val mediaController = controllerFuture.get()
                    controller = mediaController
                    mediaController.addListener(listener)

                    // Artwork and controls intentionally share the same MediaController.
                    // The first PlayerView is artwork-only; the second uses PlayerView's
                    // own Media3 controller UI, just like v0.2.
                    playerView.player = mediaController
                    controlsPlayerView.player = mediaController
                    controlsPlayerView.showController()

                    updateMetadata(mediaController.mediaMetadata)
                    queueSortMode = loadQueueSortMode()
                    updateSortButton()
                    updateStatus()
                } catch (e: Exception) {
                    playbackStatus.text = "Playback service failed: ${friendlyError(e)}"
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun sortCurrentQueue(mode: QueueSortMode): Boolean {
        val mediaController = controller ?: return false
        val itemCount = mediaController.mediaItemCount
        if (itemCount < 2) return true

        // v0.3.2 moved media items one at a time through MediaController. On large folders
        // that meant hundreds of session commands and could ANR the UI. Build the desired
        // order in memory, then replace the queue once using the same setMediaItems API that
        // MainActivity already uses to start folder playback.
        val currentItem = mediaController.currentMediaItem ?: return false
        val currentMediaId = currentItem.mediaId
        val currentPositionMs = mediaController.currentPosition.coerceAtLeast(0L)
        val shouldPlay = mediaController.playWhenReady

        val entries = (0 until itemCount).map { index ->
            QueueEntry.from(mediaController.getMediaItemAt(index))
        }

        val desired = when (mode) {
            QueueSortMode.NAME_ASC -> entries.sortedBy { it.sortName }
            QueueSortMode.NAME_DESC -> entries.sortedByDescending { it.sortName }
            QueueSortMode.DATE_DESC -> entries.sortedWith(
                compareByDescending<QueueEntry> { it.modified }.thenBy { it.sortName }
            )
            QueueSortMode.DATE_ASC -> entries.sortedWith(
                compareBy<QueueEntry> { it.modified }.thenBy { it.sortName }
            )
        }

        val newIndex = desired.indexOfFirst { it.mediaItem.mediaId == currentMediaId }
        if (newIndex < 0) return false

        val sortedItems = desired.map { it.mediaItem }
        mediaController.setMediaItems(sortedItems, newIndex, currentPositionMs)
        mediaController.prepare()
        if (shouldPlay) mediaController.play() else mediaController.pause()
        return true
    }

    private fun loadQueueSortMode(): QueueSortMode {
        val stored = getSharedPreferences(MainActivity.PREFS_UI, MODE_PRIVATE)
            .getString(MainActivity.PREF_QUEUE_SORT, null)
        return QueueSortMode.fromStorage(stored)
    }

    private fun saveQueueSortMode(mode: QueueSortMode) {
        getSharedPreferences(MainActivity.PREFS_UI, MODE_PRIVATE)
            .edit()
            .putString(MainActivity.PREF_QUEUE_SORT, mode.storageName)
            .apply()
    }

    private fun updateSortButton() {
        // The button displays only the active order, matching the Browser screen.
        queueSortButton.text = queueSortMode.label
    }

    private fun quitCleanly() {
        val mediaController = controller
        mediaController?.stop()
        mediaController?.clearMediaItems()
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
        playbackStatus.text = when {
            mediaController.playerError != null -> "Reconnecting to SMB…"
            mediaController.playbackState == Player.STATE_BUFFERING && !mediaController.playWhenReady ->
                "Building recovery buffer…"
            mediaController.playbackState == Player.STATE_BUFFERING -> "Buffering from SMB…"
            mediaController.playbackState == Player.STATE_READY && mediaController.isPlaying -> "Playing"
            mediaController.playbackState == Player.STATE_READY -> "Paused"
            mediaController.playbackState == Player.STATE_ENDED -> "Folder finished"
            mediaController.mediaItemCount == 0 -> "Nothing queued"
            else -> "Opening SMB stream…"
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

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }

    override fun onDestroy() {
        playerView.player = null
        controlsPlayerView.player = null
        controller?.removeListener(listener)
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        controller = null
        super.onDestroy()
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

    private enum class QueueSortMode(val label: String, val storageName: String) {
        NAME_ASC("A–Z", "NAME_ASC"),
        NAME_DESC("Z–A", "NAME_DESC"),
        DATE_DESC("New–Old", "DATE_DESC"),
        DATE_ASC("Old–New", "DATE_ASC");

        fun next(): QueueSortMode = entries[(ordinal + 1) % entries.size]

        companion object {
            fun fromStorage(value: String?): QueueSortMode =
                entries.firstOrNull { it.storageName == value } ?: NAME_ASC
        }
    }
}
