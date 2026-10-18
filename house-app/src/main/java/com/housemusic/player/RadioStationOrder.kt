package com.housemusic.player

import com.housemusic.player.house.HouseStation

/** Remember row positions for one visit; input order is the Pi's listening ranking. */
internal class RadioStationOrder {
    private val ids = mutableListOf<String>()

    fun reset() = ids.clear()

    fun refresh(ranked: List<HouseStation>): List<HouseStation> {
        val rows = ranked.associateBy { it.id }
        for (station in ranked) if (station.id !in ids) ids += station.id
        // Retain removed IDs until the visit ends, so Undo can recover its position.
        return ids.mapNotNull { rows[it] }
    }

    fun added(id: String) {
        ids.remove(id)
        ids.add(0, id)
    }
}
