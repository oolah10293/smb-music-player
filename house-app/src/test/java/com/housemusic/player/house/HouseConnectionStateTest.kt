package com.housemusic.player.house

import org.junit.Assert.*
import org.junit.Test

class HouseConnectionStateTest {
    @Test fun quitInvalidatesAnOutstandingServerProbe() {
        val connection = HouseConnectionState<String>()
        val lifetime = connection.beginSession()
        assertTrue(connection.clear(lifetime))
        assertFalse(connection.publish(lifetime, "old-server"))
        assertNull(connection.endpoint)
    }

    @Test fun delayedOldServiceCleanupCannotEraseNewLaunch() {
        val connection = HouseConnectionState<String>()
        val oldService = connection.beginSession()
        assertTrue(connection.publish(oldService, "server"))
        val newService = connection.beginSession()
        assertNull(connection.endpoint)
        assertTrue(connection.publish(newService, "fresh-server"))
        assertFalse(connection.clear(oldService))
        assertFalse(connection.publish(oldService, "stale-server"))
        assertEquals("fresh-server", connection.endpoint)
    }

    @Test fun networkLossClearsTheEndpointWithoutEndingTheServiceLifetime() {
        val connection = HouseConnectionState<String>()
        val lifetime = connection.beginSession()
        assertTrue(connection.publish(lifetime, "server"))
        assertTrue(connection.publish(lifetime, null))
        assertNull(connection.endpoint)
        assertTrue(connection.publish(lifetime, "reconnected-server"))
        assertEquals("reconnected-server", connection.endpoint)
    }
}
