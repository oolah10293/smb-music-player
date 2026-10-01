package com.housemusic.player.house

import org.junit.Assert.*
import org.junit.Test

class HouseAudioTimingTest {
    @Test fun readsActualSnapclientSettingsWithoutInferringDeviceDelay() {
        assertEquals(HouseAudioTiming(3000, 25), HouseAudioTiming.fromLog(
            "2026-09-30 [Info] (Controller) ServerSettings - buffer: 3000, latency: 25, volume: 100, muted: 0"))
        assertNull(HouseAudioTiming.fromLog("unrelated message"))
        assertNull(HouseAudioTiming.fromLog("ServerSettings - buffer: 999999999999, latency: 0"))
    }

    @Test fun unknownSettingsNeverAdvanceAndDefaultNeverChangesTiming() {
        assertEquals(0, HouseAudioTiming().effectiveOffset(1000))
        assertEquals(0, HouseAudioTiming(3000, 0).effectiveOffset(0))
        assertEquals(-200, HouseAudioTiming().effectiveOffset(-200))
    }

    @Test fun correctionPreservesHeadroomIncludingServerLatency() {
        assertEquals(775, HouseAudioTiming(1000, 25).effectiveOffset(1000))
        assertEquals(1000, HouseAudioTiming(3000, 25).effectiveOffset(1000))
        assertEquals(0, HouseAudioTiming(100, 200).effectiveOffset(1000))
    }

    @Test fun boundsAndSignsAreSafeEvenForExtremeReportedIntegers() {
        assertEquals(2000, HouseAudioTiming(Int.MAX_VALUE, Int.MIN_VALUE).effectiveOffset(9999))
        assertEquals(-2000, HouseAudioTiming(3000).effectiveOffset(-9999))
        assertEquals(0, HouseAudioTiming(0, Int.MAX_VALUE).effectiveOffset(100))
    }
}
