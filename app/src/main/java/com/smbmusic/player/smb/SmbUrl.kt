package com.smbmusic.player.smb

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object SmbUrl {
    fun normalize(input: String): String {
        val raw = input.trim().replace('\', '/').removePrefix("smb://")
        val parts = raw.split('/').filter { it.isNotBlank() }
        require(parts.size >= 2) { "Use host/share or host/share/folder" }

        val host = parts.first()
        val encodedPath = parts.drop(1).joinToString("/") { encodeSegment(decodeSegment(it)) }
        return "smb://$host/$encodedPath/"
    }

    fun parent(current: String, root: String): String? {
        val c = ensureSlash(current)
        val r = ensureSlash(root)
        if (c == r) return null
        if (!c.startsWith(r)) return r

        val trimmed = c.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        if (idx < 0) return r
        val parent = trimmed.substring(0, idx + 1)
        return if (parent.length < r.length) r else parent
    }

    fun display(url: String): String {
        return url.removePrefix("smb://")
            .split('/')
            .joinToString("/") { decodeSegment(it) }
    }

    private fun ensureSlash(value: String): String = if (value.endsWith('/')) value else "$value/"

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun decodeSegment(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}
