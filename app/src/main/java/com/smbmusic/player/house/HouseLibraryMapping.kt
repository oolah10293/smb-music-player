package com.smbmusic.player.house

import android.content.Context

object HouseLibraryMapping {
    fun root(context: Context): String = HouseConnection.preferences(context)
        .getString("smb_music_root", "").orEmpty()

    fun saveRoot(context: Context, value: String) {
        HouseConnection.preferences(context).edit()
            .putString("smb_music_root", HouseLibraryPaths.normalizeRoot(value)).apply()
    }
}
