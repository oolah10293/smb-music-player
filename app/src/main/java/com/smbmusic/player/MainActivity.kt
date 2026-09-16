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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
    private lateinit var connectionStatus: TextView
    private lateinit var pathText: TextView
    private lateinit var browserStatus: TextView
    private lateinit var sortButton: Button
    private lateinit var searchEdit: EditText
    private lateinit var recycler: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: FileAdapter

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    private val browserHandler = Handler(Looper.getMainLooper())
    private var browseRetryIndex = 0
    private var browseRequestGeneration = 0
    private var browseRetryEnabled = true

    private var rootUrl: String = ""
    private var currentUrl: String = ""
    private var entries: List<RemoteEntry> = emptyList()
    private var sortMode = SortMode.NAME_ASC
    private var pendingListState: Parcelable? = null
    private var pendingListUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        store = CredentialStore(this)
        smb = SmbClient(store)
        executor = Executors.newSingleThreadExecutor()

        bindViews()
        setupController()
        setupBrowser()
        setupConnection()

        if (savedInstanceState != null) {
            sortMode = runCatching {
                SortMode.valueOf(savedInstanceState.getString(STATE_SORT).orEmpty())
            }.getOrDefault(SortMode.NAME_ASC)
            sortButton.text = sortMode.label
            pendingListState = savedInstanceState.getParcelable(STATE_LIST)
            pendingListUrl = savedInstanceState.getString(STATE_URL)
        }

        restoreSavedConnection()
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
        connectionStatus = findViewById(R.id.connectionStatus)
        pathText = findViewById(R.id.pathText)
        browserStatus = findViewById(R.id.browserStatus)
        sortButton = findViewById(R.id.sortButton)
        searchEdit = findViewById(R.id.searchEdit)
        recycler = findViewById(R.id.fileList)
    }

    private fun setupController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    controller = controllerFuture.get()
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
            if (currentUrl.isNotBlank() && rootUrl.isNotBlank()) {
                SmbUrl.parent(currentUrl, rootUrl)?.let { browse(it) }
            }
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            browseRetryEnabled = false
            browseRequestGeneration++
            cancelBrowseRetry()
            connectionPanel.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.playFolderButton).setOnClickListener {
            playCurrentFolder(null)
        }

        findViewById<Button>(R.id.nowPlayingButton).setOnClickListener {
            openNowPlaying()
        }

        sortButton.setOnClickListener {
            sortMode = sortMode.next()
            sortButton.text = sortMode.label
            showSortedEntries()
        }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                showSortedEntries()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
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
            val credentials = enteredCredentials() ?: return@setOnClickListener
            try {
                val normalized = SmbUrl.normalize(credentials.address)
                store.save(credentials.copy(address = normalized))
                smb.invalidate()
                rootUrl = normalized
                currentUrl = rootUrl
                connectionPanel.visibility = View.GONE
                browserPanel.visibility = View.VISIBLE
                browse(rootUrl)
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
        if (resetRetry) {
            browseRetryEnabled = true
            browseRetryIndex = 0
            cancelBrowseRetry()
        }

        currentUrl = url
        pathText.text = SmbUrl.display(url)
        browserStatus.text = if (resetRetry) "Loading…" else "Retrying SMB…"
        val requestGeneration = ++browseRequestGeneration

        executor.execute {
            try {
                val result = smb.list(url)
                runOnUiThread {
                    if (currentUrl != url || requestGeneration != browseRequestGeneration) return@runOnUiThread
                    cancelBrowseRetry()
                    browseRetryIndex = 0
                    entries = result
                    store.saveLastFolder(url)
                    showSortedEntries()

                    if (pendingListUrl == url && pendingListState != null) {
                        layoutManager.onRestoreInstanceState(pendingListState)
                        pendingListState = null
                        pendingListUrl = null
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (currentUrl != url || requestGeneration != browseRequestGeneration) return@runOnUiThread
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
        val delay = BROWSE_RETRY_SCHEDULE_MS[minOf(browseRetryIndex, BROWSE_RETRY_SCHEDULE_MS.lastIndex)]
        browseRetryIndex++
        val seconds = delay / 1000
        browserStatus.text = "SMB unavailable: ${friendlyError(error)}\nRetrying in ${seconds}s…"
        browserHandler.postAtTime(
            { if (currentUrl == url) browse(url, resetRetry = false) },
            BROWSE_RETRY_TOKEN,
            android.os.SystemClock.uptimeMillis() + delay
        )
    }

    private fun cancelBrowseRetry() {
        browserHandler.removeCallbacksAndMessages(BROWSE_RETRY_TOKEN)
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

        val tracks = sortedTracks()
        if (tracks.isEmpty()) {
            browserStatus.text = if (searchEdit.text.toString().trim().isBlank()) {
                "No supported audio files in this folder."
            } else {
                "No matching audio files."
            }
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

        val startIndex = if (startUrl == null) 0 else {
            tracks.indexOfFirst { it.url == startUrl }.coerceAtLeast(0)
        }

        getSharedPreferences(PREFS_UI, MODE_PRIVATE)
            .edit()
            .putString(PREF_QUEUE_SORT, sortMode.name)
            .apply()

        mediaController.setMediaItems(mediaItems, startIndex, 0L)
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

    private fun openNowPlaying() {
        startActivity(Intent(this, NowPlayingActivity::class.java))
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
        outState.putString(STATE_SORT, sortMode.name)
        outState.putParcelable(STATE_LIST, layoutManager.onSaveInstanceState())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        browseRetryEnabled = false
        cancelBrowseRetry()
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        controller = null
        executor.shutdownNow()
        super.onDestroy()
    }

    private enum class SortMode(val label: String) {
        NAME_ASC("A–Z"),
        NAME_DESC("Z–A"),
        DATE_DESC("New–Old"),
        DATE_ASC("Old–New");

        fun next(): SortMode = entries[(ordinal + 1) % entries.size]
    }

    companion object {
        private const val STATE_URL = "browser_url"
        private const val STATE_SORT = "sort_mode"
        private const val STATE_LIST = "list_state"

        const val EXTRA_FILENAME = "com.smbmusic.player.filename"
        const val EXTRA_MODIFIED = "com.smbmusic.player.modified"
        const val PREFS_UI = "smb_music_ui"
        const val PREF_QUEUE_SORT = "queue_sort_mode"

        private val BROWSE_RETRY_TOKEN = Any()
        private val BROWSE_RETRY_SCHEDULE_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 15_000)
    }
}
