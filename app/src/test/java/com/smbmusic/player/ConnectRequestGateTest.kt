package com.smbmusic.player

import org.junit.Assert.*
import org.junit.Test

class ConnectRequestGateTest {
    @Test fun browserAndServiceBurstDoesNotReplaceTheConnectWorker() {
        val gate = ConnectRequestGate()
        assertTrue(gate.allow(0)) // Browser startup.
        assertFalse(gate.allow(0)) // Service created in the same turn.
        assertFalse(gate.allow(2_000)) // Former unconditional second broadcast.
        assertFalse(gate.allow(15_000)) // SMB retry: let the worker finish.
        assertTrue(gate.allow(30_000)) // A later real failure can request connection again.
    }
    @Test fun repeatedFailuresCanRequestAgainWithoutRestartingTheApp() {
        val gate = ConnectRequestGate()
        assertTrue(gate.allow(50_000))
        assertTrue(gate.allow(80_000))
        assertTrue(gate.allow(140_000))
        assertFalse(gate.allow(140_001))
    }
}
