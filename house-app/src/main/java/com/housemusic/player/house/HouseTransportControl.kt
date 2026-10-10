package com.housemusic.player.house

import android.os.Bundle
import android.widget.ImageButton
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand

/** App transport follows the service's confirmed readiness, independently of Media3's cached timeline. */
@UnstableApi
class HouseTransportControl(private val button: ImageButton, private val controller: () -> MediaController?) {
    init {
        button.setOnClickListener {
            val current = controller() ?: return@setOnClickListener
            if (current.isConnected && current.sessionExtras.getBoolean(HouseRuntime.EXTRA_CAN_PLAY)) {
                current.sendCustomCommand(SessionCommand(HouseRuntime.TOGGLE_PLAY, Bundle.EMPTY), Bundle.EMPTY)
            }
        }
        refresh()
    }

    fun refresh() {
        val current = controller()
        val extras = current?.takeIf { it.isConnected }?.sessionExtras ?: Bundle.EMPTY
        button.isEnabled = extras.getBoolean(HouseRuntime.EXTRA_CAN_PLAY)
        button.alpha = if (button.isEnabled) 1f else 0.3f
        val playing = extras.getBoolean(HouseRuntime.EXTRA_PLAYING)
        button.setImageResource(if (playing) androidx.media3.ui.R.drawable.exo_styled_controls_pause
            else androidx.media3.ui.R.drawable.exo_styled_controls_play)
        button.contentDescription = if (playing) "Pause" else "Play"
    }
}
