package com.housemusic.player

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.housemusic.player.house.HouseRuntime
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Radio-only content scrolls above the fixed mute/sync/transport controls. */
@UnstableApi
internal object RadioNowPlayingView {
    fun bind(root: View, extras: Bundle) {
        val details = extras.getBundle(HouseRuntime.EXTRA_RADIO_DETAILS) ?: Bundle.EMPTY
        val station = extras.getString(HouseRuntime.EXTRA_RADIO_STATION_NAME).orEmpty().ifBlank { "Live radio" }
        text(root, R.id.radioDetailStation, station)
        text(root, R.id.radioDetailTitle, details.getString("title").orEmpty().ifBlank { "Song details unavailable" })
        text(root, R.id.radioDetailArtist, details.getString("artist").orEmpty())
        text(root, R.id.radioDetailAlbum, details.getString("album").orEmpty())
        text(root, R.id.radioDetailBroadcast, details.getString("broadcastName").orEmpty().takeUnless { it == station }.orEmpty())
        text(root, R.id.radioDetailQuality, quality(details.getInt("bitrate"), details.getString("audio").orEmpty()))
        root.findViewById<View>(R.id.radioLastPlayedPanel).visibility =
            if (details.getBoolean("historySupported")) View.VISIBLE else View.GONE
        val last = details.getBundle("lastPlayed")
        text(root, R.id.radioLastTitle, last?.getString("title").orEmpty().ifBlank { "No previous song yet" })
        text(root, R.id.radioLastArtist, last?.getString("artist").orEmpty())
        text(root, R.id.radioLastAlbum, last?.getString("album").orEmpty())
        val started = last?.getLong("startedAtMillis") ?: 0L
        val time = if (started > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(started)) else ""
        text(root, R.id.radioLastStationTime, if (last == null) "Songs are kept after more than 10 seconds of playback."
            else listOf(last.getString("station").orEmpty(), time).filter(String::isNotBlank).joinToString(" · "))
        text(root, R.id.radioHistoryError, if (details.getBoolean("historySaveError")) "Last played could not be saved on the Pi." else "")
    }

    private fun text(root: View, id: Int, value: String) {
        root.findViewById<TextView>(id).apply {
            text = value
            visibility = if (value.isBlank()) View.GONE else View.VISIBLE
        }
    }

    internal fun quality(bitrate: Int, audio: String): String {
        val fields = audio.split(':')
        val rate = fields.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 }
        val bits = fields.getOrNull(1).orEmpty()
        val channels = when (val count = fields.getOrNull(2)?.toIntOrNull()) {
            1 -> "Mono"
            2 -> "Stereo"
            null -> ""
            else -> if (count > 0) "$count channels" else ""
        }
        return listOf(if (bitrate > 0) "$bitrate kbps" else "",
            rate?.let { String.format(Locale.getDefault(), "%.1f kHz", it / 1000.0) }.orEmpty(),
            if (bits == "f") "Float" else bits.toIntOrNull()?.takeIf { it > 0 }?.let { "$it-bit" }.orEmpty(),
            channels).filter(String::isNotBlank).joinToString(" · ")
    }
}
