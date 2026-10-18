package com.smbmusic.player.storage

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SessionRecordTest {
    private val song=SessionItem("smb://nas/music/a.mp3",filename="a.mp3",modified=123)
    @Test fun migratesV051QueueWithoutDroppingDuplicates() {
        val json=JSONObject("""{"items":[{"uri":"smb://n/a"},{"uri":"smb://n/a"}],"index":1,"positionMs":45678,"shuffle":true,"explicitlyStopped":false}""")
        val result=SessionRecord.decode(json)
        assertEquals(2,result.items.size);assertEquals(1,result.index);assertEquals(45678L,result.positionMs)
        assertTrue(result.shouldPlay);assertFalse(result.radio)
    }
    @Test fun radioRoundTripPreservesExactPlaylistAndPausedIntent() {
        val queue=SessionRecord(listOf(song,song.copy(filename="duplicate"),song.copy(uri="smb://nas/music/b.mp3")),1,23456,true,false,false,shuffleOrder=listOf(2,0,1))
        val radio=SessionRecord(listOf(SessionItem("https://radio.example/live")),0,0,false,false,true,true,"s1","Station",queue)
        assertEquals(radio,SessionRecord.decode(JSONObject(radio.encode().toString())))
    }
    @Test fun quitOverridesAutoplayAndInvalidShuffleIsDiscarded() {
        val record=SessionRecord(listOf(song,song),0,0,true,true,true,shuffleOrder=listOf(0,0))
        val decoded=SessionRecord.decode(record.encode())
        assertFalse(decoded.shouldPlay);assertTrue(decoded.shuffleOrder.isEmpty())
    }
    @Test fun rejectsRadioAsSavedSmbQueue() {
        val record=SessionRecord(listOf(SessionItem("https://radio.example/live")),0,0,false,false,radio=true)
        assertThrows(IllegalArgumentException::class.java) { SessionRecord.decode(record.encode(),true) }
        assertThrows(IllegalArgumentException::class.java) { SessionRecord.decode(record.copy(radio=false).encode()) }
    }
}
