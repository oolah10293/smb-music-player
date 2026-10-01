package com.housemusic.player.house

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HouseStateTest {
    @Test fun duplicateFilesKeepMpdIdentityAndNullMetadataDoesNotBecomeText() {
        val rows = JSONArray("""[
            {"id":12,"file":"Rap/Same.mp3","title":null,"artist":null,"albumArtist":"Artist","durationSeconds":123.25,"lastModified":"2026-09-29T12:00:00Z"},
            {"id":19,"file":"Rap/Same.mp3","title":"Second entry","durationSeconds":123.25}
        ]""")
        val tracks = HouseState.tracks(rows)
        assertEquals(listOf(12,19), tracks.map { it.id })
        assertEquals("", tracks[0].title)
        assertEquals("Artist", tracks[0].artist)
        assertEquals(123250L, tracks[0].durationMs)
        assertTrue(tracks[0].modified > 0)
        assertEquals(0L, tracks[1].modified)
        val response = JSONObject("""{"mpd":{"queueVersion":5,"songId":19,"transport":"pause","elapsedSeconds":32.5,"random":true,"repeat":true},"sessionPolicy":{"startup":{"ready":true}}}""")
        val state = HouseState.parse(response, tracks)
        assertEquals(1, state.index)
        assertEquals(32500L, state.positionMs)
        assertEquals("pause", state.transport)
        assertTrue(state.ready)
    }

    @Test fun startupWithoutReadinessCannotAdmitPlaybackEvenIfMpdIsPlaying() {
        for (policy in listOf("{}", """{"startup":{"ready":false}}""")) {
            val response = JSONObject("""{"mpd":{"transport":"play","queueVersion":4},"sessionPolicy":$policy}""")
            assertFalse(HouseState.parse(response, emptyList()).ready)
        }
    }

    @Test fun freshIdleHasNoBogusTrackOrPosition() {
        val response = JSONObject("""{"mpd":{"queueVersion":8,"songId":null,"transport":"stop","elapsedSeconds":null,"random":false,"repeat":false},"sessionPolicy":{"startup":{"ready":true}}}""")
        val state = HouseState.parse(response, emptyList())
        assertTrue(state.ready)
        assertTrue(state.tracks.isEmpty())
        assertEquals(-1, state.songId)
        assertEquals(0L, state.positionMs)
        assertFalse(state.shuffle)
    }
}
