package com.smbmusic.player.radio

import org.json.JSONObject

data class HouseStationBookmark(val id: String, val name: String, val url: String)

/** A complete, validated snapshot. A bad response must never erase the last good cache. */
object HouseStationSnapshot {
    fun parse(text: String): List<HouseStationBookmark> {
        val root = JSONObject(text)
        require(root.optString("service") == "house-audio-server") { "Unexpected House service" }
        val rows = root.getJSONArray("stations")
        require(rows.length() <= 2000) { "Station list too large" }
        val ids = mutableSetOf<String>()
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val id = row.getString("id")
            require(id.isNotBlank() && ids.add(id)) { "Invalid station identity" }
            val url = RadioUrl.normalize(row.getString("url"))
            HouseStationBookmark(id, row.getString("name").trim().take(160).ifBlank { RadioUrl.fallbackName(url) }, url)
        }.distinctBy { it.url }
    }

    /** House owns its cached bookmarks; phone-created stations, aliases and listening totals stay local. */
    fun reconcile(current: List<RadioStation>, remote: List<HouseStationBookmark>, hidden: Set<String>): List<RadioStation> {
        val remaining = current.toMutableList()
        val merged = mutableListOf<RadioStation>()
        for (bookmark in remote) {
            if (bookmark.id in hidden) continue
            val matches = remaining.filter { it.houseId == bookmark.id || it.url == bookmark.url }
            val existing = matches.firstOrNull { it.houseId == bookmark.id } ?: matches.firstOrNull()
            val alias = matches.firstOrNull { it.renamed }
            remaining.removeAll(matches.toSet())
            merged += RadioStation(existing?.id ?: "house:${bookmark.id}", alias?.name ?: bookmark.name,
                bookmark.url, matches.sumOf { it.listeningMs }, alias != null,
                bookmark.id, matches.any { it.local })
        }
        // Drop only bookmarks removed from House. Standalone additions retain their identity and URL.
        merged += remaining.filter { it.local }.map { it.copy(houseId = null) }
        return merged.distinctBy { it.url }
    }
}
