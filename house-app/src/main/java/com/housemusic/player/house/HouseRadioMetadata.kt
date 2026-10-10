package com.housemusic.player.house

import android.os.Bundle

/** Fresh session extras, independent of Media3's cached queue/timeline metadata. */
internal fun HouseState.radioDetailsBundle(): Bundle = Bundle().apply {
    putString("title", radioTitle)
    putString("artist", radioArtist)
    putString("album", radioAlbum)
    putString("broadcastName", radioBroadcastName)
    putInt("bitrate", radioBitrate)
    putString("audio", radioAudio)
    putBoolean("historySupported", radioHistorySupported)
    putBoolean("historySaveError", radioHistoryError.isNotBlank())
    lastRadioSong?.let { song ->
        putBundle("lastPlayed", Bundle().apply {
            putString("title", song.title)
            putString("artist", song.artist)
            putString("album", song.album)
            putString("station", song.stationName)
            putLong("startedAtMillis", (song.startedAtEpoch * 1000).toLong())
        })
    }
}
