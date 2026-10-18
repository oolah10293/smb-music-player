package com.smbmusic.player

import org.junit.Assert.*
import org.junit.Test

class RecoveryIntentTest {
    @Test fun pendingIntentSurvivesNetworkRetriesUntilExplicitPause() {
        val intent=RecoveryIntent();intent.play()
        repeat(20) { assertTrue(intent.eligible()) }
        intent.pause();assertFalse(intent.eligible())
        intent.play();assertTrue(intent.eligible());assertFalse(intent.eligible(stopped=true))
    }
    @Test fun resumePlaylistCannotClearRealOutputOrFocusHold() {
        for (hold in listOf("output","focus","output/focus")) {
            val intent=RecoveryIntent();intent.play();intent.hold=hold
            intent.afterPlaylistRestore(true);assertFalse(intent.eligible());assertEquals(hold,intent.hold)
            intent.play();assertTrue(intent.eligible())
        }
    }
    @Test fun pausedSavedPlaylistStaysPausedEvenWhenRadioWasPlaying() {
        val intent=RecoveryIntent();intent.play();intent.afterPlaylistRestore(false)
        assertFalse(intent.eligible());assertEquals("user",intent.hold)
    }
    @Test fun pausedRadioDoesNotEraseSavedPlayingPlaylistIntent() {
        val intent=RecoveryIntent();intent.pause();intent.afterPlaylistRestore(true)
        assertTrue(intent.eligible())
    }
}
