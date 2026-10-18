package com.smbmusic.player.storage

import com.smbmusic.player.radio.RadioUrl
import org.json.JSONArray
import org.json.JSONObject

/** Android-independent retained document, including duplicate queue entries and shuffle traversal. */
data class SessionItem(val uri: String, val id: String = uri, val mime: String = "",
    val title: String = "", val artist: String = "", val album: String = "", val filename: String = "", val modified: Long = 0)
data class SessionRecord(val items: List<SessionItem>, val index: Int, val positionMs: Long,
    val shuffle: Boolean, val stopped: Boolean, val shouldPlay: Boolean = !stopped,
    val radio: Boolean = false, val stationId: String = "", val stationName: String = "",
    val playlist: SessionRecord? = null, val shuffleOrder: List<Int> = emptyList(), val bluetoothRequired: Boolean = false) {
    fun encode(): JSONObject = JSONObject().put("version", 2).put("items", JSONArray(items.map {
        JSONObject().put("uri", it.uri).put("id", it.id).put("mime", it.mime).put("title", it.title)
            .put("artist", it.artist).put("album", it.album).put("filename", it.filename).put("modified", it.modified)
    })).put("index", index).put("positionMs", positionMs).put("shuffle", shuffle).put("explicitlyStopped", stopped)
        .put("bluetoothRequired", bluetoothRequired).put("shouldPlay", shouldPlay).put("radio", radio).put("stationId", stationId).put("stationName", stationName)
        .put("shuffleOrder", JSONArray(shuffleOrder)).apply { playlist?.let { put("playlist", it.copy(playlist = null).encode()) } }

    companion object {
        fun decode(json: JSONObject, nested: Boolean = false): SessionRecord {
            require(json.optInt("version", 1) in 1..2)
            require(!nested || !json.optBoolean("radio"))
            val radio = json.optBoolean("radio")
            val rows = json.getJSONArray("items")
            val items = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i); val uri = row.getString("uri")
                if (radio) RadioUrl.normalize(uri) else require(uri.startsWith("smb://"))
                SessionItem(uri, row.optString("id", uri), row.optString("mime"), row.optString("title"),
                    row.optString("artist"), row.optString("album"), row.optString("filename"), row.optLong("modified"))
            }
            require(items.isNotEmpty() && (!radio || items.size == 1))
            val order = json.optJSONArray("shuffleOrder")?.let { a -> (0 until a.length()).map { a.getInt(it) } }.orEmpty()
            val validOrder = order.takeIf { it.size == items.size && it.toSet() == items.indices.toSet() }.orEmpty()
            val stopped = json.optBoolean("explicitlyStopped", true)
            return SessionRecord(items, json.optInt("index").coerceIn(items.indices), json.optLong("positionMs").coerceAtLeast(0),
                json.optBoolean("shuffle"), stopped, json.optBoolean("shouldPlay", !stopped) && !stopped,
                radio, json.optString("stationId"), json.optString("stationName"),
                if (nested) null else json.optJSONObject("playlist")?.let { decode(it, true) }, validOrder, json.optBoolean("bluetoothRequired"))
        }
    }
}
