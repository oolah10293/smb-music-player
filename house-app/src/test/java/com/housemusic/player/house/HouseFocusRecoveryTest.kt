package com.housemusic.player.house

import org.junit.Assert.*
import org.junit.Test

class HouseFocusRecoveryTest {
    @Test fun explicitPlayRecoversDeniedFocusWithoutRestarting() {
        val focus = HouseFocusRecovery(false)
        var requests = 0
        focus.requestIfReady(true) { requests++; false }
        assertFalse(focus.allowed)
        repeat(10) { focus.requestIfReady(true) { requests++; true } }
        assertEquals(1, requests)
        focus.explicitPlay()
        focus.requestIfReady(true) { requests++; true }
        assertEquals(2, requests)
        assertTrue(focus.allowed)
    }

    @Test fun lostFocusWaitsForGainOrExplicitPlayAndRespectsAnotherDenial() {
        val focus = HouseFocusRecovery(false)
        focus.requestIfReady(true) { true }
        focus.focusChanged(false)
        var requests = 0
        repeat(10) { focus.requestIfReady(true) { requests++; true } }
        assertFalse(focus.allowed)
        assertEquals(0, requests)
        focus.explicitPlay()
        focus.requestIfReady(true) { requests++; false }
        assertFalse(focus.allowed)
        focus.requestIfReady(true) { requests++; true }
        assertEquals(1, requests)
        focus.focusChanged(true)
        assertTrue(focus.allowed)
    }

    @Test fun manualMuteSurvivesExplicitPlayAndLateFocusGain() {
        val focus = HouseFocusRecovery(false)
        focus.requestIfReady(true) { true }
        focus.setMuted(true)
        focus.explicitPlay()
        focus.focusChanged(true)
        focus.requestIfReady(true) { fail("Muted phone requested focus"); true }
        assertFalse(focus.allowed)
        focus.setMuted(false)
        focus.requestIfReady(true) { true }
        assertTrue(focus.allowed)
    }

    @Test fun startupWaitsForReadyAndRegisteredServerAndDoesNotRepeatRequests() {
        val focus = HouseFocusRecovery(false)
        focus.requestIfReady(false) { fail("Not ready"); true }
        var requests = 0
        focus.requestIfReady(true) { requests++; true }
        focus.explicitPlay()
        focus.requestIfReady(true) { requests++; true }
        assertEquals(1, requests)
    }
}
