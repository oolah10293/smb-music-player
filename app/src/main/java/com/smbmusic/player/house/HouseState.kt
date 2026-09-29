package com.smbmusic.player.house

import org.json.JSONArray
import org.json.JSONObject

data class HouseTrack(val id: Int, val file: String, val title: String, val artist: String,
                      val album: String, val durationMs: Long, val modified: Long)

data class HouseState(
    val tracks: List<HouseTrack> = emptyList(), val queueVersion: Int = -1,
    val songId: Int = -1, val transport: String = "stop", val positionMs: Long = 0,
    val shuffle: Boolean = false, val repeat: Boolean = false, val ready: Boolean = false,
) {
    val index: Int get() = tracks.indexOfFirst { it.id == songId }.coerceAtLeast(0)
    companion object {
        fun tracks(array: JSONArray): List<HouseTrack> = (0 until array.length()).map { i ->
            val t = array.getJSONObject(i)
            fun text(name: String): String = if (t.isNull(name)) "" else t.optString(name)
            HouseTrack(t.getInt("id"), t.getString("file"), text("title"), text("artist").ifBlank { text("albumArtist") },
                text("album"), (t.optDouble("durationSeconds", 0.0) * 1000).toLong(), HouseApi.modifiedTime(text("lastModified")))
        }
        fun parse(response: JSONObject, tracks: List<HouseTrack>): HouseState {
            val m = response.getJSONObject("mpd")
            return HouseState(tracks, m.optInt("queueVersion", -1), m.optInt("songId", -1),
                m.getString("transport"), (m.optDouble("elapsedSeconds", 0.0) * 1000).toLong(),
                m.optBoolean("random"), m.optBoolean("repeat"),
                response.optJSONObject("sessionPolicy")?.optJSONObject("startup")?.optBoolean("ready") == true)
        }
    }
}
