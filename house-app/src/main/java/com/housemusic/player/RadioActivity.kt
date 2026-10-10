package com.housemusic.player

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.common.util.concurrent.ListenableFuture
import com.housemusic.player.house.HouseApi
import com.housemusic.player.house.HouseApiException
import com.housemusic.player.house.HouseConnection
import com.housemusic.player.house.HouseEndpoint
import com.housemusic.player.house.HouseRuntime
import com.housemusic.player.house.HouseStation
import java.util.concurrent.Executors

/** Station bookmarks live on the Pi. Playback always goes through the house session. */
@UnstableApi
class RadioActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var urlEdit: EditText
    private lateinit var addButton: Button
    private lateinit var status: TextView
    private lateinit var emptyText: TextView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var adapter: StationAdapter
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var stations = emptyList<HouseStation>()
    private var connected = false
    private var supported = true
    private var visible = false
    private var loading = false
    private var listFailed = false
    private var busy = false
    @Volatile private var generation = 0L
    private var connectionEpoch = -1L
    private var connectionRevision = -1L
    private var selectedId: String? = null
    private var selectedName: String? = null
    private var radioStatus: String? = null
    private var undoStation: HouseStation? = null
    private var undoIdentity: Identity? = null
    private val addPlayGuard = RadioAddPlayGuard()

    private data class Identity(val endpoint: HouseEndpoint, val epoch: Long, val generation: Long)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = findViewById<View>(R.id.radioRoot)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        root.requestFocus()
        urlEdit = findViewById(R.id.radioUrlEdit)
        addButton = findViewById(R.id.radioAddButton)
        status = findViewById(R.id.radioStatus)
        emptyText = findViewById(R.id.radioEmptyText)
        refresh = findViewById(R.id.radioRefresh)
        adapter = StationAdapter(::playStation, ::renameStation, ::deleteStation)
        findViewById<RecyclerView>(R.id.radioStationList).apply {
            layoutManager = LinearLayoutManager(this@RadioActivity)
            adapter = this@RadioActivity.adapter
        }
        addButton.setOnClickListener { addStation() }
        urlEdit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { addStation(); true } else false
        }
        refresh.setOnRefreshListener { loadStations() }
        findViewById<Button>(R.id.radioUndoButton).setOnClickListener { undoDelete() }
        findViewById<Button>(R.id.radioLibraryButton).setOnClickListener {
            hideKeyboard()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
        }
        findViewById<Button>(R.id.radioNowPlayingButton).setOnClickListener {
            hideKeyboard()
            startActivity(Intent(this, NowPlayingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        updateControls()
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) { syncConnection(extras) }
            }).buildAsync()
        controllerFuture.addListener({
            if (isFinishing || isDestroyed) return@addListener
            try {
                controller = controllerFuture.get()
                syncConnection(controller!!.sessionExtras)
            } catch (error: Exception) {
                status.text = "Playback service unavailable: ${friendlyError(error)}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun syncConnection(extras: Bundle) {
        if (isFinishing || isDestroyed) return
        val epoch = HouseConnection.epoch
        val revision = extras.getLong(HouseRuntime.EXTRA_CONNECTION_REVISION)
        val endpoint = HouseConnection.current
        val nowConnected = extras.getBoolean(HouseRuntime.EXTRA_CONNECTED) && endpoint != null &&
            endpoint.host == HouseConnection.host(this)
        val changed = nowConnected != connected || epoch != connectionEpoch || revision != connectionRevision
        if (changed) {
            generation++
            connected = nowConnected
            connectionEpoch = epoch
            connectionRevision = revision
            loading = false
            listFailed = false
            busy = false
            supported = true
            stations = emptyList()
            refresh.isRefreshing = false
            clearUndo()
        }
        selectedId = if (extras.getBoolean(HouseRuntime.EXTRA_RADIO)) extras.getString(HouseRuntime.EXTRA_RADIO_STATION_ID) else null
        selectedName = extras.getString(HouseRuntime.EXTRA_RADIO_STATION_NAME)
        radioStatus = extras.getString(HouseRuntime.EXTRA_RADIO_STATUS)
        showStations()
        updateControls()
        if (!connected) {
            status.text = if (HouseConnection.host(this).isBlank()) "Set your house server address in Library → Server."
                else "Connect to your home network to use Radio."
        } else if (changed && visible) {
            loadStations()
        }
    }

    private fun identity(): Identity? {
        val endpoint = HouseConnection.current ?: return null
        return if (connected && endpoint.host == HouseConnection.host(this)) Identity(endpoint, HouseConnection.epoch, generation) else null
    }

    private fun isCurrent(request: Identity): Boolean = !isFinishing && !isDestroyed && connected &&
        request.generation == generation && request.epoch == HouseConnection.epoch && request.endpoint == HouseConnection.current

    private fun loadStations(keepStatus: Boolean = false) {
        handler.removeCallbacksAndMessages(REFRESH_TOKEN)
        val request = identity()
        if (request == null || busy || loading || !supported) {
            refresh.isRefreshing = false
            return
        }
        loading = true
        listFailed = false
        refresh.isRefreshing = true
        if (!keepStatus) status.text = "Loading saved stations…"
        showStations()
        executor.execute {
            try {
                val result = HouseApi(applicationContext, request.endpoint).radioStations()
                runOnUiThread {
                    if (!isCurrent(request)) return@runOnUiThread
                    stations = result
                    loading = false
                    refresh.isRefreshing = false
                    if (!keepStatus) status.text = "${stations.size} saved station(s) · Pull down to refresh"
                    showStations()
                    scheduleRefresh()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isCurrent(request)) return@runOnUiThread
                    loading = false
                    listFailed = true
                    refresh.isRefreshing = false
                    if (error is HouseApiException && error.status == 404) {
                        supported = false
                        status.text = "Radio needs house-audio-server v0.10.0 or newer."
                    } else if (!keepStatus) status.text = "Could not load stations: ${friendlyError(error)}"
                    showStations()
                    updateControls()
                    scheduleRefresh()
                }
            }
        }
    }

    private fun scheduleRefresh() {
        handler.removeCallbacksAndMessages(REFRESH_TOKEN)
        if (visible && connected && supported) handler.postAtTime({ loadStations(keepStatus = true) },
            REFRESH_TOKEN, android.os.SystemClock.uptimeMillis() + 30_000)
    }

    private fun addStation() {
        if (!addButton.isEnabled) return
        val url = urlEdit.text.toString().trim()
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            urlEdit.error = "Paste a direct http:// or https:// stream URL."
            return
        }
        hideKeyboard()
        val visibleGeneration = addPlayGuard.capture()
        mutate("Saving station and checking its name…", "Save", { it.addRadioStation(url) }) { station ->
            upsert(station)
            if (urlEdit.text.toString().trim() == url) urlEdit.text.clear()
            status.text = "Saved ${station.name}."
            // A delayed probe must not interrupt a source chosen after leaving this page.
            if (addPlayGuard.canAutoPlay(visibleGeneration)) playStation(station)
        }
    }

    private fun playStation(station: HouseStation) {
        val request = identity() ?: return
        val mediaController = controller ?: return
        if (busy || !supported) return
        busy = true
        updateControls()
        status.text = "Connecting to ${station.name}…"
        val future = mediaController.sendCustomCommand(SessionCommand(HouseRuntime.RADIO_PLAY, Bundle.EMPTY),
            Bundle().apply { putString("station_id", station.id) })
        future.addListener({
            if (!isCurrent(request)) return@addListener
            busy = false
            updateControls()
            if (runCatching { future.get().resultCode == 0 }.getOrDefault(false)) {
                status.text = "Selected ${station.name}."
                syncConnection(mediaController.sessionExtras)
            } else {
                status.text = "Could not confirm playback. Check Now Playing before trying again."
                loadStations(keepStatus = true)
            }
            scheduleRefresh()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun renameStation(station: HouseStation) {
        if (busy || identity() == null) return
        val request = identity() ?: return
        val name = EditText(this).apply {
            setText(station.name)
            setSingleLine(true)
            selectAll()
        }
        val dialog = AlertDialog.Builder(this).setTitle("Station name").setView(name)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = name.text.toString().trim()
                if (value.isBlank()) { name.error = "Enter a station name."; return@setOnClickListener }
                dialog.dismiss()
                if (!isCurrent(request)) return@setOnClickListener
                mutate("Saving station name…", "Rename", { it.renameRadioStation(station.id, value) }) { renamed ->
                    upsert(renamed)
                    status.text = "Saved ${renamed.name}."
                }
            }
        }
        dialog.show()
    }

    private fun deleteStation(station: HouseStation) {
        mutate("Removing ${station.name}…", "Delete", { it.deleteRadioStation(station.id) }) { removed ->
            stations = stations.filterNot { it.id == removed.id }
            showStations()
            status.text = if (removed.id == selectedId) "Removed from saved stations. The current broadcast continues."
                else "Removed ${removed.name}."
            showUndo(removed)
        }
    }

    private fun <T> mutate(message: String, action: String, call: (HouseApi) -> T, success: (T) -> Unit) {
        if (busy || !supported) return
        val current = identity() ?: return
        // Invalidate any older list read before dispatching this single write. Never retry writes.
        generation++
        val request = current.copy(generation = generation)
        loading = false
        refresh.isRefreshing = false
        busy = true
        handler.removeCallbacksAndMessages(REFRESH_TOKEN)
        status.text = message
        updateControls()
        executor.execute {
            try {
                check(request.generation == generation && request.epoch == HouseConnection.epoch && request.endpoint == HouseConnection.current) {
                    "The house connection changed."
                }
                val result = call(HouseApi(applicationContext, request.endpoint))
                runOnUiThread {
                    if (!isCurrent(request)) return@runOnUiThread
                    busy = false
                    updateControls()
                    success(result)
                    scheduleRefresh()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isCurrent(request)) return@runOnUiThread
                    busy = false
                    updateControls()
                    status.text = "$action could not be confirmed: ${friendlyError(error)}\nRefreshing saved stations before you try again."
                    loadStations(keepStatus = true)
                }
            }
        }
    }

    private fun upsert(station: HouseStation) {
        listFailed = false
        val index = stations.indexOfFirst { it.id == station.id }
        stations = if (index < 0) stations + station else stations.toMutableList().also { it[index] = station }
        showStations()
    }

    private fun showUndo(station: HouseStation) {
        clearUndo()
        undoStation = station
        undoIdentity = identity()
        findViewById<TextView>(R.id.radioUndoText).text = "Removed ${station.name}"
        findViewById<View>(R.id.radioUndoPanel).visibility = View.VISIBLE
        handler.postAtTime({ clearUndo() }, UNDO_TOKEN, android.os.SystemClock.uptimeMillis() + 8_000)
        updateControls()
    }

    private fun undoDelete() {
        val station = undoStation ?: return
        val original = undoIdentity ?: return
        // Other station edits can change generation, but undo still belongs to the same Pi/session.
        if (original.epoch != HouseConnection.epoch || original.endpoint != HouseConnection.current) { clearUndo(); return }
        clearUndo()
        mutate("Restoring ${station.name}…", "Restore", { it.addRadioStation(station.url, station.name) }) { restored ->
            upsert(restored)
            status.text = "Restored ${restored.name}."
        }
    }

    private fun clearUndo() {
        handler.removeCallbacksAndMessages(UNDO_TOKEN)
        undoStation = null
        undoIdentity = null
        findViewById<View>(R.id.radioUndoPanel).visibility = View.GONE
    }

    private fun updateControls() {
        val enabled = connected && supported && !busy
        addButton.isEnabled = enabled
        urlEdit.isEnabled = !busy
        refresh.isEnabled = connected && supported && !busy
        findViewById<Button>(R.id.radioUndoButton).isEnabled = enabled
        adapter.update(stations, selectedId, radioStatus, enabled)
    }

    private fun showStations() {
        adapter.update(stations, selectedId, radioStatus, connected && supported && !busy)
        emptyText.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = when {
            !connected -> "Your saved stations will appear when the house server is connected."
            !supported -> "Update the house server to use Radio."
            loading -> "Loading saved stations…"
            listFailed -> "Saved stations unavailable.\nPull down to try again."
            selectedId != null -> "${selectedName.orEmpty().ifBlank { "Radio" }} is selected.\nPaste a link above to save a station."
            else -> "No saved stations yet.\nPaste a stream link above to get started."
        }
    }

    private fun hideKeyboard() {
        urlEdit.clearFocus()
        findViewById<View>(R.id.radioRoot).requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlEdit.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        addPlayGuard.enterPage()
        controller?.let { syncConnection(it.sessionExtras) }
        if (connected && !loading && !busy) loadStations()
        urlEdit.post { if (!isFinishing && !isDestroyed) hideKeyboard() }
    }

    override fun onPause() {
        visible = false
        addPlayGuard.leavePage()
        handler.removeCallbacksAndMessages(REFRESH_TOKEN)
        super.onPause()
    }

    override fun onDestroy() {
        generation++
        handler.removeCallbacksAndMessages(null)
        if (::controllerFuture.isInitialized) MediaController.releaseFuture(controllerFuture)
        controller = null
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun friendlyError(error: Throwable): String = generateSequence(error) { it.cause }
        .mapNotNull { it.message }.lastOrNull()?.take(180) ?: "House server unavailable."

    private class StationAdapter(
        private val play: (HouseStation) -> Unit,
        private val rename: (HouseStation) -> Unit,
        private val delete: (HouseStation) -> Unit
    ) : RecyclerView.Adapter<StationAdapter.Holder>() {
        private var stations = emptyList<HouseStation>()
        private var selectedId: String? = null
        private var status: String? = null
        private var enabled = false

        fun update(items: List<HouseStation>, selected: String?, currentStatus: String?, canEdit: Boolean) {
            if (stations == items && selectedId == selected && status == currentStatus && enabled == canEdit) return
            stations = items
            selectedId = selected
            status = currentStatus
            enabled = canEdit
            notifyDataSetChanged()
        }

        override fun getItemCount() = stations.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.row_radio_station, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val station = stations[position]
            val selected = station.id == selectedId
            val context = holder.itemView.context
            holder.name.text = station.name
            holder.name.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            holder.name.setTextColor(ContextCompat.getColor(context, if (selected) R.color.accent else R.color.white))
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, if (selected) R.color.panel else R.color.black))
            val label = when (status) {
                "playing" -> "LIVE"
                "connecting" -> "Connecting…"
                "retrying" -> "Reconnecting…"
                "paused" -> "Paused · Live radio"
                "error" -> "Stream unavailable"
                else -> "Selected · Live radio"
            }
            val host = runCatching { Uri.parse(station.url).host }.getOrNull().orEmpty()
            holder.detail.text = if (selected) label else host
            holder.play.isEnabled = enabled
            holder.play.isSelected = selected
            holder.play.contentDescription = "${station.name}${if (selected) ", $label" else ""}. Tap to play; hold to rename."
            holder.play.setOnClickListener { play(station) }
            holder.play.setOnLongClickListener { rename(station); true }
            holder.delete.isEnabled = enabled
            holder.delete.contentDescription = "Delete ${station.name}"
            holder.delete.setOnClickListener { delete(station) }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val play: View = view.findViewById(R.id.radioStationPlay)
            val name: TextView = view.findViewById(R.id.radioStationName)
            val detail: TextView = view.findViewById(R.id.radioStationDetail)
            val delete: Button = view.findViewById(R.id.radioStationDelete)
        }
    }

    companion object {
        private val REFRESH_TOKEN = Any()
        private val UNDO_TOKEN = Any()
    }
}

/** A slow Add & Play probe may save after navigation, but must never play after that visit ends. */
internal class RadioAddPlayGuard {
    private var visible = false
    private var visit = 0L
    fun enterPage() { visible = true }
    fun leavePage() { visible = false; visit++ }
    fun capture(): Long = visit
    fun canAutoPlay(requestVisit: Long): Boolean = visible && requestVisit == visit
}
