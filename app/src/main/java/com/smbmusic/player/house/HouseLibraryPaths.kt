package com.smbmusic.player.house

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Explicit music-root mapping; a share root is never guessed to be MPD's root. */
object HouseLibraryPaths {
    private data class Location(val host: String, val port: Int, val parts: List<String>)

    private fun segment(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        .also { require(it.isNotEmpty() && it !in listOf(".", "..") && !it.contains('/') &&
            !it.contains('\\') && it.none { c -> c.code < 32 }) { "Invalid music path" } }
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun location(value: String): Location {
        val uri = URI(value)
        require(uri.scheme.equals("smb", true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Use an SMB host/share/music-folder path, without credentials or a query"
        }
        val parts = uri.rawPath.orEmpty().trim('/').split('/').map(::segment)
        require(parts.isNotEmpty()) { "Include the SMB share and music folder" }
        return Location(uri.host.lowercase(), uri.port, parts)
    }

    fun normalizeRoot(input: String): String {
        if (input.isBlank()) return ""
        val raw = input.trim().replace('\\', '/').replace(Regex("^smb://", RegexOption.IGNORE_CASE), "").trimStart('/')
        val host = raw.substringBefore('/')
        val parts = raw.substringAfter('/', "").trimEnd('/').split('/').map(::segment)
        val encoded = "smb://$host/${parts.joinToString("/") { encode(it) }}/"
        location(encoded)
        return encoded
    }

    fun relative(root: String, smbFile: String): String? = runCatching {
        require(root.isNotBlank() && smbFile.startsWith("smb://", true))
        val base = location(normalizeRoot(root))
        // jcifs may expose a URL with literal spaces. Normalize individual segments, never
        // decode the whole path (which would turn an encoded slash into a false separator).
        val file = location(normalizeRoot(smbFile))
        require(base.host == file.host && base.port == file.port && file.parts.size > base.parts.size)
        require(file.parts.take(base.parts.size) == base.parts)
        file.parts.drop(base.parts.size).joinToString("/")
    }.getOrNull()

    fun smb(root: String, relative: String): String? = runCatching {
        require(root.isNotBlank())
        val normalized = normalizeRoot(root)
        location(normalized)
        require(relative.isNotBlank() && !relative.startsWith('/') && !relative.endsWith('/'))
        val parts = relative.split('/').onEach {
            require(it.isNotBlank() && it !in listOf(".", "..") && !it.contains('\\') &&
                it.none { c -> c.code < 32 })
        }
        normalized.trimEnd('/') + "/" + parts.joinToString("/") { encode(it) }
    }.getOrNull()
}
