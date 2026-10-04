package com.smbmusic.player

import org.junit.Assert.*
import org.junit.Test
import com.smbmusic.player.StandalonePlaybackIntent.BluetoothAction.*

class StandalonePlaybackIntentTest {
    @Test fun disconnectedOutputResumesRetainedPlaybackExactlyOnce() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = true)
        assertEquals(PAUSE, intent.bluetoothChanged(false, hasQueue = true))
        assertEquals(NONE, intent.bluetoothChanged(false, hasQueue = true))
        assertEquals(RESUME, intent.bluetoothChanged(true, hasQueue = true))
        assertEquals(NONE, intent.bluetoothChanged(true, hasQueue = true))
    }

    @Test fun stopOrQuitSurvivesReconnectionAndRestartUntilExplicitPlay() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = true)
        intent.stop()
        assertEquals(NONE, intent.bluetoothChanged(false, hasQueue = true))
        assertEquals(NONE, intent.bluetoothChanged(true, hasQueue = true))
        val restarted = StandalonePlaybackIntent()
        restarted.restore(intent.explicitlyStopped, bluetoothAvailable = true)
        assertFalse(restarted.canResume(hasQueue = true))
        restarted.playbackRequested()
        assertTrue(restarted.canResume(hasQueue = true))
    }

    @Test fun preconnectedBluetoothOnlyResumesAnEligibleRetainedQueue() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = true)
        assertFalse(intent.canResume(hasQueue = false))
        assertTrue(intent.canResume(hasQueue = true))
        intent.restore(stopped = true, bluetoothAvailable = true)
        assertFalse(intent.canResume(hasQueue = true))
    }

    @Test fun deviceEventsCannotStartAnEmptyOrExplicitlyStoppedSession() {
        val intent = StandalonePlaybackIntent()
        intent.playbackRequested()
        assertEquals(NONE, intent.bluetoothChanged(true, hasQueue = false))
        intent.stop()
        assertEquals(NONE, intent.bluetoothChanged(false, hasQueue = true))
        assertEquals(NONE, intent.bluetoothChanged(true, hasQueue = true))
    }

    @Test fun replacingOneBluetoothOutputWithAnotherDoesNotPausePlayback() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = true)
        // Service reports whether any Bluetooth output remains, not individual device events.
        assertEquals(NONE, intent.bluetoothChanged(true, hasQueue = true))
        assertEquals(PAUSE, intent.bluetoothChanged(false, hasQueue = true))
    }

    @Test fun backgroundDenialRetainsResumeAcrossDuplicateRouteCallbacks() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = false)
        assertEquals(RESUME, intent.bluetoothChanged(true, hasQueue = true))
        assertTrue(intent.requestResume(hasQueue = true))
        // Promotion/focus failed: no completion. A duplicate route callback must not lose it.
        assertEquals(NONE, intent.bluetoothChanged(true, hasQueue = true))
        assertTrue(intent.canAttemptResume(hasQueue = true))
        intent.cancelResume() // Playback actually became audible.
        assertFalse(intent.canAttemptResume(hasQueue = true))
    }

    @Test fun disconnectWhileResumeIsBlockedCannotLaterPlayOnSpeaker() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = true)
        intent.requestResume(hasQueue = true)
        assertEquals(PAUSE, intent.bluetoothChanged(false, hasQueue = true))
        assertFalse(intent.canAttemptResume(hasQueue = true))
        assertFalse(intent.requestResume(hasQueue = true))
    }

    @Test fun explicitPauseCancelsPendingRetryButAllowsLaterReconnect() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = true)
        intent.requestResume(hasQueue = true)
        intent.cancelResume()
        assertFalse(intent.canAttemptResume(hasQueue = true))
        assertEquals(NONE, intent.bluetoothChanged(true, hasQueue = true))
        intent.bluetoothChanged(false, hasQueue = true)
        assertEquals(RESUME, intent.bluetoothChanged(true, hasQueue = true))
    }

    @Test fun quitCancelsBlockedResumeAndRejectsDelayedWake() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = true)
        intent.requestResume(hasQueue = true)
        intent.stop()
        assertFalse(intent.canAttemptResume(hasQueue = true))
        assertFalse(intent.requestResume(hasQueue = true))
    }

    @Test fun vanishedQueuePreventsRetainedResume() {
        val intent = StandalonePlaybackIntent()
        intent.restore(stopped = false, bluetoothAvailable = true)
        intent.requestResume(hasQueue = true)
        assertFalse(intent.canAttemptResume(hasQueue = false))
    }
}
