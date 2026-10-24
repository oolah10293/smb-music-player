package com.housemusic.player.house

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class HouseCommandFeedbackTest {
    @Test fun uncertainRestoreShowsTheActualServerFailure() {
        val feedback = HouseCommandFeedback()
        val text = feedback.status(PlaylistResume(status = "uncertain", error = "ACK [5@0] {seekcur} Not seekable"), "Output on", true)
        assertTrue(text.contains("ACK [5@0] {seekcur} Not seekable"))
        assertTrue(text.endsWith("Select a station or folder."))
    }

    @Test fun missingRestoreDetailStillProvidesRecoveryInstruction() {
        assertEquals("Playlist restore could not be confirmed. Select a station or folder.",
            HouseCommandFeedback().status(PlaylistResume(status = "uncertain"), "Output on", true))
    }

    @Test fun failedCommandSurvivesRepeatedReadSnapshotsUntilNextCommand() {
        val feedback = HouseCommandFeedback()
        feedback.failed("/radio/resume-playlist", HouseApiException(409, "snapshot_stale", "Saved playlist changed."))
        repeat(4) {
            assertEquals("Resume Playlist failed: Saved playlist changed. Check playback before trying again.",
                feedback.status(PlaylistResume(), "Output on", true))
        }
        feedback.clear()
        assertEquals("Output on", feedback.status(PlaylistResume(), "Output on", true))
    }

    @Test fun lostResponseDoesNotClaimTheCommandWasNotApplied() {
        val feedback = HouseCommandFeedback()
        feedback.failed("/play", SocketTimeoutException())
        assertEquals("Play failed: Connection interrupted. Check playback before trying again.",
            feedback.status(PlaylistResume(), "Output on", true))
    }

    @Test fun connectionProblemsTakePriorityAndErrorReturnsWhenConnectionRecovers() {
        val feedback = HouseCommandFeedback()
        feedback.failed("/pause", HouseApiException(503, "unavailable", "Server starting."))
        assertEquals("Reconnecting", feedback.status(PlaylistResume(status = "uncertain"), "Reconnecting", false))
        assertTrue(feedback.status(PlaylistResume(), "Output on", true).startsWith("Pause failed: Server starting."))
    }

    @Test fun serverTextIsBoundedAndControlCharactersAreRemoved() {
        val text = HouseCommandFeedback().status(PlaylistResume(status = "uncertain", error = "ACK\n\t" + "x".repeat(1000)), "", true)
        assertTrue(text.contains("ACK x"))
        assertFalse(text.contains('\n'))
        assertTrue(text.length < 350)
    }
}
