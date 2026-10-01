package com.housemusic.player.house

enum class BluetoothOutputChange { UNCHANGED, MUTE, UNMUTE }

/** Device IDs are media-capable output routes, not paired devices or media controllers. */
class HouseBluetoothPolicy(initialDevices: Set<Int>) {
    private var devices = initialDevices.toSet()
    val connected: Boolean get() = devices.isNotEmpty()

    fun update(current: Set<Int>): BluetoothOutputChange {
        val previous = devices
        devices = current.toSet()
        return when {
            (current - previous).isNotEmpty() -> BluetoothOutputChange.UNMUTE
            previous.isNotEmpty() && current.isEmpty() -> BluetoothOutputChange.MUTE
            else -> BluetoothOutputChange.UNCHANGED
        }
    }
}
