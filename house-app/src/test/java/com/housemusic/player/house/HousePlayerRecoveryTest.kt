package com.housemusic.player.house

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HousePlayerRecoveryTest {
    private class Server : HousePlaybackState {
        override var connected = true
        override var canControl = true
        override var state = HouseState(
            tracks = listOf(HouseTrack(1, "MP3s/one.mp3", "One", "", "", 200000, 0),
                HouseTrack(2, "MP3s/two.mp3", "Two", "", "", 220000, 0)),
            songId = 1, transport = "play", positionMs = 10000, ready = true)
        var pending = SettableFuture.create<SessionResult>()
        val writes = mutableListOf<String>()
        override fun command(path: String, body: JSONObject): ListenableFuture<SessionResult> {
            writes += path
            return pending
        }
    }

    @Test fun rejectedNextAdoptsStoppedStateAndPlayWorksWithoutReopening() {
        val server = Server()
        val player = HousePlayer(server)
        player.seekToNextMediaItem()
        assertEquals(listOf("/next"), server.writes)
        assertEquals("MP3s/one.mp3", player.currentMediaItem?.mediaId)
        server.state = server.state.copy(transport = "stop", positionMs = 0)
        player.refresh() // Real runtime publishes before completing the command future.
        server.pending.set(SessionResult(SessionError.ERROR_UNKNOWN))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(Util.shouldEnablePlayPauseButton(player))
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertEquals(0L, player.currentPosition)
        server.pending = SettableFuture.create()
        Util.handlePlayButtonAction(player)
        assertEquals(listOf("/next", "/play"), server.writes)
        server.state = server.state.copy(transport = "play")
        server.pending.set(SessionResult(SessionResult.RESULT_SUCCESS))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(player.isPlaying)
        player.release()
    }

    @Test fun lostResponseAdoptsActualNextTrackWithoutReplayingCommand() {
        val server = Server()
        val player = HousePlayer(server)
        player.seekToNextMediaItem()
        server.state = server.state.copy(songId = 2, positionMs = 2000)
        server.pending.set(SessionResult(SessionError.ERROR_UNKNOWN))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("MP3s/two.mp3", player.currentMediaItem?.mediaId)
        assertEquals(listOf("/next"), server.writes)
        assertTrue(Util.shouldEnablePlayPauseButton(player))
        player.release()
    }

    @Test fun connectionLossAndFreshStateRestoreControlsInSamePlayer() {
        val server = Server()
        val player = HousePlayer(server)
        server.connected = false; server.canControl = false
        player.refresh()
        assertFalse(Util.shouldEnablePlayPauseButton(player))
        assertNull(player.currentMediaItem)
        server.connected = true; server.canControl = true
        server.state = server.state.copy(transport = "stop")
        player.refresh()
        assertTrue(Util.shouldEnablePlayPauseButton(player))
        assertTrue(server.writes.isEmpty())
        player.release()
    }
}
