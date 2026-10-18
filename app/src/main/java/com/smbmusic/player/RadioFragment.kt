package com.smbmusic.player

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.common.util.concurrent.ListenableFuture
import com.smbmusic.player.radio.*

@UnstableApi
class RadioFragment : SmbPageFragment(R.layout.activity_radio, 2) {
    private lateinit var store: RadioStore
    private lateinit var future: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val order = RadioStationOrder()
    private val handler = Handler(Looper.getMainLooper())
    private var rows = emptyList<RadioStation>()
    private var deleted: RadioStation? = null
    private lateinit var listAdapter: Stations
    private val expireUndo = Runnable { deleted = null; if (view != null) findViewById<View>(R.id.radioUndoPanel).visibility = View.GONE }
    private val listener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) { if (view != null) refresh() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = RadioStore(pageContext)
        listAdapter = Stations()
        findViewById<RecyclerView>(R.id.radioStationList).apply {
            layoutManager = LinearLayoutManager(pageContext); adapter = listAdapter
        }
        findViewById<SwipeRefreshLayout>(R.id.radioRefresh).setOnRefreshListener {
            refresh(); findViewById<SwipeRefreshLayout>(R.id.radioRefresh).isRefreshing = false
        }
        findViewById<Button>(R.id.radioAddButton).setOnClickListener {
            val input = findViewById<EditText>(R.id.radioUrlEdit)
            try {
                val station = store.add(input.text.toString())
                order.added(station.id); input.text.clear(); input.clearFocus()
                (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(input.windowToken, 0)
                refresh(); play(station)
            } catch (e: Exception) { status(e.message ?: "Could not save station.") }
        }
        findViewById<EditText>(R.id.radioUrlEdit).setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                findViewById<Button>(R.id.radioAddButton).performClick(); true
            } else false
        }
        findViewById<Button>(R.id.radioUndoButton).setOnClickListener {
            deleted?.let { station ->
                try { store.put(station); handler.removeCallbacks(expireUndo); expireUndo.run(); refresh() }
                catch (_: Exception) { status("Could not restore station.") }
            }
        }
        findViewById<TextView>(R.id.radioStatus).setOnLongClickListener { PlaybackDiagnostics.show(pageContext); true }
        refresh()
        future = MediaController.Builder(pageContext, SessionToken(pageContext, ComponentName(pageContext, PlaybackService::class.java)))
            .setListener(listener).buildAsync()
        future.addListener({
            if (isDestroyed) return@addListener
            runCatching { controller = future.get(); refresh() }.onFailure { status("Playback service unavailable.") }
        }, ContextCompat.getMainExecutor(pageContext))
    }

    override fun onResume() { super.onResume(); order.reset(); refresh() }
    private fun status(message: String) { if (view != null) findViewById<TextView>(R.id.radioStatus).text = message }
    private fun refresh() {
        if (view == null || !::store.isInitialized) return
        rows = order.refresh(store.stations()); listAdapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.radioEmptyText).apply {
            visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            text = "Add a direct station stream link to start listening."
        }
        val extras = controller?.sessionExtras
        status(if (extras?.getBoolean(PlaybackService.EXTRA_RADIO) == true)
            extras.getString("radioStatus", "Connecting") else "${rows.size} saved station(s)")
    }
    private fun play(station: RadioStation) {
        val c = controller ?: run { status("Playback service is connecting — tap again."); return }
        val result = c.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_RADIO_PLAY, Bundle.EMPTY),
            Bundle().apply { putString("stationId", station.id) })
        result.addListener({
            if (isDestroyed) return@addListener
            if (isSelectedPage && runCatching { result.get().resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS }.getOrDefault(false))
                refresh()
            else if (isSelectedPage) status("Could not play the selected station.")
        }, ContextCompat.getMainExecutor(pageContext))
    }
    private fun rename(station: RadioStation) {
        val edit = EditText(pageContext).apply { setSingleLine(); setText(station.name); selectAll() }
        val dialog = AlertDialog.Builder(pageContext).setTitle("Rename station").setView(edit)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { store.rename(station.id, edit.text.toString()); refresh(); dialog.dismiss() }
            catch (e: Exception) { edit.error = e.message ?: "Could not rename station." }
        } }
        dialog.show()
    }
    private fun delete(station: RadioStation) {
        try {
            store.delete(station.id); deleted = station; refresh()
            findViewById<View>(R.id.radioUndoPanel).visibility = View.VISIBLE
            findViewById<TextView>(R.id.radioUndoText).text = "Deleted ${station.name}"
            handler.removeCallbacks(expireUndo); handler.postDelayed(expireUndo, 8_000)
        } catch (_: Exception) { status("Could not delete station.") }
    }
    private inner class Stations : RecyclerView.Adapter<StationHolder>() {
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = StationHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.row_radio_station, parent, false))
        override fun onBindViewHolder(holder: StationHolder, position: Int) {
            val station = rows[position]
            val active = controller?.sessionExtras?.let { it.getBoolean(PlaybackService.EXTRA_RADIO) && it.getString("stationId") == station.id } == true
            holder.itemView.apply {
                setBackgroundColor(ContextCompat.getColor(context, if (active) R.color.panel2 else R.color.black))
                findViewById<TextView>(R.id.radioStationName).text = station.name
                findViewById<TextView>(R.id.radioStationDetail).text = if (active) "Selected · ${station.listeningMs / 60_000} min listened" else "${station.listeningMs / 60_000} min listened"
                findViewById<View>(R.id.radioStationPlay).apply {
                    setOnClickListener { play(station) }; setOnLongClickListener { rename(station); true }
                }
                findViewById<Button>(R.id.radioStationDelete).setOnClickListener { delete(station) }
            }
        }
    }
    private class StationHolder(view: View) : RecyclerView.ViewHolder(view)
    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        if (::future.isInitialized) MediaController.releaseFuture(future)
        controller = null; deleted = null
        super.onDestroyView()
    }
}
