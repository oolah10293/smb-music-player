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
