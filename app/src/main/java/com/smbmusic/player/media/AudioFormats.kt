package com.smbmusic.player.media

object AudioFormats {
    private val supportedExtensions = setOf(
        "mp3",
        "flac",
        "m4a",
        "mp4",
        "aac",
        "ogg",
        "opus",
        "wav"
    )

    fun isSupported(filename: String): Boolean = extension(filename) in supportedExtensions

    fun extension(filename: String): String =
        filename.substringAfterLast('.', "").lowercase()

    fun typeLabel(filename: String): String =
        extension(filename).uppercase().ifBlank { "AUDIO" }

    fun titleFromFilename(filename: String): String {
        val ext = extension(filename)
        return if (ext.isBlank()) filename else filename.dropLast(ext.length + 1)
    }
}
