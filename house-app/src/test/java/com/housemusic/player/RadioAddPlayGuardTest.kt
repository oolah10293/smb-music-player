package com.housemusic.player

import org.junit.Assert.*
import org.junit.Test

class RadioAddPlayGuardTest {
    @Test fun aConfirmedAddCanPlayWhileTheUserStaysOnRadio() {
        val guard = RadioAddPlayGuard()
        guard.enterPage()
        val add = guard.capture()
        assertTrue(guard.canAutoPlay(add))
    }

    @Test fun leavingRadioCancelsDelayedPlaybackEvenAfterReturning() {
        val guard = RadioAddPlayGuard()
        guard.enterPage()
        val slowAdd = guard.capture()
        guard.leavePage()
        assertFalse(guard.canAutoPlay(slowAdd))
        guard.enterPage()
        assertFalse(guard.canAutoPlay(slowAdd))
        assertTrue(guard.canAutoPlay(guard.capture()))
    }
}
