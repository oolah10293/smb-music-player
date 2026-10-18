package com.smbmusic.player.radio

import java.net.URI
import java.util.Locale

data class RadioStation(val id: String, val name: String, val url: String,
                        val listeningMs: Long = 0, val renamed: Boolean = false,
                        val houseId: String? = null, val local: Boolean = true)

object RadioUrl {
    fun normalize(raw: String): String {
        val uri = try { URI(raw.trim()) } catch (_: Exception) {
            throw IllegalArgumentException("Paste a valid direct HTTP or HTTPS stream URL.")
        }
        require(uri.scheme?.lowercase(Locale.ROOT) in listOf("http", "https") && !uri.host.isNullOrBlank()) {
            "Paste a direct HTTP or HTTPS stream URL."
        }
        require(uri.rawUserInfo == null && uri.rawFragment == null && uri.port in -1..65535) {
            "Stream URLs must not contain credentials or fragments."
        }
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        require(!path.endsWith(".pls") && !path.endsWith(".m3u")) {
            "Use the direct stream link, or an HLS .m3u8 link, rather than a PLS/M3U directory."
        }
        // Preserve escaped paths and query tokens exactly; normalize only scheme/host/default port.
        val scheme = uri.scheme.lowercase(Locale.ROOT)
        val host = uri.host.lowercase(Locale.ROOT)
        val port = uri.port.takeUnless { it == -1 || (scheme == "http" && it == 80) || (scheme == "https" && it == 443) }
        return "$scheme://$host${port?.let { ":$it" }.orEmpty()}${uri.rawPath.orEmpty().ifBlank { "/" }}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
    }
    fun fallbackName(url: String): String = "Station — ${URI(url).host}"
}

class RadioStationOrder {
    private val ids = mutableListOf<String>()
    fun reset() = ids.clear()
    fun added(id: String) { ids.remove(id); ids.add(0, id) }
    fun refresh(ranked: List<RadioStation>): List<RadioStation> {
        val rows = ranked.associateBy { it.id }
        ranked.forEach { if (it.id !in ids) ids += it.id }
        return ids.mapNotNull { rows[it] }
    }
}

data class RadioSong(val title: String, val artist: String = "", val album: String = "",
                     val identified: Boolean = false)
data class PreviousRadioSong(val song: RadioSong, val station: String, val startedAt: Long)

/** Counts advancing playback, never wall time spent paused/buffering or a seek jump. */
class RadioHistory {
    private var candidate: PreviousRadioSong? = null
    private var playedMs = 0L
    var previous: PreviousRadioSong? = null
        private set
    fun restore(previous: PreviousRadioSong?) { this.previous = previous }
    fun update(song: RadioSong?, station: String, now: Long) {
        if (candidate?.song == song && candidate?.station == station) return
        finish()
        candidate = song?.takeIf { it.identified && it.title.isNotBlank() }
            ?.let { PreviousRadioSong(it, station, now) }
        playedMs = 0
    }
    fun advance(deltaMs: Long) { if (candidate != null) playedMs += deltaMs.coerceIn(0, 5_000) }
    fun finish() {
        if (playedMs > 10_000 && candidate != null) previous = candidate
        candidate = null; playedMs = 0
    }
}
