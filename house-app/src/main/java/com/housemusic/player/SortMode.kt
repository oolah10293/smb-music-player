package com.housemusic.player

import android.content.Context

enum class SortMode(val label: String) {
    NAME_ASC("A–Z"),
    NAME_DESC("Z–A"),
    DATE_DESC("New–Old"),
    DATE_ASC("Old–New");

    fun next(): SortMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStorage(value: String?): SortMode =
            entries.firstOrNull { it.name == value } ?: NAME_ASC
    }
}

object SortModeStore {
    const val PREFS_UI = "house_music_ui"
    const val PREF_SORT_MODE = "queue_sort_mode"

    fun load(context: Context): SortMode {
        val stored = context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
            .getString(PREF_SORT_MODE, null)
        return SortMode.fromStorage(stored)
    }

    fun save(context: Context, mode: SortMode) {
        context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SORT_MODE, mode.name)
            .apply()
    }
}
