package com.housemusic.player.house

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.widget.ImageButton
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HouseMediaControlsTest {
    private class Phone {
        var muted = false
        var bluetooth = true
        val changes = mutableListOf<Boolean>()
        val controls = HouseMediaControls("com.housemusic.player", { muted }, { bluetooth }, {
            muted = it; changes += it
        })
        fun key(code: Int, action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0,
            source: String = "com.android.bluetooth") = controls.onMediaButton(source,
            Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(0, 0, action, code, repeat)))
    }

    @Test fun pauseAndShutdownStopMuteLocallyBeforeOrAfterRouteLoss() {
        for (code in listOf(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP)) {
            for (connected in listOf(true, false)) {
                val phone = Phone().apply { bluetooth = connected }
                assertTrue(phone.key(code))
                assertTrue(phone.muted)
                assertTrue(phone.key(code))
                assertTrue("Repeated explicit Pause must never toggle back on", phone.muted)
            }
        }
    }

    @Test fun toggleUsesPhoneMuteAndConsumesUpAndRepeatWithoutTogglingAgain() {
        val phone = Phone()
        assertTrue(phone.key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertTrue(phone.muted)
        assertTrue(phone.key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, repeat = 1))
        assertTrue(phone.key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.ACTION_UP))
        assertEquals(listOf(true), phone.changes)
        assertTrue(phone.key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertFalse(phone.muted)
        assertEquals(listOf(true, false), phone.changes)
    }

    @Test fun playChecksCurrentRouteAndCannotLeakToPhoneSpeakerAfterDisconnect() {
        val phone = Phone().apply { muted = true }
        assertTrue(phone.key(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertFalse(phone.muted)
        phone.muted = true
        phone.bluetooth = false
        assertTrue(phone.key(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertTrue(phone.controls.onTransport("com.android.bluetooth", true))
        assertTrue(phone.muted)
        assertEquals(listOf(false), phone.changes)
    }

    @Test fun forwardedFrameworkKeysStayLocalAndRemoteAppCommandsRemainGlobal() {
        for (source in listOf("android", "android.media.session.MediaController", "com.housemusic.player")) {
            val phone = Phone().apply { bluetooth = false }
            assertTrue(phone.key(KeyEvent.KEYCODE_MEDIA_PAUSE, source = source))
            assertTrue(phone.muted)
        }
        val phone = Phone()
        for (source in listOf("com.garmin.android.apps.connectmobile", "com.housemusic.player", null)) {
            assertFalse(phone.controls.onTransport(source, false))
        }
        assertFalse(phone.key(KeyEvent.KEYCODE_MEDIA_PAUSE, source = "com.garmin.android.apps.connectmobile"))
        assertFalse(phone.key(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertFalse(phone.key(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertTrue(phone.changes.isEmpty())
    }

    @Test fun bluetoothTransportControlsAreLocalForPlayPauseAndStop() {
        val phone = Phone()
        assertTrue(phone.controls.onTransport("com.android.bluetooth", false))
        assertTrue(phone.muted)
        assertTrue(phone.controls.onTransport("com.google.android.bluetooth", true))
        assertFalse(phone.muted)
    }

    private class Server : HousePlaybackState {
        override val connected = true
        override val canControl = true
        override var localOutputMuted = false
        override var state = HouseState(tracks = listOf(HouseTrack(1, "MP3s/one.mp3", "One", "", "", 200000, 0)),
            songId = 1, transport = "play", ready = true)
        val writes = mutableListOf<String>()
        override fun command(path: String, body: JSONObject) =
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)).also { writes += path }
    }

    @Test fun realSessionRoutesBluetoothTransportLocallyButActualAppPauseStillControlsHouse() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val bluetoothContext = object : ContextWrapper(activity) {
            override fun getPackageName() = "com.android.bluetooth"
            override fun getApplicationContext(): Context = this
        }
        val server = Server()
        val player = HousePlayer(server)
        val hardware = HouseMediaControls(activity.packageName, { server.localOutputMuted }, { true }, {
            server.localOutputMuted = it; player.refresh()
        })
        val session = MediaSession.Builder(activity, player).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, info: MediaSession.ControllerInfo) =
                MediaSession.ConnectionResult.AcceptedResultBuilder(session, info)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(HouseRuntime.TOGGLE_PLAY, Bundle.EMPTY)).build()).build()
            override fun onCustomCommand(session: MediaSession, info: MediaSession.ControllerInfo,
                command: SessionCommand, args: Bundle) = server.command(
                    if (server.state.transport == "play") "/pause" else "/play", JSONObject())
        }).build()
        player.localMediaTransport = { hardware.onTransport(session.controllerForCurrentRequest?.packageName, it) }
        val btFuture = MediaController.Builder(bluetoothContext, session.token).buildAsync()
        val appFuture = MediaController.Builder(activity, session.token).buildAsync()
        drain()
        assertTrue(btFuture.isDone)
        assertTrue(appFuture.isDone)
        val bt = btFuture.get()
        val app = appFuture.get()
        try {
            bt.pause(); drain()
            assertTrue("Bluetooth controller must mute the phone", server.localOutputMuted)
            assertFalse("Headset must see paused phone state", bt.playWhenReady)
            assertEquals("play", server.state.transport)
            assertTrue(server.writes.isEmpty())
            bt.play(); drain()
            assertFalse(server.localOutputMuted)
            assertTrue(bt.playWhenReady)
            assertTrue(server.writes.isEmpty())
            bt.stop(); drain()
            assertTrue("Shutdown Stop is also phone-only", server.localOutputMuted)
            assertTrue(server.writes.isEmpty())

            session.setSessionExtras(Bundle().apply {
                putBoolean(HouseRuntime.EXTRA_CAN_PLAY, true)
                putBoolean(HouseRuntime.EXTRA_PLAYING, true)
            }); drain()
            val button = ImageButton(activity)
            HouseTransportControl(button) { app }.refresh()
            assertEquals("Pause", button.contentDescription)
            button.performClick(); drain()
            assertEquals(listOf("/pause"), server.writes)

            // A Bluetooth Play after deliberate house Pause only unmutes; no /play override.
            server.state = server.state.copy(transport = "pause")
            player.refresh(); drain()
            bt.play(); drain()
            assertFalse(server.localOutputMuted)
            assertFalse(bt.playWhenReady)
            assertEquals(listOf("/pause"), server.writes)
        } finally {
            bt.release(); app.release(); session.release(); player.release(); activity.finish()
        }
    }

    @Test fun mutedPhoneAdvertisesPausedForFilesAndRadioWithoutLosingHouseQueue() {
        val server = Server().apply { localOutputMuted = true }
        val player = HousePlayer(server)
        assertFalse(player.playWhenReady)
        assertEquals("One", player.mediaMetadata.title.toString())
        assertTrue(player.isCurrentMediaItemSeekable)
        server.state = server.state.copy(isRadio = true, radioStatus = "playing", radioPlayIntent = "play")
        player.refresh()
        assertFalse(player.playWhenReady)
        assertTrue(player.isCurrentMediaItemLive)
        assertFalse(player.isCurrentMediaItemSeekable)
        assertTrue(server.writes.isEmpty())
        player.release()
    }

    private fun drain() { repeat(12) { shadowOf(Looper.getMainLooper()).idle() } }
}
