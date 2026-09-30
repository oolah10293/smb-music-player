package com.smbmusic.player.house

/** A Quit invalidates pending probes and cleanup belonging to the previous service. */
class HouseSelection<T> {
    private data class Selection<T>(val endpoint: T?, val resolved: Boolean, val epoch: Long)
    @Volatile private var value = Selection<T>(null, false, 0)
    val endpoint: T? get() = value.endpoint
    val resolved: Boolean get() = value.resolved
    val epoch: Long get() = value.epoch

    @Synchronized fun publish(expectedEpoch: Long, endpoint: T?): Boolean {
        if (value.epoch != expectedEpoch) return false
        value = Selection(endpoint, true, expectedEpoch)
        return true
    }

    @Synchronized fun clear(expectedEpoch: Long = value.epoch): Boolean {
        if (value.epoch != expectedEpoch) return false
        value = Selection(null, false, value.epoch + 1)
        return true
    }
}
