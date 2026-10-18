package com.smbmusic.player

/** An internal pause does not change this user intent. Focus/output holds do. */
internal class RecoveryIntent {
    var requested = false
    var hold = ""
    fun play() { requested = true; hold = "" }
    fun pause() { requested = false; hold = "user" }
    fun eligible(stopped: Boolean = false): Boolean = requested && hold.isBlank() && !stopped
    fun afterPlaylistRestore(savedShouldPlay: Boolean) {
        requested = savedShouldPlay
        hold = if (!savedShouldPlay) "user" else hold.takeUnless { it == "user" }.orEmpty()
    }
}
