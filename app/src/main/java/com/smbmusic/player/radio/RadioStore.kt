package com.smbmusic.player.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** All reads re-read preferences so the page and service never overwrite each other's counters. */
class RadioStore(context: Context) {
    private val prefs = context.getSharedPreferences("standalone_radio", Context.MODE_PRIVATE)
    fun stations(): List<RadioStation> = synchronized(LOCK) {
        val rows = runCatching { JSONArray(prefs.getString("stations", "[]")) }.getOrDefault(JSONArray())
        (0 until rows.length()).mapNotNull { i -> runCatching {
            val o = rows.getJSONObject(i)
            RadioStation(o.getString("id"), o.getString("name"), RadioUrl.normalize(o.getString("url")),
                o.optLong("listeningMs").coerceAtLeast(0), o.optBoolean("renamed"),
                o.optString("houseId").takeIf { it.isNotBlank() }, o.optBoolean("local", true))
        }.getOrNull() }.sortedWith(compareByDescending<RadioStation> { it.listeningMs }.thenBy { it.name.lowercase() })
    }
    fun add(raw: String): RadioStation = synchronized(LOCK) {
        val url = RadioUrl.normalize(raw)
        stations().firstOrNull { it.url == url }?.copy(local = true)?.also { put(it) }
            ?: RadioStation(UUID.randomUUID().toString(), RadioUrl.fallbackName(url), url)
            .also { save(stations() + it) }
    }
    fun put(station: RadioStation) = synchronized(LOCK) {
        val rows = stations()
        if (rows.none { it.url == station.url && it.id != station.id }) {
            save(rows.filterNot { it.id == station.id } + station, hiddenHouseIds() - setOfNotNull(station.houseId))
        }
    }
    fun delete(id: String) = synchronized(LOCK) {
        val rows = stations()
        save(rows.filterNot { it.id == id }, hiddenHouseIds() + setOfNotNull(rows.find { it.id == id }?.houseId))
    }
    fun rename(id: String, name: String) = synchronized(LOCK) {
        require(name.trim().isNotEmpty()) { "Enter a station name." }
        save(stations().map { if (it.id == id) it.copy(name = name.trim().take(160), renamed = true) else it })
    }
    fun broadcastName(id: String, name: String) = synchronized(LOCK) {
        if (name.isNotBlank()) save(stations().map { if (it.id == id && !it.renamed && it.houseId == null) it.copy(name = name.trim().take(160)) else it })
    }
    fun addListening(id: String, ms: Long) = synchronized(LOCK) {
        if (ms > 0) save(stations().map { if (it.id == id) it.copy(listeningMs = it.listeningMs + ms) else it })
    }
    private fun hiddenHouseIds(): Set<String> = prefs.getStringSet("hiddenHouseIds", emptySet()).orEmpty().toSet()
    fun refreshHouse(remote: List<HouseStationBookmark>) = synchronized(LOCK) {
        val hidden = hiddenHouseIds().intersect(remote.map { it.id }.toSet())
        save(HouseStationSnapshot.reconcile(stations(), remote, hidden), hidden)
    }
    private fun save(rows: List<RadioStation>, hidden: Set<String> = hiddenHouseIds()) {
        val json = JSONArray(rows.map { JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)
            .put("listeningMs", it.listeningMs).put("renamed", it.renamed)
            .put("houseId", it.houseId.orEmpty()).put("local", it.local) })
        check(prefs.edit().putString("stations", json.toString()).putStringSet("hiddenHouseIds", hidden).commit()) { "Could not save stations." }
    }
    fun previous(): PreviousRadioSong? = runCatching {
        val o = JSONObject(prefs.getString("previous", "")!!)
        PreviousRadioSong(RadioSong(o.getString("title"), o.optString("artist"), o.optString("album"), true),
            o.getString("station"), o.getLong("startedAt"))
    }.getOrNull()
    fun savePrevious(previous: PreviousRadioSong?) {
        if (previous == null) return
        val value = JSONObject().put("title", previous.song.title).put("artist", previous.song.artist)
            .put("album", previous.song.album).put("station", previous.station).put("startedAt", previous.startedAt)
        prefs.edit().putString("previous", value.toString()).apply()
    }
    companion object { private val LOCK = Any() }
}
