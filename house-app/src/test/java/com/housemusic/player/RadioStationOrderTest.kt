package com.housemusic.player

import com.housemusic.player.house.HouseStation
import org.junit.Assert.*
import org.junit.Test

class RadioStationOrderTest {
    private fun station(id: String, name: String = id) = HouseStation(id, "https://example.com/$id", name, "user")
    private fun List<HouseStation>.ids() = map { it.id }

    @Test fun enteringUsesPiRankingAndRefreshDoesNotMoveRows() {
        val order = RadioStationOrder()
        val a = station("a"); val b = station("b"); val c = station("c")
        assertEquals(listOf("b", "a", "c"), order.refresh(listOf(b, a, c)).ids())
        assertEquals(listOf("b", "a", "c"), order.refresh(listOf(c, b, a)).ids())
        order.reset()
        assertEquals(listOf("c", "b", "a"), order.refresh(listOf(c, b, a)).ids())
    }

    @Test fun newStationStaysAtTopOnlyUntilNextVisit() {
        val order = RadioStationOrder()
        val a = station("a"); val b = station("b"); val c = station("c")
        order.refresh(listOf(a, b))
        order.added("c")
        assertEquals(listOf("c", "a", "b"), order.refresh(listOf(a, b, c)).ids())
        assertEquals(listOf("c", "a", "b"), order.refresh(listOf(b, a, c)).ids())
        order.reset()
        assertEquals(listOf("b", "a", "c"), order.refresh(listOf(b, a, c)).ids())
    }

    @Test fun renameDeleteUndoAndRemoteAddKeepExistingPositions() {
        val order = RadioStationOrder()
        val a = station("a"); val b = station("b"); val c = station("c")
        order.refresh(listOf(a, b))
        val renamed = b.copy(name = "A new name")
        val rows = order.refresh(listOf(renamed, c, a))
        assertEquals(listOf("a", "b", "c"), rows.ids())
        assertEquals("A new name", rows[1].name)
        assertEquals(listOf("a", "c"), order.refresh(listOf(c, a)).ids())
        assertEquals(listOf("a", "b", "c"), order.refresh(listOf(c, renamed, a)).ids())
    }
}
