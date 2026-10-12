package com.housemusic.player

import androidx.media3.common.util.UnstableApi

/** One window owns Library, Now Playing and Radio; swipes never launch activities. */
@UnstableApi
class MainActivity : HousePageActivity() {
    companion object {
        const val EXTRA_FILENAME = "com.housemusic.player.filename"
        const val EXTRA_MODIFIED = "com.housemusic.player.modified"
        const val PREFS_UI = SortModeStore.PREFS_UI
        const val PREF_QUEUE_SORT = SortModeStore.PREF_SORT_MODE
    }
}

