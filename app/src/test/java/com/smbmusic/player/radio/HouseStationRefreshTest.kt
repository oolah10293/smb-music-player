package com.smbmusic.player.radio

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class HouseStationRefreshTest {
    private fun bookmark(id: String, name: String = id, url: String = "http://radio.example/$id") = HouseStationBookmark(id, name, url)
    @Test fun updatesHouseNamesAndUrlsRemovesDeletedHouseRowsButKeepsLocalStations() {
        val local = RadioStation("local", "My station", "http://local.example/live", 4000, true)
        val initial = HouseStationSnapshot.reconcile(listOf(local), listOf(bookmark("a"), bookmark("b")), emptySet())
        val playing = initial.first { it.houseId == "a" }.copy(listeningMs = 12345)
        val updated = HouseStationSnapshot.reconcile(initial.map { if (it.id == playing.id) playing else it },
            listOf(bookmark("a", "New House Name", "http://radio.example/new"), bookmark("c")), emptySet())
        assertTrue(local in updated)
        assertFalse(updated.any { it.houseId == "b" })
        assertEquals(playing.id, updated.first { it.houseId == "a" }.id)
        assertEquals(12345L, updated.first { it.houseId == "a" }.listeningMs)
        assertEquals("New House Name", updated.first { it.houseId == "a" }.name)
        assertEquals("http://radio.example/new", updated.first { it.houseId == "a" }.url)
    }
    @Test fun repeatedRefreshDeduplicatesAndDoesNotResetLocalAliasOrListening() {
        val local = RadioStation("old-id", "My alias", "http://radio.example/a", 7654, true)
        val remote = listOf(bookmark("a"))
        val first = HouseStationSnapshot.reconcile(listOf(local), remote, emptySet())
        assertEquals(1, first.size)
        assertEquals("old-id", first.single().id)
        assertEquals("My alias", first.single().name)
        assertEquals(first, HouseStationSnapshot.reconcile(first, remote, emptySet()))
        val removed = HouseStationSnapshot.reconcile(first, emptyList(), emptySet())
        assertEquals(local, removed.single())
    }
    @Test fun localDeletionStaysHiddenUntilUndoAndEmptyHouseListIsAuthoritative() {
        val remote = listOf(bookmark("a"), bookmark("b"))
        val first = HouseStationSnapshot.reconcile(emptyList(), remote, setOf("a"))
        assertEquals(listOf("b"), first.map { it.houseId })
        val restored = HouseStationSnapshot.reconcile(first, remote, emptySet())
        assertEquals(setOf("a", "b"), restored.map { it.houseId }.toSet())
        assertTrue(HouseStationSnapshot.reconcile(restored, emptyList(), emptySet()).isEmpty())
    }
    @Test fun remoteUrlChangeCollidingWithLocalStationKeepsOneRowAndLocalEdits() {
        val current = listOf(RadioStation("cached", "House", "http://old.example/", 1000, houseId = "a", local = false),
            RadioStation("local", "Local alias", "http://new.example/", 2000, true))
        val merged = HouseStationSnapshot.reconcile(current, listOf(bookmark("a", "House rename", "http://new.example/")), emptySet())
        assertEquals(1, merged.size)
        assertEquals("cached", merged.single().id)
        assertEquals(3000L, merged.single().listeningMs)
        assertEquals("Local alias", merged.single().name)
        assertTrue(merged.single().local)
    }
    @Test fun validatesWholeResponseBeforeAllowingCacheReplacement() {
        val valid = """{"service":"house-audio-server","stations":[{"id":"a","name":"A","url":"HTTPS://radio.example:443/live"}]}"""
        assertEquals("https://radio.example/live", HouseStationSnapshot.parse(valid).single().url)
        assertTrue(HouseStationSnapshot.parse("""{"service":"house-audio-server","stations":[]}""").isEmpty())
        listOf("{}", "<html>error</html>", valid.replace("house-audio-server", "other-service"),
            valid.replace("HTTPS://radio.example:443/live", "smb://nas/share"),
            """{"service":"house-audio-server","stations":[{"id":"a","name":"A","url":"http://r.example/"},{"id":"b"}]}""")
            .forEach { bad -> assertThrows(Exception::class.java) { HouseStationSnapshot.parse(bad) } }
    }
    @Test fun onlyPhysicalDirectHomeRouteQualifiesIncludingWhenAVpnIsDefault() {
        val target = InetAddress.getByName("192.0.2.12")
        val direct = StationLanRoute(InetAddress.getByName("192.0.2.0"), 24, false, true)
        assertTrue(StationLanPolicy.qualifies(true, false, true, target, listOf(direct)))
        assertFalse(StationLanPolicy.qualifies(true, true, false, target, listOf(direct)))
        assertFalse(StationLanPolicy.qualifies(false, false, true, target, listOf(direct)))
        assertFalse(StationLanPolicy.qualifies(true, false, true, target, listOf(direct.copy(hasGateway = true))))
        assertFalse(StationLanPolicy.qualifies(true, false, true, target, listOf(direct.copy(prefixLength = 0))))
        assertFalse(StationLanPolicy.qualifies(true, false, true, InetAddress.getByName("198.51.100.12"), listOf(direct)))
        assertFalse(StationLanPolicy.qualifies(true, false, true, target, listOf(direct.copy(unicast = false))))
        assertTrue(StationLanPolicy.hasMatchingLinkPrefix(direct.address,24,InetAddress.getByName("192.0.2.30"),24))
        assertFalse(StationLanPolicy.hasMatchingLinkPrefix(direct.address,24,InetAddress.getByName("198.51.100.30"),24))
    }
}
