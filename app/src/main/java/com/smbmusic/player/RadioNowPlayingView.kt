package com.smbmusic.player

import android.os.Bundle
import android.view.View
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

internal object RadioNowPlayingView {
    fun bind(root: View, extras: Bundle) {
        val station = extras.getString("stationName").orEmpty().ifBlank { "Live radio" }
        text(root, R.id.radioDetailStation, station)
        text(root, R.id.radioDetailTitle, extras.getString("radioTitle").orEmpty().ifBlank { "Song details unavailable" })
        text(root, R.id.radioDetailArtist, extras.getString("radioArtist").orEmpty())
        text(root, R.id.radioDetailAlbum, extras.getString("radioAlbum").orEmpty())
        text(root, R.id.radioDetailBroadcast, extras.getString("broadcastName").orEmpty().takeUnless { it == station }.orEmpty())
        text(root, R.id.radioDetailQuality, extras.getString("radioQuality").orEmpty())
        text(root, R.id.radioLastTitle, extras.getString("lastTitle").orEmpty().ifBlank { "No previous song yet" })
        text(root, R.id.radioLastArtist, extras.getString("lastArtist").orEmpty())
        text(root, R.id.radioLastAlbum, extras.getString("lastAlbum").orEmpty())
        val time = extras.getLong("lastTime")
        text(root, R.id.radioLastStationTime, if (time > 0) extras.getString("lastStation").orEmpty() + " · " +
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
            else "Songs are kept after more than 10 seconds of playback.")
        text(root, R.id.radioHistoryError, "")
    }
    private fun text(root: View, id: Int, value: String) {
        root.findViewById<TextView>(id).apply { text = value; visibility = if (value.isBlank()) View.GONE else View.VISIBLE }
    }
}
