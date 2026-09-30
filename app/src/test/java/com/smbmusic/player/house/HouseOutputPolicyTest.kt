package com.smbmusic.player.house

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HouseOutputPolicyTest {
    private val own = "phone-self"
    private fun renderer(audible: Boolean, owner: String? = null) = JSONObject()
        .put("controllerId", owner ?: JSONObject.NULL).put("present", true).put("audible", audible)
    private fun presence(count: Int, vararg outputs: JSONObject, reachable: Boolean = true) = JSONObject()
        .put("presence", JSONObject().put("snapserverReachable", reachable)
            .put("audibleCount", count).put("renderers", JSONArray(outputs.toList())))
    private fun decide(transport: String, presence: JSONObject? = null,
                       action: HousePlaybackStart = HousePlaybackStart.QUEUE, muted: Boolean = true) =
        HouseOutputPolicy.shouldUnmute(action, muted, transport, presence, own)

    @Test fun changingSongsOrPlaylistWithAudibleRadiosPreservesPhoneMute() {
        assertFalse(decide("play", presence(1, renderer(true))))
        assertFalse(decide("play", presence(2, renderer(true), renderer(true))))
        assertFalse(decide("play", presence(1, renderer(true, "another-phone"))))
    }

    @Test fun deliberatelyStartingFromPauseOrStopUnmutesEvenIfARadioIsPresent() {
        for (transport in listOf("pause", "stop")) {
            assertTrue(decide(transport, presence(1, renderer(true))))
            assertTrue(decide(transport, action = HousePlaybackStart.PLAY))
        }
    }

    @Test fun playingWithoutAudibleOutputsUnmutesIncludingFinalTrackDrain() {
        assertTrue(decide("play", presence(0)))
        assertTrue(decide("play", presence(0, renderer(false))))
        assertTrue(decide("play", presence(0, renderer(false, "another-phone"))))
    }

    @Test fun ownStaleOutputDoesNotMasqueradeAsAnotherListener() {
        assertTrue(decide("play", presence(1, renderer(true, own))))
        assertFalse(decide("play", presence(2, renderer(true, own), renderer(true))))
    }

    @Test fun alreadyUnmutedPhoneDoesNotRequestOutputAgain() {
        assertFalse(decide("stop", muted = false))
        assertFalse(decide("play", presence(0), muted = false))
    }

    @Test fun browseSortSkipAndPlayDuringPlaybackDoNotUnmute() {
        for (transport in listOf("play", "pause", "stop"))
            assertFalse(decide(transport, presence(0), action = HousePlaybackStart.NONE))
        assertFalse(decide("play", presence(1, renderer(true)), action = HousePlaybackStart.PLAY))
    }

    @Test fun unknownOrUnreachableAudibilityIsNotEvidenceOfSilence() {
        assertFalse(decide("play"))
        assertFalse(decide("play", presence(0, reachable = false)))
        assertFalse(decide("play", presence(-1)))
        assertFalse(decide("play", presence(1))) // Incomplete renderer evidence.
        assertFalse(decide("unknown", presence(0)))
    }
}
