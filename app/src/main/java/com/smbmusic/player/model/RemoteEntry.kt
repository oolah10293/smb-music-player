package com.smbmusic.player.model

import com.smbmusic.player.media.AudioFormats

data class RemoteEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val modified: Long,
    val size: Long
) {
    val isAudio: Boolean
        get() = !isDirectory && AudioFormats.isSupported(name)
}
