package com.housemusic.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.media3.ui.PlayerView
import com.housemusic.player.house.HouseRuntime
import com.housemusic.player.house.HouseState
import com.housemusic.player.house.RadioSong
import com.housemusic.player.house.radioDetailsBundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RadioNowPlayingViewTest {
    private fun extras(state: HouseState) = Bundle().apply {
        putString(HouseRuntime.EXTRA_RADIO_STATION_NAME, "Saved station")
        putBundle(HouseRuntime.EXTRA_RADIO_DETAILS, state.radioDetailsBundle())
    }

    @Test fun freshDetailsReplaceCurrentWithoutReplacingPreviousOrDependingOnTimeline() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic)
        val root = LayoutInflater.from(context).inflate(R.layout.radio_now_playing_details, null)
        val previous = RadioSong("Previous song", "Previous artist", "Previous album", "Previous station", 1791638000.0)
        val state = HouseState(isRadio = true, radioTitle = "Current title", radioArtist = "Current artist",
            radioAlbum = "Album", radioBroadcastName = "Official station", radioBitrate = 192,
            radioAudio = "44100:24:2", radioHistorySupported = true, lastRadioSong = previous)
        RadioNowPlayingView.bind(root, extras(state))
        assertEquals("Saved station", root.findViewById<TextView>(R.id.radioDetailStation).text.toString())
        assertEquals("Current title", root.findViewById<TextView>(R.id.radioDetailTitle).text.toString())
        assertEquals("Current artist", root.findViewById<TextView>(R.id.radioDetailArtist).text.toString())
        assertEquals("Album", root.findViewById<TextView>(R.id.radioDetailAlbum).text.toString())
        assertTrue(root.findViewById<TextView>(R.id.radioDetailQuality).text.contains("192 kbps"))
        assertTrue(root.findViewById<TextView>(R.id.radioLastStationTime).text.contains("Previous station"))
        RadioNowPlayingView.bind(root, extras(state.copy(radioTitle = "Next title", radioAlbum = "")))
        assertEquals("Next title", root.findViewById<TextView>(R.id.radioDetailTitle).text.toString())
        assertEquals("Previous song", root.findViewById<TextView>(R.id.radioLastTitle).text.toString())
        assertEquals(View.GONE, root.findViewById<View>(R.id.radioDetailAlbum).visibility)
        RadioNowPlayingView.bind(root, extras(HouseState(isRadio = true)))
        assertEquals(View.GONE, root.findViewById<View>(R.id.radioLastPlayedPanel).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.radioDetailArtist).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.radioDetailQuality).visibility)
        assertEquals("Song details unavailable", root.findViewById<TextView>(R.id.radioDetailTitle).text.toString())
    }

    @Test fun longRadioMetadataScrollsWithoutMovingFixedControlsOnSmallPhones() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic)
        val density = context.resources.displayMetrics.density
        for (width in listOf(320, 360, 411)) {
            val root = LayoutInflater.from(context).inflate(R.layout.activity_now_playing, null)
            root.findViewById<View>(R.id.localMetadataPanel).visibility = View.GONE
            root.findViewById<View>(R.id.playerView).visibility = View.GONE
            root.findViewById<PlayerView>(R.id.controlsPlayerView).apply {
                controllerShowTimeoutMs = 0
                showController()
                showLiveRadioControls(true)
            }
            val scroll = root.findViewById<View>(R.id.radioDetailsScroll)
            scroll.visibility = View.VISIBLE
            RadioNowPlayingView.bind(scroll, extras(HouseState(radioTitle = "Long title ".repeat(60),
                radioArtist = "Artist", radioHistorySupported = true)))
            root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val controls = root.findViewById<View>(R.id.controlsPlayerView)
            assertTrue(scroll.height > 100 * density)
            assertEquals((200 * density).toInt(), controls.height)
            assertTrue(controls.bottom <= root.height)
            assertTrue(root.findViewById<View>(R.id.housePlayPauseButton).height > 0)
        }
    }
}
