package com.smbmusic.player.house

import org.junit.Assert.*
import org.junit.Test

class PlaybackTransitionPolicyTest {
    @Test fun `preconnected and reconnect can resume retained playback but cannot invent a queue`() {
        assertTrue(PlaybackTransitionPolicy.bluetoothResume(true, true, false, false))
        assertFalse(PlaybackTransitionPolicy.bluetoothResume(true, false, false, false))
        assertFalse(PlaybackTransitionPolicy.bluetoothResume(false, true, false, false))
    }

    @Test fun `Stop Quit and an unresolved commit defeat later Bluetooth reconnect`() {
        assertFalse(PlaybackTransitionPolicy.bluetoothResume(true, true, true, false))
        assertFalse(PlaybackTransitionPolicy.bluetoothResume(true, true, false, true))
    }

    @Test fun `departure requires audible playing state and still connected output`() {
        assertTrue(PlaybackTransitionPolicy.continueDeparture(true, true, false))
        assertFalse(PlaybackTransitionPolicy.continueDeparture(false, true, false))
        assertFalse(PlaybackTransitionPolicy.continueDeparture(true, false, false))
        assertFalse(PlaybackTransitionPolicy.continueDeparture(true, true, true))
    }

    @Test fun `timeout receipts select HOUSE only after proven commit or existing authority`() {
        assertTrue(PlaybackTransitionPolicy.mayAdoptHandoff("committed"))
        assertTrue(PlaybackTransitionPolicy.mayAdoptHandoff("adopt_existing"))
        listOf("reserved", "expired", "cancelled", "failed", "unknown", "")
            .forEach { assertFalse(it, PlaybackTransitionPolicy.mayAdoptHandoff(it)) }
    }
}
