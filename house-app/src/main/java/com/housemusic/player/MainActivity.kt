package com.housemusic.player

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
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import com.housemusic.player.house.HouseApiException
import com.housemusic.player.house.HouseApi
import com.housemusic.player.house.HouseConnection
import com.housemusic.player.house.HouseRuntime
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.common.util.concurrent.ListenableFuture
import com.housemusic.player.model.RemoteEntry
import com.housemusic.player.ui.FileAdapter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : AppCompatActivity() {
    private lateinit var executor: ExecutorService

    private lateinit var connectionPanel: LinearLayout
    private lateinit var browserPanel: LinearLayout
    private lateinit var houseAddressEdit: EditText
    private lateinit var connectionStatus: TextView
    private lateinit var pathText: TextView
    private lateinit var browserStatus: TextView
    private lateinit var sortButton: Button
    private lateinit var searchEdit: EditText
    private lateinit var searchClearButton: ImageButton
    private lateinit var recycler: RecyclerView
    private lateinit var libraryRefresh: SwipeRefreshLayout
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
    private var browserVisible = false
    private var browseLoading = false
    private var libraryUpdating = false
    private var libraryUpdateUnsupported = false

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

        setupController()
        restoreSavedConnection()
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
        houseAddressEdit = findViewById(R.id.houseAddressEdit)
        connectionStatus = findViewById(R.id.connectionStatus)
        pathText = findViewById(R.id.pathText)
        browserStatus = findViewById(R.id.browserStatus)
        sortButton = findViewById(R.id.sortButton)
        searchEdit = findViewById(R.id.searchEdit)
        searchClearButton = findViewById(R.id.searchClearButton)
        recycler = findViewById(R.id.fileList)
        libraryRefresh = findViewById(R.id.libraryRefresh)
    }

    private fun setupController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) { showHouseDefault(extras) }
        }).buildAsync()
        controllerFuture.addListener(
            {
                if (isFinishing || isDestroyed) return@addListener
                try {
                    controller = controllerFuture.get().also {
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

        libraryRefresh.setOnRefreshListener {
            browse(currentUrl, forceUpdate = true)
        }
        browserStatus.setOnLongClickListener { HouseDiagnostics.show(this); true }
        findViewById<Button>(R.id.upButton).setOnClickListener {
            if (currentUrl.isNotBlank()) browse(currentUrl.substringBeforeLast('/', ""))
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            val mediaController = controller ?: return@setOnClickListener
            val current = mediaController.sessionExtras.getString(HouseRuntime.EXTRA_DEFAULT)
            if (current !in listOf("MP3s", "Rap")) return@setOnClickListener
            val button = findViewById<Button>(R.id.settingsButton)
            button.isEnabled = false
            val future = mediaController.sendCustomCommand(
                SessionCommand(HouseRuntime.DEFAULT, Bundle.EMPTY), Bundle().apply {
                    putString("folder", if (current == "MP3s") "Rap" else "MP3s")
                })
            future.addListener({
                if (isFinishing || isDestroyed) return@addListener
                button.isEnabled = true
                if (!runCatching { future.get().resultCode == 0 }.getOrDefault(false)) {
                    browserStatus.text = "Default folder change failed — try again when connected."
                }
            }, ContextCompat.getMainExecutor(this))
        }
        findViewById<Button>(R.id.serverSettingsButton).setOnClickListener {
            houseAddressEdit.setText(HouseConnection.host(this))
            connectionStatus.text = "Enter the Pi’s LAN address."
            connectionPanel.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.playFolderButton).setOnClickListener {
            playCurrentFolder(null)
        }

        findViewById<Button>(R.id.nowPlayingButton).setOnClickListener {
            openNowPlaying()
        }

        findViewById<Button>(R.id.radioButton).setOnClickListener {
            hideSearchKeyboard()
            startActivity(Intent(this, RadioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
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
        findViewById<Button>(R.id.cancelConnectionButton).setOnClickListener {
            connectionPanel.visibility = View.GONE
            hideSearchKeyboard()
        }
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            try {
                val host = houseAddressEdit.text.toString().trim()
                val changed = host != HouseConnection.host(this)
                require(host.isNotBlank()) { "Enter the Pi’s LAN host name or IP address." }
                HouseConnection.saveHost(this, host)
                if (changed) {
                    browseRequestGeneration++
                    cancelBrowseRetry()
                    entries = emptyList()
                    adapter.submit(emptyList())
                    currentUrl = ""
                    pendingListState = null
                    pendingListUrl = null
                    HouseConnection.preferences(this).edit().remove("last_folder").apply()
                    ContextCompat.startForegroundService(this,
                        Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_RECONNECT))
                }
                connectionPanel.visibility = View.GONE
                hideSearchKeyboard()
                browse(currentUrl)
            } catch (e: Exception) {
                connectionStatus.text = friendlyError(e)
            }
        }
    }

    private fun restoreSavedConnection() {
        val host = HouseConnection.host(this)
        houseAddressEdit.setText(host)
        currentUrl = pendingListUrl
            ?: HouseConnection.preferences(this).getString("last_folder", "").orEmpty()
        connectionPanel.visibility = if (host.isBlank()) View.VISIBLE else View.GONE
        browserPanel.visibility = View.VISIBLE
        findViewById<Button>(R.id.settingsButton).text = "…"
        browse(currentUrl)
    }

    private fun browse(url: String, resetRetry: Boolean = true, updateIndex: Boolean = true, forceUpdate: Boolean = false) {
        if (isFinishing || isDestroyed) return
        if (HouseConnection.host(this).isBlank()) {
            cancelBrowseRetry()
            libraryRefresh.isRefreshing = false
            browserStatus.text = "Set the house server address to browse music."
            return
        }
        browserHandler.removeCallbacksAndMessages(BROWSE_REFRESH_TOKEN)
        browseRetryPending = false
        val endpoint = HouseConnection.current
        if (resetRetry) {
            browseRetryEnabled = true
            browseRetryIndex = 0
            cancelBrowseRetry()
        }

        if (url == currentUrl && entries.isNotEmpty() && pendingListState == null) {
            pendingListState = layoutManager.onSaveInstanceState()
            pendingListUrl = url
        } else if (url != currentUrl) {
            entries = emptyList()
            adapter.submit(emptyList())
        }
        browseLoading = true
        if (forceUpdate) libraryRefresh.isRefreshing = true
        currentUrl = url
        pathText.text = url.substringAfterLast('/').ifBlank { "Music" }
        browserStatus.text = if (resetRetry) "Loading…" else "Reconnecting to House Music…"
        val requestGeneration = ++browseRequestGeneration

        executor.execute {
            try {
                checkNotNull(endpoint) { "House server is not connected" }
                val api = HouseApi(this, endpoint)
                var unsupported = libraryUpdateUnsupported
                if (updateIndex) {
                    try {
                        api.updateLibrary(forceUpdate)
                        unsupported = false
                    } catch (e: HouseApiException) {
                        if (e.status != 404) throw e
                        unsupported = true // Older server still supports browsing and playback.
                    }
                }
                val result = api.browse(url)
                runOnUiThread {
                    if (isFinishing || isDestroyed || currentUrl != url || requestGeneration != browseRequestGeneration) return@runOnUiThread
                    cancelBrowseRetry()
                    browseRetryIndex = 0
                    browseLoading = false
                    libraryRefresh.isRefreshing = false
                    libraryUpdating = result.updating
                    libraryUpdateUnsupported = unsupported
                    entries = result.entries
                    HouseConnection.preferences(this).edit().putString("last_folder", url).apply()
                    showSortedEntries()

                    if (pendingListUrl == url && pendingListState != null) {
                        layoutManager.onRestoreInstanceState(pendingListState)
                        pendingListState = null
                        pendingListUrl = null
                    }
                    scheduleLibraryRefresh()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || currentUrl != url || requestGeneration != browseRequestGeneration) return@runOnUiThread
                    browseLoading = false
                    libraryRefresh.isRefreshing = false
                    HouseDiagnostics.record(this, "Library refresh failed: ${e.javaClass.simpleName}")
                    scheduleBrowseRetry(url, e)
                }
            }
        }
    }

    private fun scheduleLibraryRefresh() {
        if (!browserVisible) return
        val scanning = libraryUpdating
        browserHandler.postAtTime({
            if (browserVisible && !browseLoading) browse(currentUrl, updateIndex = !scanning)
        }, BROWSE_REFRESH_TOKEN, android.os.SystemClock.uptimeMillis() + if (scanning) 2_000 else 30_000)
    }

    private fun scheduleBrowseRetry(url: String, error: Throwable) {
        if (!browseRetryEnabled || !browserVisible) return
        cancelBrowseRetry()

        val delay = BROWSE_RETRY_SCHEDULE_MS[minOf(browseRetryIndex, BROWSE_RETRY_SCHEDULE_MS.lastIndex)]
        browseRetryIndex++
        browseRetryPending = true
        val seconds = delay / 1000
        browserStatus.text = "House server unavailable: ${friendlyError(error)}\nRetrying in ${seconds}s…"
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

    private fun hideSearchKeyboard() {
        searchEdit.clearFocus()
        findViewById<View>(R.id.mainRoot).requestFocus()
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchEdit.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        browserVisible = true
        if (!browseLoading) browse(currentUrl)
        syncSortModeFromStore()
        // Browse should never throw the keyboard up merely because the Activity was opened or
        // brought back from Now Playing. Tapping the search typing area still focuses it normally.
        if (::searchEdit.isInitialized) searchEdit.post { hideSearchKeyboard() }
    }

    override fun onPause() {
        browserVisible = false
        browserHandler.removeCallbacksAndMessages(BROWSE_REFRESH_TOKEN)
        cancelBrowseRetry()
        super.onPause()
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
        if (libraryUpdating) browserStatus.append(" · Scanning for new music…")
        if (libraryUpdateUnsupported) browserStatus.append("\nInstall server v0.9.1 for automatic library updates.")
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

        val button = findViewById<Button>(R.id.playFolderButton)
        button.isEnabled = false
        val future = mediaController.sendCustomCommand(SessionCommand(HouseRuntime.PLAY_LIST, Bundle.EMPTY), Bundle().apply {
            putStringArrayList("tracks", ArrayList(tracks.map { it.url }))
        })
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            button.isEnabled = true
            if (runCatching { future.get().resultCode == 0 }.getOrDefault(false)) openNowPlaying()
            else browserStatus.text = "House Music command failed — refresh before trying again."
        }, ContextCompat.getMainExecutor(this))
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
        if (isFinishing || isDestroyed) return
        findViewById<Button>(R.id.settingsButton).text =
            extras.getString(HouseRuntime.EXTRA_DEFAULT).orEmpty().ifBlank { "…" }
        val connected = extras.getBoolean(HouseRuntime.EXTRA_CONNECTED)
        val revision = extras.getLong(HouseRuntime.EXTRA_CONNECTION_REVISION)
        val recovered = connected && (!wasHouseConnected || revision != lastHouseConnectionRevision)
        wasHouseConnected = connected
        lastHouseConnectionRevision = revision
        if (recovered && browserVisible && !browseLoading) browse(currentUrl)
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
        browserHandler.removeCallbacksAndMessages(BROWSE_REFRESH_TOKEN)
        cancelBrowseRetry()
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

        const val EXTRA_FILENAME = "com.housemusic.player.filename"
        const val EXTRA_MODIFIED = "com.housemusic.player.modified"
        const val PREFS_UI = SortModeStore.PREFS_UI
        const val PREF_QUEUE_SORT = SortModeStore.PREF_SORT_MODE

        private val BROWSE_REFRESH_TOKEN = Any()
        private val BROWSE_RETRY_TOKEN = Any()
        private val BROWSE_RETRY_SCHEDULE_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 15_000)

    }
}
