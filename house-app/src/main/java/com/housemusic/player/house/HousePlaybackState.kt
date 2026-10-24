package com.housemusic.player.house

import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject

/** Server snapshot and single-attempt command boundary consumed by Media3. */
interface HousePlaybackState {
    val state: HouseState
    val connected: Boolean
    val canControl: Boolean
    val localOutputMuted: Boolean get() = false
    fun command(path: String, body: JSONObject = JSONObject()): ListenableFuture<SessionResult>
}
