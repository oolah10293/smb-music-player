package com.housemusic.player.house

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HouseRadioStateTest {
    private val station = """{"id":"saved-1","name":"The Rock Station","url":"https://example.com/live"}"""
    private val queue = listOf(HouseTrack(17, "https://example.com/live", "Old title", "Old artist", "", 0, 0))

    private fun response(status: String, intent: String, title: String = "New title") = JSONObject("""
        {"mpd":{"queueVersion":7,"songId":17,"transport":"stop","elapsedSeconds":400,
          "repeat":true,"random":true,"song":{"id":17,"file":"https://example.com/live","title":"$title","artist":"New artist"}},
         "source":{"type":"radio","station":$station,"status":"$status","playIntent":"$intent",
          "lastError":null,"canSeek":false,"canSkip":false,"canShuffle":false,"canRepeat":false},
         "sessionPolicy":{"startup":{"ready":true}}}
    """)

    @Test fun liveTitlesRefreshWithoutQueueRevisionChange() {
        val first = HouseState.parse(response("playing", "play", "First song"), queue)
        val next = HouseState.parse(response("playing", "play", "Second song"), queue)
        assertEquals(first.queueVersion, next.queueVersion)
        assertEquals(first.songId, next.songId)
        assertEquals("Second song", next.tracks.single().title)
        assertEquals("New artist", next.tracks.single().artist)
        assertEquals("The Rock Station", next.radioStationName)
        assertEquals("saved-1", next.radioStationId)
        assertEquals(0L, next.positionMs)
        assertFalse(next.shuffle)
        assertFalse(next.repeat)
    }

    @Test fun radioPauseUsesSourceIntentWhileRawMpdIsStopped() {
        val paused = HouseState.parse(response("paused", "pause"), queue)
        assertEquals("stop", paused.transport)
        assertEquals("paused", paused.radioStatus)
        assertEquals("pause", paused.radioPlayIntent)
        assertFalse(paused.canNavigate)
        assertFalse(paused.canSeek)
        assertFalse(paused.canSkip)
        assertFalse(paused.canShuffle)
        assertFalse(paused.canRepeat)
        assertEquals("", paused.radioError)
    }

    @Test fun retainedStationSurvivesMissingMpdSongAndBlankIcyTitleIsNotDisplayed() {
        val blank = HouseState.parse(response("connecting", "play", " - "), queue)
        assertEquals("", blank.tracks.single().title)
        val stoppedResponse = response("stopped", "stop")
        stoppedResponse.getJSONObject("mpd").put("song", JSONObject.NULL).put("songId", JSONObject.NULL)
        val retained = HouseState.parse(stoppedResponse, emptyList())
        assertEquals("https://example.com/live", retained.tracks.single().file)
        assertEquals("The Rock Station", retained.radioStationName)
    }

    @Test fun oldServerAndReturningLocalFolderKeepOriginalTransportCapabilities() {
        val localTracks = listOf(HouseTrack(28, "MP3s/song.mp3", "Local song", "", "", 120000, 0))
        val response = JSONObject("""{"mpd":{"queueVersion":8,"songId":28,"transport":"play","elapsedSeconds":18,"random":true,"repeat":true},"sessionPolicy":{"startup":{"ready":true}}}""")
        for (source in listOf<JSONObject?>(null, JSONObject("""{"type":"library","live":false,"station":null,"canSeek":true,"canSkip":true,"canShuffle":true,"canRepeat":true}"""))) {
            if (source != null) response.put("source", source)
            val local = HouseState.parse(response, localTracks)
            assertFalse(local.isRadio)
            assertEquals("", local.radioStationName)
            assertEquals(18000L, local.positionMs)
            assertTrue(local.canSeek && local.canSkip && local.canShuffle && local.canRepeat)
            assertTrue(local.shuffle && local.repeat)
        }
    }
}
