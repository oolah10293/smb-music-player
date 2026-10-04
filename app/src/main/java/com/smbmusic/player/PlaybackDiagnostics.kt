package com.smbmusic.player

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bounded diagnostic events only: callers must never supply credentials, URLs or filenames. */
internal object PlaybackDiagnostics {
    @Synchronized fun record(context: Context, event: String) {
        val prefs = context.getSharedPreferences("playback_diagnostics", Context.MODE_PRIVATE)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val rows = prefs.getString("events", "").orEmpty().lines().filter { it.isNotBlank() }
        prefs.edit().putString("events", (rows + "$time $event").takeLast(50).joinToString("\n")).apply()
        Log.i("SMBMusic", event)
    }
    fun show(context: Context) {
        val text = context.getSharedPreferences("playback_diagnostics", Context.MODE_PRIVATE)
            .getString("events", "No events recorded yet.")
        AlertDialog.Builder(context).setTitle("SMB Music connection log")
            .setMessage(text).setPositiveButton("Close", null).show()
    }
}
