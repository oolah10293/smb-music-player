package com.smbmusic.player.radio

/** Phone-side equivalent of the House parser's observed ICY envelopes. No URL tails displayed. */
object RadioMetadata {
    private val envelope = Regex("^\\s*(?:title|artist|album|text)\\s*=\\s*[\"']", RegexOption.IGNORE_CASE)
    private val prefix = Regex("^\\s*(.*?)\\s+[-|]\\s+(?=text\\s*=\\s*[\"'])", RegexOption.IGNORE_CASE)
    private val key = Regex("\\s*([a-z][a-z0-9_]*)\\s*=\\s*([\"'])", RegexOption.IGNORE_CASE)
    private val boundary = Regex("\\s*[,;]?\\s*(?=[a-z][a-z0-9_]*\\s*=)", RegexOption.IGNORE_CASE)
    private val end = Regex("\\s*[,;]?\\s*$")
    private val spot = Regex("(?:^|[\\s,;\"'])song_spot\\s*=\\s*([\"'])([a-z])\\1", RegexOption.IGNORE_CASE)
    fun parse(raw: String, artist: String = "", album: String = ""): RadioSong {
        if (raw.length > 16_384) return RadioSong("")
        val pref = prefix.find(raw)
        if (!envelope.containsMatchIn(raw) && pref == null) {
            val pieces = raw.split(" - ", limit = 2)
            return if (artist.isBlank() && pieces.size == 2 && pieces.all { it.isNotBlank() })
                RadioSong(pieces[1].trim(), pieces[0].trim(), album, true)
            else RadioSong(raw.trim(), artist, album, artist.isNotBlank())
        }
        val fields = mutableMapOf<String, String>()
        var position = pref?.range?.last?.plus(1) ?: 0
        while (position < raw.length) {
            val match = key.find(raw, position)?.takeIf { it.range.first == position } ?: break
            val name = match.groupValues[1].lowercase()
            if (name !in setOf("title", "artist", "album", "text")) break
            val quote = match.groupValues[2][0]
            val start = match.range.last + 1
            var cursor = start
            var found = false
            while (cursor < raw.length) {
                if (raw[cursor] == '\\' && cursor + 1 < raw.length) { cursor += 2; continue }
                if (raw[cursor] == quote) {
                    val tail = cursor + 1
                    val separator = boundary.find(raw, tail)?.takeIf { it.range.first == tail }
                    if (separator != null || end.matches(raw.substring(tail))) {
                        fields.putIfAbsent(name, raw.substring(start, cursor)
                            .replace(Regex("\\\\([\\\\\"'])"), "$1").replace("$quote$quote", "$quote").trim())
                        position = separator?.range?.last?.plus(1) ?: raw.length
                        found = true; break
                    }
                }
                cursor++
            }
            if (!found) break
        }
        val marker = spot.find(raw.substring(position))?.groupValues?.get(2)?.uppercase()
        val title = fields["title"] ?: fields["text"].orEmpty()
        val parsedArtist = artist.ifBlank { fields["artist"] ?: pref?.groupValues?.get(1).orEmpty() }
        if (marker in listOf("T", "O")) return RadioSong(pref?.groupValues?.get(1).orEmpty().ifBlank { title })
        return RadioSong(title, parsedArtist, album.ifBlank { fields["album"].orEmpty() },
            title.isNotBlank() && (parsedArtist.isNotBlank() || marker in listOf("M", "F")))
    }
}
