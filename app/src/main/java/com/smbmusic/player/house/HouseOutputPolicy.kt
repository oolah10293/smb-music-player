package com.smbmusic.player.house

import org.json.JSONObject

enum class HousePlaybackStart { NONE, PLAY, QUEUE }

/** Decide from fresh pre-command state, not the playing state produced by the command. */
object HouseOutputPolicy {
    fun shouldUnmute(start: HousePlaybackStart, muted: Boolean, transport: String,
                     controllers: JSONObject?, ownControllerId: String, bluetoothConnected: Boolean = false): Boolean {
        if (!muted || start == HousePlaybackStart.NONE) return false
        if (bluetoothConnected && transport in setOf("play", "pause", "stop")) return true
        if (transport == "pause" || transport == "stop") return true
        if (start != HousePlaybackStart.QUEUE || transport != "play") return false
        val presence = controllers?.optJSONObject("presence") ?: return false
        // A monitor outage is not evidence of an empty house.
        if (!presence.optBoolean("snapserverReachable")) return false
        val renderers = presence.optJSONArray("renderers") ?: return false
        val audibleCount = presence.optInt("audibleCount", -1)
        if (audibleCount < 0) return false
        var ownAudible = false
        for (i in 0 until renderers.length()) {
            val renderer = renderers.getJSONObject(i)
            if (renderer.optBoolean("audible")) {
                if (renderer.optString("controllerId") != ownControllerId) return false
                ownAudible = true
            }
        }
        // Exclude this phone's briefly stale renderer report, but never guess about missing outputs.
        return audibleCount <= if (ownAudible) 1 else 0
    }
}
