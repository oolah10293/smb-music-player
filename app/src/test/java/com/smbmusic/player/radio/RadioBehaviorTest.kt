package com.smbmusic.player.radio

import org.junit.Assert.*
import org.junit.Test

class RadioBehaviorTest {
    @Test fun urlNormalizationPreservesTokensAndEscapes() {
        assertEquals("https://radio.example/a%2Fb?token=AbC%2F", RadioUrl.normalize(" HTTPS://Radio.Example:443/a%2Fb?token=AbC%2F "))
        assertEquals("http://[::1]:8000/live", RadioUrl.normalize("http://[::1]:8000/live"))
        assertEquals("http://radio.example/", RadioUrl.normalize("http://radio.example:80"))
    }
    @Test fun rejectsCredentialsDirectoriesAndWrongSchemes() {
        listOf("smb://nas/share", "https://u:p@radio.example/live", "https://radio.example/list.pls",
            "https://radio.example/list.m3u", "https://radio.example/live#fragment", "https://", "station").forEach {
            assertThrows(IllegalArgumentException::class.java) { RadioUrl.normalize(it) }
        }
    }
    @Test fun supportsHlsAndDirectExtensionlessLinks() {
        assertEquals("https://r.example/live.m3u8", RadioUrl.normalize("https://r.example/live.m3u8"))
        assertEquals("http://r.example/stream", RadioUrl.normalize("http://r.example/stream"))
    }
    @Test fun plainAndStructuredSongMetadata() {
        assertEquals(RadioSong("Song", "Artist", identified=true), RadioMetadata.parse("Artist - Song"))
        assertEquals(RadioSong("Song", "Artist", "Record", true), RadioMetadata.parse("title=\"Song\" artist=\"Artist\" album=\"Record\""))
        val song = RadioMetadata.parse("Artist - text=\"Song\" song_spot=\"M\" MediaBaseId=\"123\"")
        assertEquals("Song", song.title); assertEquals("Artist", song.artist); assertTrue(song.identified)
    }
    @Test fun opaqueTailsAndStationSlogansAreNotSongs() {
        val song = RadioMetadata.parse("Station - text=\"The best rock\" song_spot=\"T\" url=\"https://secret.example/token\"")
        assertEquals("Station", song.title); assertFalse(song.identified); assertEquals("", song.artist)
        assertFalse(RadioMetadata.parse("The best rock").identified)
        assertFalse(RadioMetadata.parse( "x".repeat(17000)).identified)
    }
    @Test fun historyRequiresMoreThanTenSecondsOfAdvancingPlayback() {
        val h = RadioHistory(); val a = RadioSong("A", "Artist", identified=true)
        h.update(a,"Station",10); h.advance(5000);h.advance(5000);h.update(null,"Station",20)
        assertNull(h.previous)
        h.update(a,"Station",30); repeat(11) {h.advance(1000)}; h.update(RadioSong("ad"),"Station",40)
        assertEquals(PreviousRadioSong(a,"Station",30),h.previous)
        h.update(null,"Station",50); h.advance(100000); h.finish()
        assertEquals("A",h.previous?.song?.title)
    }
    @Test fun rankingIsStableDuringVisitNewStationPinsAndUndoRetainsPosition() {
        val order=RadioStationOrder();val a=RadioStation("a","A","http://a.example/");val b=RadioStation("b","B","http://b.example/")
        assertEquals(listOf(a,b),order.refresh(listOf(a,b)))
        assertEquals(listOf(a,b),order.refresh(listOf(b,a)))
        assertEquals(listOf(b),order.refresh(listOf(b)))
        assertEquals(listOf(a,b),order.refresh(listOf(b,a)))
        val c=RadioStation("c","C","http://c.example/");order.added("c")
        assertEquals(listOf(c,a,b),order.refresh(listOf(b,a,c)))
        order.reset();assertEquals(listOf(b,a,c),order.refresh(listOf(b,a,c)))
    }
}
