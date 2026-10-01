package com.smbmusic.player.house

/** The local player's automatic lifecycle never overrides an explicit Stop or Quit. */
object PlaybackTransitionPolicy {
    fun bluetoothResume(connected: Boolean, hasSession: Boolean, explicitlyStopped: Boolean,
                        handoffPending: Boolean): Boolean =
        connected && hasSession && !explicitlyStopped && !handoffPending

    fun continueDeparture(wasAudiblePlaying: Boolean, bluetoothConnected: Boolean,
                          explicitlyStopped: Boolean): Boolean =
        wasAudiblePlaying && bluetoothConnected && !explicitlyStopped

    /** Once commit may have started, only proof of a terminal success can select HOUSE. */
    fun mayAdoptHandoff(status: String): Boolean = status == "committed" || status == "adopt_existing"
}
