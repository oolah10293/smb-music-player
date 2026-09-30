package com.smbmusic.player.house

import org.junit.Assert.*
import org.junit.Test

class HouseSelectionTest {
    @Test fun quitInvalidatesAnOutstandingHouseProbe() {
        val selection = HouseSelection<String>()
        val oldEpoch = selection.epoch
        selection.clear()
        assertFalse(selection.publish(oldEpoch, "old-house"))
        assertNull(selection.endpoint)
        assertFalse(selection.resolved)
    }

    @Test fun delayedOldServiceCleanupCannotEraseNewLaunch() {
        val selection = HouseSelection<String>()
        val oldService = selection.epoch
        assertTrue(selection.publish(oldService, "house"))
        selection.clear()
        assertTrue(selection.publish(selection.epoch, "fresh-house"))
        assertFalse(selection.clear(oldService))
        assertEquals("fresh-house", selection.endpoint)
        assertTrue(selection.resolved)
    }

    @Test fun freshStandaloneSelectionIsResolvedWithoutStaleEndpoint() {
        val selection = HouseSelection<String>()
        selection.publish(selection.epoch, "house")
        selection.clear()
        assertTrue(selection.publish(selection.epoch, null))
        assertNull(selection.endpoint)
        assertTrue(selection.resolved)
    }
}
