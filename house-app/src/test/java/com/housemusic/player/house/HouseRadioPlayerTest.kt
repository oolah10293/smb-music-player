package com.housemusic.player.house

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.SessionResult
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
class HouseRadioPlayerTest {
    private class Server : HousePlaybackState {
        override var connected = true
        override var canControl = true
        override var state = HouseState(
            tracks = listOf(HouseTrack(17, "https://example.com/live", "", "", "", 0, 0)),
            songId = 17, transport = "stop", ready = true, isRadio = true,
            radioStationId = "station-1", radioStationName = "The Rock Station",
            radioStatus = "paused", radioPlayIntent = "pause")
        val writes = mutableListOf<String>()
        override fun command(path: String, body: JSONObject): ListenableFuture<SessionResult> {
            writes += path
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    @Test fun pausedLiveStationCanResumeAndCannotSeekSkipShuffleOrRepeat() {
        val server = Server()
        val player = HousePlayer(server)
        assertEquals(Player.STATE_READY, player.playbackState)
        assertFalse(player.playWhenReady)
        assertTrue(Util.shouldEnablePlayPauseButton(player))
        assertTrue(player.isCurrentMediaItemLive)
        assertFalse(player.isCurrentMediaItemSeekable)
        assertEquals(C.TIME_UNSET, player.duration)
        assertEquals("The Rock Station", player.mediaMetadata.title.toString())
        for (command in listOf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_REPEAT_MODE)) {
            assertFalse("Radio advertises unsupported command $command", player.isCommandAvailable(command))
        }
        player.seekTo(15000)
        player.seekToNextMediaItem()
        player.seekToPreviousMediaItem()
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ALL
        assertTrue(server.writes.isEmpty())
        Util.handlePlayButtonAction(player)
        assertEquals(listOf("/play"), server.writes)
        player.release()
    }

    @Test fun reconnectingStationOffersPauseEvenThoughMpdIsStopped() {
        for (status in listOf("connecting", "retrying")) {
            val server = Server().apply { state = state.copy(radioStatus = status, radioPlayIntent = "play") }
            val player = HousePlayer(server)
            assertEquals(Player.STATE_BUFFERING, player.playbackState)
            assertTrue(player.playWhenReady)
            assertTrue(Util.shouldEnablePlayPauseButton(player))
            player.pause()
            assertEquals(listOf("/pause"), server.writes)
            player.release()
        }
    }

    @Test fun stoppedOrFailedStationCanBeStartedAgain() {
        for (status in listOf("stopped", "error")) {
            val server = Server().apply { state = state.copy(radioStatus = status, radioPlayIntent = "stop") }
            val player = HousePlayer(server)
            assertEquals(Player.STATE_IDLE, player.playbackState)
            assertTrue(Util.shouldEnablePlayPauseButton(player))
            Util.handlePlayButtonAction(player)
            assertEquals(listOf("/play"), server.writes)
            player.release()
        }
    }

    @Test fun liveMetadataUpdatesInPlaceAndSwitchingToLibraryRestoresCapabilities() {
        val server = Server()
        val player = HousePlayer(server)
        server.state = server.state.copy(tracks = server.state.tracks.map { it.copy(title = "River", artist = "Band") },
            radioStatus = "playing", radioPlayIntent = "play", transport = "play")
        player.refresh()
        assertEquals("River", player.mediaMetadata.title.toString())
        assertEquals("Band · The Rock Station", player.mediaMetadata.artist.toString())
        assertTrue(player.isPlaying)
        server.state = HouseState(tracks = listOf(HouseTrack(18, "MP3s/local.mp3", "Local", "Band", "Album", 180000, 0)),
            songId = 18, transport = "play", ready = true, shuffle = true, repeat = true)
        player.refresh()
        assertFalse(player.isCurrentMediaItemLive)
        assertTrue(player.isCurrentMediaItemSeekable)
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))
        assertEquals(180000L, player.duration)
        assertEquals("Local", player.mediaMetadata.title.toString())
        assertTrue(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        player.release()
    }
}
