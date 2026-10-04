package com.housemusic.player

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bounded local event log; no credentials, paths, track titles, or network addresses. */
object HouseDiagnostics {
    @Synchronized fun record(context: Context, event: String) {
        val prefs = context.getSharedPreferences("house_diagnostics", Context.MODE_PRIVATE)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val lines = prefs.getString("events", "").orEmpty().lines().filter { it.isNotBlank() }
        prefs.edit().putString("events", (lines + "$time  $event").takeLast(50).joinToString("\n")).apply()
        Log.i("HouseMusic", event)
    }
    fun show(context: Context) {
        val text = context.getSharedPreferences("house_diagnostics", Context.MODE_PRIVATE)
            .getString("events", "No connection events recorded yet.")
        AlertDialog.Builder(context).setTitle("House Music 0.1.1 · connection log")
            .setMessage(text).setPositiveButton("Close", null).show()
    }
}
