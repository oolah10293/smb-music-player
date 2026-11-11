package com.housemusic.player

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FolderHeaderLayoutTest {
    @Test fun shortNameCentersInGapAndLongNameStartsAtVisibleDefaultButtonEdge() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic)
        val density = context.resources.displayMetrics.density
        for (widthDp in listOf(320, 360, 411)) {
            val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null) as ViewGroup
            root.findViewById<View>(R.id.browserPanel).visibility = View.VISIBLE
            val label = root.findViewById<TextView>(R.id.pathText)
            val start = root.findViewById<TextView>(R.id.settingsButton)
            val end = root.findViewById<View>(R.id.upButton)
            start.text = "MP3s"
            fun layout(name: String) {
                label.text = name
                root.measure(View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            }
            layout("Rock")
            assertEquals(Gravity.CENTER, label.gravity)
            assertEquals("Centered between both buttons", (start.right + end.left) / 2, (label.left + label.right) / 2)
            assertTrue(label.top < start.bottom)
            // Legacy Robolectric assigns synthetic glyph widths; use an unambiguously wide fixture.
            layout("An exceptionally long folder name that will not fit between the buttons ".repeat(8))
            assertEquals(Gravity.START or Gravity.CENTER_VERTICAL, label.gravity)
            assertEquals(start.left + (4 * density).toInt(), label.left)
            assertTrue("No overlap with buttons", label.top > start.bottom)
            assertTrue(label.right <= (label.parent as View).width)
            assertEquals(android.text.TextUtils.TruncateAt.END, label.ellipsize)
            layout("Rap")
            assertEquals(Gravity.CENTER, label.gravity)
            assertTrue(label.top < start.bottom)
        }
    }
}
