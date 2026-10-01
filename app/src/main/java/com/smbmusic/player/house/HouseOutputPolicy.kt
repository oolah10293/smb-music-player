package com.smbmusic.player.house

/** Local sound eligibility is independent of MPD transport and other listeners. */
class HouseOutputPolicy(initialBluetoothConnected: Boolean) {
    var bluetoothConnected = initialBluetoothConnected
        private set
    var muted = !initialBluetoothConnected
        private set

    fun updateRoute(connected: Boolean, newOutput: Boolean) {
        bluetoothConnected = connected
        muted = when {
            !connected -> true
            newOutput -> false
            else -> muted
        }
    }

    fun requestMute(value: Boolean) {
        muted = value || !bluetoothConnected
    }

    /** Play/Resume/queue commands never supply permission to unmute. */
    fun transportChanged() {
        muted = muted || !bluetoothConnected
    }
}
