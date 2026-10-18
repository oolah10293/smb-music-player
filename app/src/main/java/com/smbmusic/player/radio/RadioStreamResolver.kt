package com.smbmusic.player.radio

import java.net.HttpURLConnection
import java.net.URL

/** Bounded sniff supports extensionless HLS and redirects without fetching a station website in a WebView. */
object RadioStreamResolver {
    fun resolve(raw: String): String? {
        var url = RadioUrl.normalize(raw)
        repeat(6) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000; connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "SMBMusic/0.6.0")
                connection.setRequestProperty("Icy-MetaData", "1")
                val code = connection.responseCode
                if (code in 300..399) {
                    url = RadioUrl.normalize(URL(URL(url), connection.getHeaderField("Location") ?: error("Missing redirect")).toString())
                } else {
                    require(code in 200..299) { "Station unavailable" }
                    val type = connection.contentType.orEmpty().substringBefore(';').lowercase()
                    require(type !in listOf("text/html", "application/xhtml+xml", "audio/x-scpls")) { "Use a direct stream URL" }
                    if (type.startsWith("audio/") && !type.contains("mpegurl")) return type
                    val bytes = ByteArray(2048)
                    val count = connection.inputStream.use { it.read(bytes) }.coerceAtLeast(0)
                    val head = String(bytes, 0, count, Charsets.UTF_8).trimStart()
                    if (head.startsWith("#EXTM3U") && head.contains("#EXT-X-")) return "application/x-mpegURL"
                    require(!head.startsWith("#EXTM3U") && !head.startsWith("[playlist]", true) && !head.startsWith("<!DOCTYPE", true) && !head.startsWith("<html", true)) { "Use a direct stream URL" }
                    return null // Let the extractor identify MP3/AAC when the server omits a MIME type.
                }
            } finally { connection.disconnect() }
        }
        throw IllegalArgumentException("Too many stream redirects")
    }
}
