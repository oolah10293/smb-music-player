package com.housemusic.player.house

import org.json.JSONArray
import org.json.JSONObject

data class HouseTrack(val id: Int, val file: String, val title: String, val artist: String,
                      val album: String, val durationMs: Long, val modified: Long)

data class HouseState(
    val tracks: List<HouseTrack> = emptyList(), val queueVersion: Int = -1,
    val songId: Int = -1, val transport: String = "stop", val positionMs: Long = 0,
    val shuffle: Boolean = false, val repeat: Boolean = false, val ready: Boolean = false,
    val isRadio: Boolean = false, val radioStationId: String = "",
    val radioStationName: String = "", val radioStatus: String = "",
    val radioPlayIntent: String = "stop", val radioError: String = "",
    val supportsSeek: Boolean = true, val supportsSkip: Boolean = true,
    val supportsShuffle: Boolean = true, val supportsRepeat: Boolean = true,
) {
    // MPD rejects Next/Previous/Seek when stopped. Play remains available for a retained queue.
    val canNavigate: Boolean get() = !isRadio && tracks.isNotEmpty() && transport in setOf("play", "pause")
    val canSeek: Boolean get() = canNavigate && supportsSeek
    val canSkip: Boolean get() = canNavigate && supportsSkip
    val canShuffle: Boolean get() = !isRadio && supportsShuffle
    val canRepeat: Boolean get() = !isRadio && supportsRepeat
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
            val source = response.optJSONObject("source")
            val radio = source?.optString("type") == "radio"
            val station = source?.optJSONObject("station")
            val song = m.optJSONObject("song")
            val songId = m.optInt("songId", -1)
            val transport = m.getString("transport")
            val stationName = station.text("name").ifBlank { song.text("stationName") }.ifBlank { "Live radio" }
            val radioStatus = source.text("status").ifBlank {
                when (transport) { "play" -> "playing"; "pause" -> "paused"; else -> "stopped" }
            }
            // ICY titles change without changing MPD's queue revision. Refresh the
            // current radio item from every /state response, not only from /queue.
            val currentTracks = if (radio) {
                val stationUrl = station.text("url")
                val retainedTracks = tracks.ifEmpty {
                    if (stationUrl.isBlank()) emptyList() else listOf(
                        HouseTrack(songId, stationUrl, "", "", "", 0, 0))
                }
                retainedTracks.map { track ->
                    if (song != null && (track.id == songId || track.file == song.text("file"))) {
                        track.copy(title = radioTitle(song.text("title")),
                            artist = song.text("artist").ifBlank { song.text("albumArtist") },
                            album = "", durationMs = 0)
                    } else track.copy(title = radioTitle(track.title), durationMs = 0)
                }
            } else tracks
            return HouseState(
                tracks = currentTracks, queueVersion = m.optInt("queueVersion", -1), songId = songId,
                transport = transport, positionMs = if (radio) 0 else (m.optDouble("elapsedSeconds", 0.0) * 1000).toLong(),
                shuffle = !radio && m.optBoolean("random"), repeat = !radio && m.optBoolean("repeat"),
                ready = response.optJSONObject("sessionPolicy")?.optJSONObject("startup")?.optBoolean("ready") == true,
                isRadio = radio, radioStationId = if (radio) station.text("id") else "",
                radioStationName = if (radio) stationName else "", radioStatus = if (radio) radioStatus else "",
                radioPlayIntent = if (radio) source.text("playIntent").ifBlank {
                    when (radioStatus) { "playing", "connecting", "retrying" -> "play"; "paused" -> "pause"; else -> "stop" }
                } else "stop",
                radioError = if (radio) source.text("lastError") else "",
                supportsSeek = source?.optBoolean("canSeek", true) ?: true,
                supportsSkip = source?.optBoolean("canSkip", true) ?: true,
                supportsShuffle = source?.optBoolean("canShuffle", true) ?: true,
                supportsRepeat = source?.optBoolean("canRepeat", true) ?: true,
            )
        }

        private fun JSONObject?.text(name: String): String =
            if (this == null || isNull(name)) "" else optString(name).trim()

        private fun radioTitle(title: String): String = title.trim().takeUnless {
            it.all { character -> character.isWhitespace() || character in "-–—" }
        }.orEmpty()
    }
}
