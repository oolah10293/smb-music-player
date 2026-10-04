package com.housemusic.player.house

import org.junit.Assert.*
import org.junit.Test

class HouseOutputPolicyTest {
    @Test fun pauseResumeWithTwoNodesNeverEnablesPhoneWithoutBluetooth() {
        val phone = HouseOutputPolicy(false)
        phone.transportChanged() // Pause the shared session.
        phone.transportChanged() // Resume the two nodes.
        assertTrue(phone.muted)
        assertFalse(phone.bluetoothConnected)
        phone.requestMute(false) // Explicit user intent allows phone / wired output.
        assertFalse(phone.muted)
    }

    @Test fun wiredUnmuteSurvivesPlayPauseAndUnrelatedAudioCallbacks() {
        val phone = HouseOutputPolicy(false)
        phone.requestMute(false)
        repeat(5) {
            phone.transportChanged()
            phone.updateRoute(false, false)
            assertFalse(phone.muted)
        }
        phone.requestMute(true)
        phone.transportChanged()
        assertTrue(phone.muted)
    }

    @Test fun bluetoothAutomationStillOverridesOnConnectAndDisconnect() {
        val phone = HouseOutputPolicy(false)
        phone.requestMute(false)
        phone.updateRoute(true, true)
        assertFalse(phone.muted)
        phone.updateRoute(false, false)
        assertTrue(phone.muted)
        phone.updateRoute(true, true)
        assertFalse(phone.muted)
    }

    @Test fun alreadyConnectedOutputIsRecognizedOnAttachment() {
        val phone = HouseOutputPolicy(true)
        assertFalse(phone.muted)
        assertTrue(phone.bluetoothConnected)
        phone.transportChanged()
        assertFalse(phone.muted)
    }

    @Test fun manualMuteSurvivesTransportAndUnchangedRoute() {
        val phone = HouseOutputPolicy(true)
        phone.requestMute(true)
        repeat(5) { phone.transportChanged() }
        phone.updateRoute(true, false)
        assertTrue(phone.muted)
        phone.updateRoute(true, true) // A newly connected media route is distinct output intent.
        assertFalse(phone.muted)
    }

    @Test fun disconnectOverridesPendingPlayAndReconnectRestoresEligibility() {
        val phone = HouseOutputPolicy(true)
        phone.updateRoute(false, false)
        phone.transportChanged()
        assertTrue(phone.muted)
        phone.requestMute(false)
        assertFalse(phone.muted)
        phone.updateRoute(true, true)
        assertFalse(phone.muted)
    }
}
