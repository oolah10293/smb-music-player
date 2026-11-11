package com.housemusic.player.house

/** Focus retries require user intent; polling and loss callbacks never fight other audio. */
class HouseFocusRecovery(initiallyMuted: Boolean) {
    var allowed = false
        private set
    private var muted = initiallyMuted
    private var pending = !initiallyMuted

    fun setMuted(value: Boolean) {
        muted = value
        pending = !value
        if (value) allowed = false
    }

    fun explicitPlay() {
        if (!muted && !allowed) pending = true
    }

    fun focusChanged(gained: Boolean) {
        allowed = !muted && gained
        pending = false
    }

    fun requestIfReady(ready: Boolean, request: () -> Boolean) {
        if (ready && pending && !muted) {
            pending = false
            allowed = request()
        }
    }
}
