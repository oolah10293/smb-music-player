package com.smbmusic.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import com.smbmusic.player.house.HouseApi
import com.smbmusic.player.house.HouseConnection
import com.smbmusic.player.house.HouseRuntime
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import com.smbmusic.player.model.RemoteEntry
import com.smbmusic.player.smb.SmbClient
import com.smbmusic.player.smb.SmbUrl
import com.smbmusic.player.storage.CredentialStore
import com.smbmusic.player.storage.SmbCredentials
import com.smbmusic.player.ui.FileAdapter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : AppCompatActivity() {
    private lateinit var store: CredentialStore
    private lateinit var smb: SmbClient
    private lateinit var executor: ExecutorService

    private lateinit var connectionPanel: LinearLayout
    private lateinit var browserPanel: LinearLayout
    private lateinit var addressEdit: EditText
    private lateinit var userEdit: EditText
    private lateinit var passwordEdit: EditText
    private lateinit var houseAddressEdit: EditText
    private val isHouse: Boolean get() = HouseConnection.current != null
    private lateinit var connectionStatus: TextView
    private lateinit var pathText: TextView
    private lateinit var browserStatus: TextView
    private lateinit var sortButton: Button
    private lateinit var searchEdit: EditText
    private lateinit var searchClearButton: ImageButton
    private lateinit var recycler: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: FileAdapter

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    private val browserHandler = Handler(Looper.getMainLooper())
    private var browseRetryPending = false
    private var lastHouseConnectionRevision = -1L
    private var wasHouseConnected = false
    private var browseRetryIndex = 0
    private var browseRequestGeneration = 0
    private var browseRetryEnabled = true
    private var tailscaleRecoveryRequested = false

    private var rootUrl: String = ""
    private var currentUrl: String = ""
    private var entries: List<RemoteEntry> = emptyList()
    private var sortMode = SortMode.NAME_ASC
    private var pendingListState: Parcelable? = null
    private var pendingListUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        applySystemBarInsets()

        store = CredentialStore(this)
        smb = SmbClient(store)
        executor = Executors.newSingleThreadExecutor()

        bindViews()
        sortMode = SortModeStore.load(this)
        sortButton.text = sortMode.label
        findViewById<View>(R.id.mainRoot).requestFocus()
        setupBrowser()
        setupConnection()

        if (savedInstanceState != null) {
            pendingListState = savedInstanceState.getParcelable(STATE_LIST)
            pendingListUrl = savedInstanceState.getString(STATE_URL)
        }

        browserStatus.text = "Checking home connection…"
        val selectionEpoch = HouseConnection.epoch
        executor.execute {
            val selected = if (HouseConnection.resolved) HouseConnection.current else HouseConnection.probe(this)
            runOnUiThread {
                if (isFinishing || isDestroyed || !HouseConnection.publish(selectionEpoch, selected)) return@runOnUiThread
                if (!isHouse) requestTailscaleConnect()
                setupController()
                restoreSavedConnection()
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 30)
        }
    }

    private fun applySystemBarInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(R.id.mainRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun bindViews() {
        connectionPanel = findViewById(R.id.connectionPanel)
        browserPanel = findViewById(R.id.browserPanel)
        addressEdit = findViewById(R.id.addressEdit)
        userEdit = findViewById(R.id.userEdit)
        passwordEdit = findViewById(R.id.passwordEdit)
        houseAddressEdit = findViewById(R.id.houseAddressEdit)
        connectionStatus = findViewById(R.id.connectionStatus)
        pathText = findViewById(R.id.pathText)
        browserStatus = findViewById(R.id.browserStatus)
        sortButton = findViewById(R.id.sortButton)
        searchEdit = findViewById(R.id.searchEdit)
        searchClearButton = findViewById(R.id.searchClearButton)
        recycler = findViewById(R.id.fileList)
    }

    private fun setupController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        if (isHouse) ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) { showHouseDefault(extras) }
        }).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    controller = controllerFuture.get().also {
                        if (!isHouse) it.repeatMode = Player.REPEAT_MODE_ALL
                        showHouseDefault(it.sessionExtras)
                    }
                } catch (e: Exception) {
                    browserStatus.text = "Playback service failed: ${friendlyError(e)}"
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun setupBrowser() {
        adapter = FileAdapter { entry ->
            if (entry.isDirectory) browse(entry.url) else playCurrentFolder(entry.url)
        }
        layoutManager = LinearLayoutManager(this)
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter

        findViewById<Button>(R.id.upButton).setOnClickListener {
            if (isHouse) {
                if (currentUrl.isNotBlank()) browse(currentUrl.substringBeforeLast('/', ""))
            } else if (currentUrl.isNotBlank() && rootUrl.isNotBlank()) {
                SmbUrl.parent(currentUrl, rootUrl)?.let { browse(it) }
            }
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            if (isHouse) {
                val current = controller?.sessionExtras?.getString(HouseRuntime.EXTRA_DEFAULT)
                if (current in listOf("MP3s", "Rap")) controller?.sendCustomCommand(
                    SessionCommand(HouseRuntime.DEFAULT, Bundle.EMPTY), Bundle().apply {
                        putString("folder", if (current == "MP3s") "Rap" else "MP3s")
                    })
                return@setOnClickListener
            }
            browseRetryEnabled = false
            browseRequestGeneration++
            cancelBrowseRetry()
            connectionPanel.visibility = View.VISIBLE
        }
        findViewById<Button>(R.id.settingsButton).setOnLongClickListener {
            connectionPanel.visibility = View.VISIBLE
            true
        }

        findViewById<Button>(R.id.playFolderButton).setOnClickListener {
            playCurrentFolder(null)
        }

        findViewById<Button>(R.id.nowPlayingButton).setOnClickListener {
            openNowPlaying()
        }

        sortButton.setOnClickListener {
            sortMode = sortMode.next()
            SortModeStore.save(this, sortMode)
            sortButton.text = sortMode.label
            showSortedEntries()
        }

        searchClearButton.setOnClickListener {
            // The clear control is a separate, non-focusable target. Clearing never requests
            // keyboard focus: if the keyboard is hidden it stays hidden; if the user is already
            // typing, the EditText keeps its existing focus and keyboard state.
            if (searchEdit.text.isNotEmpty()) searchEdit.text.clear()
        }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSearchClearButton()
                showSortedEntries()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateSearchClearButton()
    }

    private fun setupConnection() {
        findViewById<Button>(R.id.testButton).setOnClickListener {
            val credentials = enteredCredentials() ?: return@setOnClickListener
            connectionStatus.text = "Testing…"
            executor.execute {
                try {
                    val count = smb.test(credentials)
                    runOnUiThread {
                        connectionStatus.text = "Connected. $count item(s) visible at root."
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        connectionStatus.text = "Test failed: ${friendlyError(e)}"
                    }
                }
            }
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val host = houseAddressEdit.text.toString().trim()
            val hostChanged = host != HouseConnection.host(this)
            try { HouseConnection.saveHost(this, host) } catch (e: Exception) {
                connectionStatus.text = e.message; return@setOnClickListener
            }
            val credentials = enteredCredentials() ?: return@setOnClickListener
            try {
                val normalized = SmbUrl.normalize(credentials.address)
                store.save(credentials.copy(address = normalized))
                smb.invalidate()
                if (!isHouse) {
                    rootUrl = normalized
                    currentUrl = rootUrl
                }
                connectionPanel.visibility = View.GONE
                browserPanel.visibility = View.VISIBLE
                browse(currentUrl)
                if (hostChanged) android.widget.Toast.makeText(this, "Saved. Quit and reopen to apply the house address.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                connectionStatus.text = "Bad address: ${friendlyError(e)}"
            }
        }
    }

    private fun restoreSavedConnection() {
        val credentials = store.load()
        addressEdit.setText(SmbUrl.display(credentials.address).trimEnd('/'))
        userEdit.setText(credentials.username)
        passwordEdit.setText(credentials.password)
        houseAddressEdit.setText(HouseConnection.host(this))

        if (isHouse) {
            rootUrl = ""
            currentUrl = pendingListUrl?.takeUnless { it.startsWith("smb:") }
                ?: HouseConnection.preferences(this).getString("last_folder", "").orEmpty()
            connectionPanel.visibility = View.GONE
            browserPanel.visibility = View.VISIBLE
            findViewById<Button>(R.id.settingsButton).text = "…"
            browse(currentUrl)
            return
        }

        if (credentials.address.isNotBlank()) {
            try {
                rootUrl = SmbUrl.normalize(credentials.address)
                val savedStateUrl = pendingListUrl
                val last = when {
                    !savedStateUrl.isNullOrBlank() && savedStateUrl.startsWith(rootUrl) -> savedStateUrl
                    else -> store.loadLastFolder()
                }
                currentUrl = if (last.startsWith(rootUrl)) last else rootUrl
                connectionPanel.visibility = View.GONE
                browserPanel.visibility = View.VISIBLE
                browse(currentUrl)
            } catch (_: Exception) {
                connectionPanel.visibility = View.VISIBLE
            }
        }
    }

    private fun enteredCredentials(): SmbCredentials? {
        val address = addressEdit.text.toString().trim()
        val username = userEdit.text.toString().trim()
        val password = passwordEdit.text.toString()
        if (address.isBlank()) {
            connectionStatus.text = "Enter the SMB host/share path."
            return null
        }
        return SmbCredentials(address, username, password)
    }

    private fun browse(url: String, resetRetry: Boolean = true) {
        if (isFinishing || isDestroyed) return
        browseRetryPending = false
        val endpoint = HouseConnection.current
        val selectionEpoch = HouseConnection.epoch
        if (resetRetry) {
            browseRetryEnabled = true
            browseRetryIndex = 0
            tailscaleRecoveryRequested = false
            cancelBrowseRetry()
        }

        currentUrl = url
        pathText.text = if (isHouse) url.substringAfterLast('/').ifBlank { "Music" }
            else SmbUrl.display(url).trimEnd('/').substringAfterLast('/')
        browserStatus.text = if (resetRetry) "Loading…" else if (endpoint != null) "Retrying HOUSE…" else "Retrying SMB…"
        val requestGeneration = ++browseRequestGeneration

        executor.execute {
            try {
                val result = if (endpoint != null) HouseApi(this, endpoint).browse(url) else smb.list(url)
                runOnUiThread {
                    if (isFinishing || isDestroyed || selectionEpoch != HouseConnection.epoch ||
                        currentUrl != url || requestGeneration != browseRequestGeneration) return@runOnUiThread
                    cancelBrowseRetry()
                    browseRetryIndex = 0
                    tailscaleRecoveryRequested = false
                    entries = result
                    if (isHouse) HouseConnection.preferences(this).edit().putString("last_folder", url).apply()
                    else store.saveLastFolder(url)
                    showSortedEntries()

                    if (pendingListUrl == url && pendingListState != null) {
                        layoutManager.onRestoreInstanceState(pendingListState)
                        pendingListState = null
                        pendingListUrl = null
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || selectionEpoch != HouseConnection.epoch ||
                        currentUrl != url || requestGeneration != browseRequestGeneration) return@runOnUiThread
                    entries = emptyList()
                    adapter.submit(emptyList())
                    scheduleBrowseRetry(url, e)
                }
            }
        }
    }

    private fun scheduleBrowseRetry(url: String, error: Throwable) {
        if (!browseRetryEnabled) return
        cancelBrowseRetry()

        // Startup already asks Tailscale to connect. If SMB is still unreachable after
        // several retries, make one additional connect request sequence. This deliberately
        // does not force-disconnect Tailscale: a missing server/share should not tear down an
        // otherwise healthy VPN, and the existing SMB retry loop remains authoritative.
        if (!isHouse && !tailscaleRecoveryRequested && browseRetryIndex >= TAILSCALE_RECOVERY_AFTER_RETRIES) {
            tailscaleRecoveryRequested = true
            requestTailscaleConnect()
        }
        val delay = BROWSE_RETRY_SCHEDULE_MS[minOf(browseRetryIndex, BROWSE_RETRY_SCHEDULE_MS.lastIndex)]
        browseRetryIndex++
        browseRetryPending = true
        val seconds = delay / 1000
        browserStatus.text = "${if (isHouse) "HOUSE" else "SMB"} unavailable: ${friendlyError(error)}\nRetrying in ${seconds}s…"
        browserHandler.postAtTime(
            { if (currentUrl == url) browse(url, resetRetry = false) },
            BROWSE_RETRY_TOKEN,
            android.os.SystemClock.uptimeMillis() + delay
        )
    }

    private fun cancelBrowseRetry() {
        browseRetryPending = false
        browserHandler.removeCallbacksAndMessages(BROWSE_RETRY_TOKEN)
    }

    private fun requestTailscaleConnect() {
        browserHandler.removeCallbacksAndMessages(TAILSCALE_CONNECT_TOKEN)
        sendTailscaleConnectBroadcast()
        // Current Tailscale Android exposes CONNECT_VPN for external automation. On some
        // Android 16 devices a second request shortly after the first is more reliable while
        // the VPN backend is starting. Sending CONNECT_VPN while already connected is benign.
        browserHandler.postAtTime(
            { sendTailscaleConnectBroadcast() },
            TAILSCALE_CONNECT_TOKEN,
            android.os.SystemClock.uptimeMillis() + TAILSCALE_SECOND_CONNECT_DELAY_MS
        )
    }

    private fun sendTailscaleConnectBroadcast() {
        runCatching {
            sendBroadcast(
                Intent(TAILSCALE_CONNECT_ACTION)
                    .setPackage(TAILSCALE_PACKAGE)
            )
        }
    }

    private fun hideSearchKeyboard() {
        searchEdit.clearFocus()
        findViewById<View>(R.id.mainRoot).requestFocus()
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchEdit.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        syncSortModeFromStore()
        // Browse should never throw the keyboard up merely because the Activity was opened or
        // brought back from Now Playing. Tapping the search typing area still focuses it normally.
        if (::searchEdit.isInitialized) searchEdit.post { hideSearchKeyboard() }
    }

    private fun syncSortModeFromStore() {
        if (!::sortButton.isInitialized) return
        val sharedMode = SortModeStore.load(this)
        if (sharedMode == sortMode) return
        sortMode = sharedMode
        sortButton.text = sortMode.label
        if (::adapter.isInitialized) showSortedEntries()
    }

    private fun updateSearchClearButton() {
        searchClearButton.visibility = if (searchEdit.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun showSortedEntries() {
        val visible = filteredEntries()
        val sorted = when (sortMode) {
            // Name sort keeps the familiar browser behavior: folders first, then tracks.
            SortMode.NAME_ASC -> visible.sortedWith(compareBy<RemoteEntry>({ !it.isDirectory }, { it.name.lowercase() }))
            SortMode.NAME_DESC -> visible.sortedWith(compareBy<RemoteEntry> { !it.isDirectory }.thenByDescending { it.name.lowercase() })

            // Date sort is a true date sort across folders and tracks. Previously all folders were
            // forced ahead of every track, which made a new track look "missing" in a mixed folder.
            SortMode.DATE_ASC -> visible.sortedWith(compareBy<RemoteEntry>({ it.modified }, { it.name.lowercase() }))
            SortMode.DATE_DESC -> visible.sortedWith(compareByDescending<RemoteEntry> { it.modified }.thenBy { it.name.lowercase() })
        }
        adapter.submit(sorted)

        val tracks = visible.count { it.isAudio }
        val dirs = visible.count { it.isDirectory }
        val query = searchEdit.text.toString().trim()
        browserStatus.text = if (query.isBlank()) {
            "$dirs folder(s), $tracks track(s)"
        } else {
            "$dirs folder(s), $tracks track(s) matching"
        }
    }

    private fun filteredEntries(): List<RemoteEntry> {
        val query = searchEdit.text.toString().trim()
        if (query.isBlank()) return entries

        // Treat whitespace as an AND separator between search terms. Each term only
        // needs to appear somewhere in the filename/folder name, so punctuation or
        // the lack of punctuation between terms does not matter (for example,
        // "blink 182" matches Blink-182, Blink_182, Blink$182, and Blink182).
        val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        return entries.filter { entry ->
            terms.all { term -> entry.name.contains(term, ignoreCase = true) }
        }
    }

    private fun playCurrentFolder(startUrl: String?) {
        val mediaController = controller
        if (mediaController == null) {
            browserStatus.text = "Playback service is still connecting — tap again."
            return
        }

        val sortedTracks = sortedTracks()
        if (sortedTracks.isEmpty()) {
            browserStatus.text = if (searchEdit.text.toString().trim().isBlank()) {
                "No supported audio files in this folder."
            } else {
                "No matching audio files."
            }
            return
        }

        // A selected start track becomes queue item zero. The remaining sorted sequence wraps
        // around from that track: D,E,F,A,B,C rather than starting at an interior queue index.
        val selectedIndex = startUrl
            ?.let { url -> sortedTracks.indexOfFirst { it.url == url } }
            ?.takeIf { it >= 0 }
            ?: 0
        val tracks = rotateFrom(sortedTracks, selectedIndex)

        if (isHouse) {
            val future = mediaController.sendCustomCommand(SessionCommand(HouseRuntime.PLAY_LIST, Bundle.EMPTY), Bundle().apply {
                putStringArrayList("tracks", ArrayList(tracks.map { it.url }))
            })
            future.addListener({
                if (runCatching { future.get().resultCode == 0 }.getOrDefault(false)) openNowPlaying()
                else browserStatus.text = "HOUSE command failed — refresh before trying again."
            }, ContextCompat.getMainExecutor(this))
            return
        }

        val mediaItems = tracks.map { entry ->
            val extras = Bundle().apply {
                putString(EXTRA_FILENAME, entry.name)
                putLong(EXTRA_MODIFIED, entry.modified)
            }
            MediaItem.Builder()
                .setMediaId(entry.url)
                .setUri(entry.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        SortModeStore.save(this, sortMode)

        // setMediaItems replaces the prior folder/filter outright. If the prior song is not in
        // this newly requested playlist, it is intentionally not retained.
        mediaController.repeatMode = Player.REPEAT_MODE_ALL
        mediaController.setMediaItems(mediaItems, 0, 0L)
        mediaController.prepare()
        mediaController.play()
        openNowPlaying()
    }

    private fun sortedTracks(): List<RemoteEntry> {
        // When search is active, playback is intentionally limited to the matching tracks.
        val tracks = filteredEntries().filter { it.isAudio }
        return when (sortMode) {
            SortMode.NAME_ASC -> tracks.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> tracks.sortedByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> tracks.sortedBy { it.modified }
            SortMode.DATE_DESC -> tracks.sortedByDescending { it.modified }
        }
    }

    private fun <T> rotateFrom(items: List<T>, startIndex: Int): List<T> {
        if (items.isEmpty() || startIndex <= 0) return items
        return items.drop(startIndex) + items.take(startIndex)
    }

    private fun openNowPlaying() {
        startActivity(Intent(this, NowPlayingActivity::class.java))
    }

    private fun showHouseDefault(extras: Bundle) {
        if (extras.getBoolean(HouseRuntime.EXTRA_HOUSE)) {
            findViewById<Button>(R.id.settingsButton).text = extras.getString(HouseRuntime.EXTRA_DEFAULT).orEmpty().ifBlank { "…" }
            val connected = extras.getBoolean(HouseRuntime.EXTRA_CONNECTED)
            val revision = extras.getLong(HouseRuntime.EXTRA_CONNECTION_REVISION)
            val recovered = connected && (!wasHouseConnected || revision != lastHouseConnectionRevision)
            wasHouseConnected = connected
            lastHouseConnectionRevision = revision
            if (recovered && browseRetryPending) browse(currentUrl)
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_URL, currentUrl)
        outState.putParcelable(STATE_LIST, layoutManager.onSaveInstanceState())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        browseRequestGeneration++
        browseRetryEnabled = false
        cancelBrowseRetry()
        browserHandler.removeCallbacksAndMessages(TAILSCALE_CONNECT_TOKEN)
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        controller = null
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val STATE_URL = "browser_url"
        private const val STATE_LIST = "list_state"

        const val EXTRA_FILENAME = "com.smbmusic.player.filename"
        const val EXTRA_MODIFIED = "com.smbmusic.player.modified"
        const val PREFS_UI = SortModeStore.PREFS_UI
        const val PREF_QUEUE_SORT = SortModeStore.PREF_SORT_MODE

        private val BROWSE_RETRY_TOKEN = Any()
        private val TAILSCALE_CONNECT_TOKEN = Any()
        private val BROWSE_RETRY_SCHEDULE_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 15_000)

        private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
        private const val TAILSCALE_CONNECT_ACTION = "com.tailscale.ipn.CONNECT_VPN"
        private const val TAILSCALE_SECOND_CONNECT_DELAY_MS = 2_000L
        private const val TAILSCALE_RECOVERY_AFTER_RETRIES = 3
    }
}
