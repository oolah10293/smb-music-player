package com.housemusic.player.house

import android.graphics.Rect
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.housemusic.player.R
import com.housemusic.player.showLiveRadioControls
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HouseControlsLayoutTest {
    @Test fun radioHidesLocalControlsAndRestoresThemWithoutMovingPhoneOutputButtons() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic)
        val root = LayoutInflater.from(context).inflate(R.layout.activity_now_playing, null) as ViewGroup
        val controls = root.findViewById<PlayerView>(R.id.controlsPlayerView)
        controls.setControllerShowTimeoutMs(0)
        controls.showController()
        val density = context.resources.displayMetrics.density
        val width = (320 * density).toInt()
        val height = (640 * density).toInt()
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            shadowOf(Looper.getMainLooper()).idle()
        }
        fun bounds(id: Int): Rect {
            val view = controls.findViewById<View>(id)
            return Rect(0, 0, view.width, view.height).also { root.offsetDescendantRectToMyCoords(view, it) }
        }
        controls.showLiveRadioControls(false)
        layout()
        val mute = bounds(R.id.muteOutputButton)
        val sync = bounds(R.id.houseSettingsButton)
        controls.showLiveRadioControls(true)
        layout()
        for (id in listOf(androidx.media3.ui.R.id.exo_prev, androidx.media3.ui.R.id.exo_next,
            androidx.media3.ui.R.id.exo_shuffle, androidx.media3.ui.R.id.exo_repeat_toggle,
            androidx.media3.ui.R.id.exo_time, androidx.media3.ui.R.id.exo_progress)) {
            assertNotEquals("Live control $id should be hidden", View.VISIBLE, controls.findViewById<View>(id).visibility)
        }
        assertEquals(View.VISIBLE, controls.findViewById<View>(R.id.muteOutputButton).visibility)
        assertEquals(View.VISIBLE, controls.findViewById<View>(R.id.houseSettingsButton).visibility)
        assertEquals(mute, bounds(R.id.muteOutputButton))
        assertEquals(sync, bounds(R.id.houseSettingsButton))
        controls.showLiveRadioControls(false)
        layout()
        for (id in listOf(androidx.media3.ui.R.id.exo_prev, androidx.media3.ui.R.id.exo_next,
            androidx.media3.ui.R.id.exo_shuffle, androidx.media3.ui.R.id.exo_repeat_toggle,
            androidx.media3.ui.R.id.exo_time, androidx.media3.ui.R.id.exo_progress)) {
            assertEquals("Local control $id should return", View.VISIBLE, controls.findViewById<View>(id).visibility)
        }
    }

    @Test fun muteAndSyncStayVisibleBesideShuffleAndRepeatOnSmallScreens() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic)
        val density = context.resources.displayMetrics.density
        for (widthDp in listOf(320, 360, 411)) {
            val root = LayoutInflater.from(context).inflate(R.layout.activity_now_playing, null) as ViewGroup
            val controls = root.findViewById<PlayerView>(R.id.controlsPlayerView)
            controls.setControllerShowTimeoutMs(0)
            controls.setShowShuffleButton(true)
            controls.setRepeatToggleModes(2)
            controls.showController()
            val width = (widthDp * density).toInt()
            val height = (640 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            shadowOf(Looper.getMainLooper()).idle()
            val ids = listOf(androidx.media3.ui.R.id.exo_shuffle, androidx.media3.ui.R.id.exo_repeat_toggle,
                R.id.muteOutputButton, R.id.houseSettingsButton)
            val bounds = ids.map { id ->
                val view = controls.findViewById<View>(id)
                assertEquals("$widthDp dp: hidden control $id", View.VISIBLE, view.visibility)
                val rect = Rect(0, 0, view.width, view.height)
                root.offsetDescendantRectToMyCoords(view, rect)
                assertTrue("$widthDp dp: clipped $rect", rect.left >= 0 && rect.right <= width && rect.width() > 0)
                rect
            }
            assertEquals(1, bounds.map { it.top }.distinct().size)
            bounds.zipWithNext().forEach { (a,b) -> assertTrue(a.right <= b.left) }
            val time = controls.findViewById<View>(androidx.media3.ui.R.id.exo_time)
            val timeRect = Rect(0, 0, time.width, time.height)
            root.offsetDescendantRectToMyCoords(time, timeRect)
            assertTrue("$widthDp dp: time overlaps controls", timeRect.right <= bounds.first().left)
        }
    }
}
