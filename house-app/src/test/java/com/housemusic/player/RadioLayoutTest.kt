package com.housemusic.player

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
class RadioLayoutTest {
    @Test fun radioNavigationAndUrlEntryFitSmallPhonesWithoutOpeningKeyboard() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic)
        val density = context.resources.displayMetrics.density
        for (widthDp in listOf(320, 360, 411)) {
            val root = LayoutInflater.from(context).inflate(R.layout.activity_radio, null) as ViewGroup
            root.requestFocus()
            layout(root, widthDp, 640, density)
            val url = root.findViewById<EditText>(R.id.radioUrlEdit)
            assertFalse("URL field must wait for a tap", url.hasFocus())
            for (id in listOf(R.id.radioUrlEdit, R.id.radioAddButton)) {
                val view = root.findViewById<View>(id)
                val bounds = bounds(root, view)
                assertTrue("$widthDp dp clipped control $id: $bounds", bounds.left >= 0 && bounds.right <= root.width)
                assertTrue("$widthDp dp short tap target $id", view.height >= (48 * density).toInt())
            }
            assertTrue(root.findViewById<View>(R.id.radioStationList).height > 100 * density)
        }
    }

    @Test fun longStationNameLeavesASeparate48DpDeleteTarget() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic)
        val density = context.resources.displayMetrics.density
        for (widthDp in listOf(320, 360, 411)) {
            val root = LayoutInflater.from(context).inflate(R.layout.row_radio_station, null) as ViewGroup
            root.findViewById<TextView>(R.id.radioStationName).text =
                "Radio Paradise: An exceptionally long station name with a second line"
            root.findViewById<TextView>(R.id.radioStationDetail).text = "Paused · Live radio"
            root.measure(View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val play = bounds(root, root.findViewById(R.id.radioStationPlay))
            val deleteView = root.findViewById<View>(R.id.radioStationDelete)
            val delete = bounds(root, deleteView)
            assertTrue(play.right <= delete.left)
            assertTrue(delete.right <= root.width)
            assertTrue(delete.width() >= (48 * density).toInt())
            assertTrue(delete.height() >= (48 * density).toInt())
            assertFalse(deleteView.contentDescription.isNullOrBlank())
        }
    }

    private fun layout(root: View, widthDp: Int, heightDp: Int, density: Float) {
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }

    private fun bounds(root: ViewGroup, view: View): Rect = Rect(0, 0, view.width, view.height).also {
        root.offsetDescendantRectToMyCoords(view, it)
    }
}
