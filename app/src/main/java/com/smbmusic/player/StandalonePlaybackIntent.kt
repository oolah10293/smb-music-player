package com.smbmusic.player

/** Only standalone user intent and Bluetooth output availability, independent of networking. */
internal class StandalonePlaybackIntent {
    enum class BluetoothAction { NONE, PAUSE, RESUME }

    var explicitlyStopped = true
        private set
    var bluetoothConnected = false
        private set

    fun restore(stopped: Boolean, bluetoothAvailable: Boolean) {
        explicitlyStopped = stopped
        bluetoothConnected = bluetoothAvailable
    }

    fun playbackRequested() { explicitlyStopped = false }
    fun stop() { explicitlyStopped = true }

    fun canResume(hasQueue: Boolean): Boolean =
        bluetoothConnected && hasQueue && !explicitlyStopped

    fun bluetoothChanged(connected: Boolean, hasQueue: Boolean): BluetoothAction {
        if (connected == bluetoothConnected) return BluetoothAction.NONE
        bluetoothConnected = connected
        if (!hasQueue || explicitlyStopped) return BluetoothAction.NONE
        return if (connected) BluetoothAction.RESUME else BluetoothAction.PAUSE
    }
}
