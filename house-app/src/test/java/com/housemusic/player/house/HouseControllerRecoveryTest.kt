package com.housemusic.player.house

import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.Futures
import com.housemusic.player.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.app.Activity

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HouseControllerRecoveryTest {
    private class Server : HousePlaybackState {
        override var connected = false
        override var canControl = false
        override var state = HouseState()
        val writes = mutableListOf<String>()
        override fun command(path: String, body: JSONObject) =
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)).also { writes += path }
    }

    @Test fun coldControllerReceivesCommandsAfterStoppedQueueAndLeaseArrive() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity, R.style.Theme_HouseMusic)
        val view = LayoutInflater.from(context).inflate(R.layout.activity_now_playing, null)
        activity.setContentView(view)
        val controls = view.findViewById<PlayerView>(R.id.controlsPlayerView)
        val server = Server()
        val player = HousePlayer(server)
        val session = MediaSession.Builder(context, player).build()
        val future = MediaController.Builder(context, session.token).buildAsync()
        drain()
        assertTrue("Controller connected", future.isDone)
        val controller = future.get()
        try {
            controls.player = controller
            controls.setControllerShowTimeoutMs(0)
            controls.showController()
            assertFalse(androidx.media3.common.util.Util.shouldEnablePlayPauseButton(controller))
            server.connected = true
            server.state = HouseState(tracks = listOf(HouseTrack(1, "MP3s/one.mp3", "One", "", "", 200000, 0)),
                songId = 1, transport = "stop", ready = true)
            player.refresh()
            drain()
            assertFalse("Wait for controller lease", androidx.media3.common.util.Util.shouldEnablePlayPauseButton(controller))
            server.canControl = true
            player.refresh()
            drain()
            assertTrue("Controller receives Play capability", controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            assertNotNull("Controller receives retained queue", controller.currentMediaItem)
            assertTrue("Actual visible Play button recovers", androidx.media3.common.util.Util.shouldEnablePlayPauseButton(controller))
            androidx.media3.common.util.Util.handlePlayButtonAction(controller)
            drain()
            assertEquals(listOf("/play"), server.writes)
            server.canControl = false
            player.refresh()
            drain()
            assertFalse(androidx.media3.common.util.Util.shouldEnablePlayPauseButton(controller))
            server.canControl = true
            player.refresh()
            drain()
            assertTrue(androidx.media3.common.util.Util.shouldEnablePlayPauseButton(controller))
        } finally {
            controls.player = null
            controller.release()
            session.release()
            player.release()
            activity.finish()
        }
    }

    @Test fun authoritativeExtrasRecoverActualButtonEvenWhenControllerTimelineIsStillEmpty() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity, R.style.Theme_HouseMusic)
        val view = LayoutInflater.from(context).inflate(R.layout.activity_now_playing, null)
        activity.setContentView(view)
        val controls = view.findViewById<PlayerView>(R.id.controlsPlayerView)
        val button = controls.findViewById<android.widget.ImageButton>(R.id.housePlayPauseButton)
        val player = HousePlayer(Server()) // Deliberately remains disconnected with no queue/commands.
        val received = mutableListOf<String>()
        val session = MediaSession.Builder(context, player).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, info: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                MediaSession.ConnectionResult.AcceptedResultBuilder(session, info)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(androidx.media3.session.SessionCommand(HouseRuntime.TOGGLE_PLAY, android.os.Bundle.EMPTY)).build()).build()
            override fun onCustomCommand(session: MediaSession, info: MediaSession.ControllerInfo,
                command: androidx.media3.session.SessionCommand, args: android.os.Bundle) =
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)).also { received += command.customAction }
        }).build()
        var controller: MediaController? = null
        val transport = HouseTransportControl(button) { controller }
        val future = MediaController.Builder(context, session.token).setListener(object : MediaController.Listener {
            override fun onExtrasChanged(c: MediaController, extras: android.os.Bundle) { transport.refresh() }
        }).buildAsync()
        drain()
        controller = future.get()
        controls.player = controller
        controls.setControllerShowTimeoutMs(0)
        controls.showController()
        try {
            assertFalse(button.isEnabled)
            fun publish(ready: Boolean, playing: Boolean = false) {
                session.setSessionExtras(android.os.Bundle().apply {
                    putBoolean(HouseRuntime.EXTRA_CAN_PLAY, ready)
                    putBoolean(HouseRuntime.EXTRA_PLAYING, playing)
                })
                drain()
            }
            publish(true)
            assertFalse(controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            assertNull(controller.currentMediaItem)
            assertTrue("Confirmed server readiness must recover the actual app button", button.isEnabled)
            assertEquals("Play", button.contentDescription)
            button.performClick()
            drain()
            assertEquals(listOf(HouseRuntime.TOGGLE_PLAY), received)
            publish(true, true)
            assertEquals("Pause", button.contentDescription)
            publish(false)
            assertFalse("Expired lease/disconnected server must disable controls", button.isEnabled)
            // Even a forced accessibility/test click cannot send a command after authority is lost.
            button.performClick()
            drain()
            assertEquals(1, received.size)
            publish(true)
            assertTrue(button.isEnabled)
        } finally {
            controls.player = null
            controller.release(); session.release(); player.release(); activity.finish()
        }
    }

    private fun drain() { repeat(12) { shadowOf(Looper.getMainLooper()).idle() } }
}
