package com.housemusic.player.house

import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.IntentCompat

/** Hardware transport affects this phone. The app's custom transport affects the house. */
class HouseMediaControls(
    private val appPackage: String,
    private val isMuted: () -> Boolean,
    private val hasBluetoothOutput: () -> Boolean,
    private val setMuted: (Boolean) -> Unit,
    private val record: (String) -> Unit = {},
) {
    fun onMediaButton(packageName: String, intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return false
        val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
        if (event.keyCode !in LOCAL_KEYS) return false
        // Android can attribute forwarded hardware keys to the framework or this service.
        // Identified third-party remote apps retain their normal house controls.
        if (!isBluetooth(packageName) && packageName !in SYSTEM_PACKAGES && packageName != appPackage) return false
        // A press consists of down/up, sometimes repeated downs. Apply it only once.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val play = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> true
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> false
                else -> isMuted()
            }
            localTransport(play, packageName, "key")
        }
        return true
    }

    /** Also cover Bluetooth stacks that use transport controls instead of media key intents. */
    fun onTransport(packageName: String?, play: Boolean): Boolean {
        if (packageName == null || (!isBluetooth(packageName) && packageName !in SYSTEM_PACKAGES)) return false
        localTransport(play, packageName, "transport")
        return true
    }

    private fun localTransport(play: Boolean, packageName: String, origin: String) {
        // Query the actual route now: a late Play after disconnection must not enable speakers.
        val connected = hasBluetoothOutput()
        val ignored = play && !connected
        record("Media $origin ${if (play) "Play" else "Pause/Stop"} from $packageName; Bluetooth=$connected; " +
            if (ignored) "ignored without Bluetooth output" else "phone ${if (play) "unmute" else "mute"}")
        if (!ignored) setMuted(!play)
        // Presence heartbeats let the server pause/resume ONLY its automatically paused session.
        // Never send /play, /pause or /stop here, including after a disconnect.
    }

    private fun isBluetooth(packageName: String) = packageName in BLUETOOTH_PACKAGES

    companion object {
        private val BLUETOOTH_PACKAGES = setOf("com.android.bluetooth", "com.google.android.bluetooth")
        private val SYSTEM_PACKAGES = setOf("android", "android.media.session.MediaController")
        private val LOCAL_KEYS = setOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK)
    }
}
