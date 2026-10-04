package com.smbmusic.player

/** Only standalone user intent and Bluetooth output availability, independent of networking. */
internal class StandalonePlaybackIntent {
    enum class BluetoothAction { NONE, PAUSE, RESUME }

    var resumePending = false
        private set

    var explicitlyStopped = true
        private set
    var bluetoothConnected = false
        private set

    fun restore(stopped: Boolean, bluetoothAvailable: Boolean) {
        explicitlyStopped = stopped
        bluetoothConnected = bluetoothAvailable
        resumePending = false
    }

    fun playbackRequested() { explicitlyStopped = false }
    fun stop() { explicitlyStopped = true; cancelResume() }
    fun requestResume(hasQueue: Boolean): Boolean {
        if (!canResume(hasQueue)) return false
        resumePending = true
        return true
    }
    fun cancelResume() { resumePending = false }
    fun canAttemptResume(hasQueue: Boolean): Boolean = resumePending && canResume(hasQueue)

    fun canResume(hasQueue: Boolean): Boolean =
        bluetoothConnected && hasQueue && !explicitlyStopped

    fun bluetoothChanged(connected: Boolean, hasQueue: Boolean): BluetoothAction {
        if (connected == bluetoothConnected) return BluetoothAction.NONE
        bluetoothConnected = connected
        if (!connected) cancelResume()
        if (!hasQueue || explicitlyStopped) return BluetoothAction.NONE
        return if (connected) BluetoothAction.RESUME else BluetoothAction.PAUSE
    }
}
