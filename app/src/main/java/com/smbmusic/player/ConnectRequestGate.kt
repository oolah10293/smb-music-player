package com.smbmusic.player

/** Shared between browser/service calls: avoid restarting Tailscale's connect worker in a burst. */
internal class ConnectRequestGate(private val intervalMs: Long = 30_000L) {
    private var lastRequest: Long? = null
    fun allow(now: Long): Boolean {
        if (lastRequest?.let { now - it < intervalMs } == true) return false
        lastRequest = now
        return true
    }
}
