package com.housemusic.player.house

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaylistResumeTest {
    private val token = "a".repeat(32)
    private fun response(status: String = "ready", available: Boolean = true, id: Any = token) = JSONObject()
        .put("mpd", JSONObject().put("transport", "stop"))
        .put("source", JSONObject().put("type", "radio").put("resumePlaylist", JSONObject()
            .put("available", available).put("snapshotId", id).put("status", status).put("lastError", JSONObject.NULL)))
        .put("sessionPolicy", JSONObject().put("startup", JSONObject().put("ready", true)))

    private class Server : HousePlaybackState {
        override var connected = true
        override var canControl = true
        override var state = HouseState()
        var resultCode = SessionResult.RESULT_SUCCESS
        val writes = mutableListOf<Pair<String, String>>()
        override fun command(path: String, body: JSONObject): ListenableFuture<SessionResult> {
            writes += path to body.toString()
            return Futures.immediateFuture(SessionResult(resultCode))
        }
    }

    @Test fun parsesAvailabilityAndServerIdFromState() {
        val state = HouseState.parse(response(), emptyList())
        assertTrue(state.playlistResume.canResume)
        assertEquals(token, state.playlistResume.snapshotId)
        assertEquals("", state.playlistResume.error)
    }

    @Test fun oldServerEmptyCompletedUncertainAndMalformedIdsAreUnavailable() {
        for (json in listOf(response("none", false, JSONObject.NULL), response("completed", false),
            response("uncertain", false), response(id = "null"), response(id = "x".repeat(31)),
            response(id = "x".repeat(33)))) {
            assertFalse(HouseState.parse(json, emptyList()).playlistResume.canResume)
        }
        val old = response().apply { getJSONObject("source").remove("resumePlaylist") }
        assertFalse(HouseState.parse(old, emptyList()).playlistResume.canResume)
        old.remove("source")
        assertFalse(HouseState.parse(old, emptyList()).playlistResume.canResume)
    }

    @Test fun resumeSendsExactlyOneServerOwnedSnapshotRequest() {
        val server = Server().apply { state = HouseState.parse(response(), emptyList()) }
        assertEquals(SessionResult.RESULT_SUCCESS, server.resumePlaylist(token).get().resultCode)
        assertEquals(1, server.writes.size)
        assertEquals("/radio/resume-playlist", server.writes.single().first)
        val body = JSONObject(server.writes.single().second)
        assertEquals(setOf("snapshotId"), body.keys().asSequence().toSet())
        assertEquals(token, body.getString("snapshotId"))
    }

    @Test fun staleUnavailableDisconnectedAndAlreadyRestoredRequestsNeverWrite() {
        val server = Server().apply { state = HouseState.parse(response(), emptyList()) }
        server.resumePlaylist("b".repeat(32)).get()
        server.canControl = false
        server.resumePlaylist(token).get()
        server.canControl = true
        server.state = server.state.copy(isRadio = false)
        server.resumePlaylist(token).get()
        server.state = HouseState.parse(response("uncertain", false), emptyList())
        server.resumePlaylist(token).get()
        assertTrue(server.writes.isEmpty())
    }

    @Test fun failedResumeIsNotAutomaticallyReplayed() {
        val server = Server().apply {
            state = HouseState.parse(response(), emptyList())
            resultCode = SessionError.ERROR_UNKNOWN
        }
        assertEquals(SessionError.ERROR_UNKNOWN, server.resumePlaylist(token).get().resultCode)
        assertEquals(1, server.writes.size)
    }
}
