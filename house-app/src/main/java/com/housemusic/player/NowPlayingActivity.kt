package com.housemusic.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType
import androidx.appcompat.app.AlertDialog
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
import com.housemusic.player.house.HouseRuntime
import com.housemusic.player.house.HousePlayer
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.housemusic.player.house.HouseTransportControl
import com.housemusic.player.media.AudioFormats

@UnstableApi
class NowPlayingActivity : HousePageActivity() {
    override val housePage = 1
    private lateinit var houseTransport: HouseTransportControl
    private lateinit var playerView: PlayerView
    private lateinit var controlsPlayerView: PlayerView
    private lateinit var titleText: TextView
    private lateinit var albumArtistText: TextView
    private lateinit var albumText: TextView
    private lateinit var playbackStatus: TextView
    private lateinit var queueSortButton: Button
    private lateinit var muteOutputButton: ImageButton

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var queueSortMode = SortMode.NAME_ASC

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateMetadata(controller?.mediaMetadata ?: mediaItem?.mediaMetadata)
            updateStatus()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateMetadata(mediaMetadata)
            updateStatus()
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
            updateMetadata(controller.mediaMetadata)
            updateStatus()
        }
        override fun onDisconnected(controller: MediaController) {
            if (::houseTransport.isInitialized) houseTransport.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)
        applySystemBarInsets()

        playerView = findViewById(R.id.playerView)
        controlsPlayerView = findViewById(R.id.controlsPlayerView)
        houseTransport = HouseTransportControl(
            controlsPlayerView.findViewById(R.id.housePlayPauseButton)) { controller }
        titleText = findViewById(R.id.nowPlayingText)
        albumArtistText = findViewById(R.id.albumArtistText)
        albumText = findViewById(R.id.albumText)
        playbackStatus = findViewById(R.id.playbackStatus)
        queueSortButton = findViewById(R.id.queueSortButton)
        muteOutputButton = controlsPlayerView.findViewById(R.id.muteOutputButton)
        controlsPlayerView.findViewById<ImageButton>(R.id.houseSettingsButton).setOnClickListener { showSyncAdjustment() }
        playbackStatus.setOnLongClickListener { HouseDiagnostics.show(this); true }
        muteOutputButton.setOnLongClickListener { showSyncAdjustment(); true }
        muteOutputButton.setOnClickListener {
            controller?.sendCustomCommand(SessionCommand(HouseRuntime.MUTE, Bundle.EMPTY), Bundle.EMPTY)
        }

        findViewById<Button>(R.id.quitButton).setOnClickListener {
            quitCleanly()
        }

        queueSortMode = SortModeStore.load(this)
        updateSortButton()
        queueSortButton.setOnClickListener {
            sortCurrentQueue(queueSortMode.next())
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
                if (isFinishing || isDestroyed) return@addListener
                try {
                    val mediaController = controllerFuture.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    controlsPlayerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL)

                    // Artwork and controls intentionally share the same MediaController.
                    // The first PlayerView is artwork-only; the second uses PlayerView's
                    // own Media3 controller UI, just like v0.2.
                    playerView.player = mediaController
                    controlsPlayerView.player = mediaController
                    controlsPlayerView.showController()

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

    private fun sortCurrentQueue(mode: SortMode) {
        val mediaController = controller ?: return
        if (mediaController.sessionExtras.getBoolean(HouseRuntime.EXTRA_RADIO)) return
        queueSortButton.isEnabled = false
        val future = mediaController.sendCustomCommand(SessionCommand(HouseRuntime.SORT, Bundle.EMPTY), Bundle().apply { putString("mode", mode.name) })
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            queueSortButton.isEnabled = !mediaController.sessionExtras.getBoolean(HouseRuntime.EXTRA_RADIO)
            if (runCatching { future.get().resultCode == 0 }.getOrDefault(false)) {
                queueSortMode = mode
                SortModeStore.save(this, mode)
                updateSortButton()
            } else playbackStatus.text = "House Music sort failed — refresh before trying again."
        }, ContextCompat.getMainExecutor(this))
        // Commit the shared sort setting only after the server acknowledges it.
    }

    private fun updateSortButton() {
        val extras = controller?.sessionExtras
        val radio = extras?.getBoolean(HouseRuntime.EXTRA_CONNECTED) == true && extras.getBoolean(HouseRuntime.EXTRA_RADIO)
        queueSortButton.text = if (radio) "LIVE" else queueSortMode.label
        queueSortButton.isEnabled = !radio
    }

    private fun quitCleanly() {
        playerView.player = null
        controlsPlayerView.player = null
        startService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_QUIT))
        finishAffinity()
    }

    private fun showSyncAdjustment() {
        val mediaController = controller ?: run {
            playbackStatus.text = "House Music — connecting to playback service"
            return
        }
        val extras = mediaController.sessionExtras
        val bluetooth = extras.getBoolean(HouseRuntime.EXTRA_BLUETOOTH)
        val profile = if (bluetooth) "Bluetooth" else "Phone / wired"
        val buffer = if (extras.containsKey(HouseRuntime.EXTRA_STREAM_BUFFER))
            "${extras.getInt(HouseRuntime.EXTRA_STREAM_BUFFER)} ms" else "not reported yet — unmute to read"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        layout.addView(TextView(this).apply {
            text = "Shared buffer: $buffer\nServer latency: ${extras.getInt(HouseRuntime.EXTRA_SERVER_LATENCY)} ms\n" +
                "Applied correction: ${extras.getInt(HouseRuntime.EXTRA_SYNC_APPLIED)} ms\n\n" +
                "Positive makes this phone earlier; negative makes it later. Range: −1000 to 1000 ms. " +
                "Advance is limited by the reported buffer. Applying rejoins this phone’s audio."
        })
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(extras.getInt(HouseRuntime.EXTRA_SYNC_OFFSET).toString())
            hint = "Correction in milliseconds"
            selectAll()
        }
        layout.addView(input)
        val dialog = AlertDialog.Builder(this).setTitle("$profile sync")
            .setView(layout).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val offset = input.text.toString().toIntOrNull()
                if (offset == null || offset !in -1000..1000) {
                    input.error = "Enter −1000 to 1000 ms"
                } else {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.isEnabled = false
                    val result = mediaController.sendCustomCommand(SessionCommand(HouseRuntime.SYNC, Bundle.EMPTY),
                        Bundle().apply { putInt("offsetMs", offset); putBoolean("bluetooth", bluetooth) })
                    result.addListener({
                        if (isFinishing || isDestroyed) return@addListener
                        button.isEnabled = true
                        if (runCatching { result.get().resultCode == 0 }.getOrDefault(false)) dialog.dismiss()
                        else input.error = "Could not apply — reconnect and try again"
                    }, ContextCompat.getMainExecutor(this))
                }
            }
        }
        dialog.show()
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        val itemMetadata = controller?.currentMediaItem?.mediaMetadata
        val sessionExtras = controller?.sessionExtras
        val radio = sessionExtras?.getBoolean(HouseRuntime.EXTRA_CONNECTED) == true && sessionExtras.getBoolean(HouseRuntime.EXTRA_RADIO)
        findViewById<View>(R.id.localMetadataPanel).visibility = if (radio) View.GONE else View.VISIBLE
        playerView.visibility = if (radio) View.GONE else View.VISIBLE
        findViewById<View>(R.id.radioDetailsScroll).visibility = if (radio) View.VISIBLE else View.GONE
        if (radio) {
            RadioNowPlayingView.bind(findViewById(R.id.radioDetailsScroll), sessionExtras!!)
            return
        }
        val filename = itemMetadata?.extras?.getString(MainActivity.EXTRA_FILENAME).orEmpty()

        // Use the server's track metadata, falling back to the queue filename when needed.
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
        houseTransport.refresh()
        val mediaController = controller ?: return
        val extras = mediaController.sessionExtras
        val radio = extras.getBoolean(HouseRuntime.EXTRA_CONNECTED) && extras.getBoolean(HouseRuntime.EXTRA_RADIO)
        controlsPlayerView.showLiveRadioControls(radio)
        updateSortButton()
        val muted = extras.getBoolean(HouseRuntime.EXTRA_MUTED, true)
        muteOutputButton.setImageResource(if (muted) R.drawable.ic_output_muted else R.drawable.ic_output_on)
        muteOutputButton.contentDescription = getString(if (muted) R.string.unmute_output else R.string.mute_output)
        muteOutputButton.tooltipText = "${muteOutputButton.contentDescription}; hold for sync adjustment"
        val status = extras.getString(HouseRuntime.EXTRA_STATUS, "House Music — connecting").orEmpty()
        val liveStatus = when (extras.getString(HouseRuntime.EXTRA_RADIO_STATUS)) {
            "connecting" -> "Connecting"
            "retrying" -> "Reconnecting"
            "paused" -> "Paused · Play rejoins live"
            "stopped" -> "Stopped"
            "error" -> "Station unavailable"
            else -> "Playing"
        }
        val radioError = extras.getString(HouseRuntime.EXTRA_RADIO_ERROR).orEmpty().take(160)
        playbackStatus.text = if (radio) listOf("LIVE · $liveStatus", status,
            radioError.takeIf { extras.getString(HouseRuntime.EXTRA_RADIO_STATUS) in setOf("error", "retrying") }.orEmpty())
            .filter { it.isNotBlank() }.joinToString("\n") else status
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

}

/** Render both source modes, so returning to a folder restores every local control. */
@UnstableApi
internal fun PlayerView.showLiveRadioControls(radio: Boolean) {
    setShowPreviousButton(!radio)
    setShowNextButton(!radio)
    setShowShuffleButton(!radio)
    setRepeatToggleModes(if (radio) RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE else RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL)
    // Keep the lower row's phone mute/sync positions, while live radio has no
    // elapsed/duration display or seek bar that could imply time shifting.
    findViewById<View>(androidx.media3.ui.R.id.exo_time)?.visibility = if (radio) View.INVISIBLE else View.VISIBLE
    findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.visibility = if (radio) View.INVISIBLE else View.VISIBLE
}
