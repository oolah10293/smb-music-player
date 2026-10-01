package com.housemusic.player.house

/** An old network probe or service cleanup cannot republish over a later launch. */
class HouseConnectionState<T> {
    private data class Connection<T>(val endpoint: T?, val epoch: Long)
    @Volatile private var value = Connection<T>(null, 0)
    val endpoint: T? get() = value.endpoint
    val epoch: Long get() = value.epoch

    @Synchronized fun beginSession(): Long {
        value = Connection(null, value.epoch + 1)
        return value.epoch
    }

    @Synchronized fun publish(expectedEpoch: Long, endpoint: T?): Boolean {
        if (value.epoch != expectedEpoch) return false
        value = Connection(endpoint, expectedEpoch)
        return true
    }

    @Synchronized fun clear(expectedEpoch: Long): Boolean {
        if (value.epoch != expectedEpoch) return false
        value = Connection(null, value.epoch + 1)
        return true
    }
}
