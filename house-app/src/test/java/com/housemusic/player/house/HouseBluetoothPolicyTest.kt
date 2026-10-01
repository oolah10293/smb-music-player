package com.housemusic.player.house

import org.junit.Assert.*
import org.junit.Test

class HouseBluetoothPolicyTest {
    @Test fun openingWithExistingBluetoothDoesNotUnmuteSilentAttachment() {
        val policy = HouseBluetoothPolicy(setOf(10))
        assertTrue(policy.connected)
        assertEquals(BluetoothOutputChange.UNCHANGED, policy.update(setOf(10)))
    }

    @Test fun newConnectionUnmutesAndLastDisconnectionMutes() {
        val policy = HouseBluetoothPolicy(emptySet())
        assertEquals(BluetoothOutputChange.UNMUTE, policy.update(setOf(10)))
        assertEquals(BluetoothOutputChange.MUTE, policy.update(emptySet()))
        assertFalse(policy.connected)
    }

    @Test fun duplicateCallbacksDoNotOverrideSubsequentManualMute() {
        val policy = HouseBluetoothPolicy(emptySet())
        policy.update(setOf(10))
        assertEquals(BluetoothOutputChange.UNCHANGED, policy.update(setOf(10)))
        assertEquals(BluetoothOutputChange.UNMUTE, policy.update(setOf(20)))
    }

    @Test fun removingOneOfTwoOutputsDoesNotMuteTheRemainingOutput() {
        val policy = HouseBluetoothPolicy(setOf(10, 20))
        assertEquals(BluetoothOutputChange.UNCHANGED, policy.update(setOf(20)))
        assertTrue(policy.connected)
        assertEquals(BluetoothOutputChange.MUTE, policy.update(emptySet()))
    }
}
