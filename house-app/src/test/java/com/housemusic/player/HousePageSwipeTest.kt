package com.housemusic.player

import android.view.MotionEvent
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HousePageSwipeTest {
    @Test fun horizontalSwipeCancelsOriginalTapBeforeNavigating() {
        val pages = mutableListOf<Int>()
        var clicks = 0
        val row = attachedButton().apply {
            setOnClickListener { clicks++ }
        }
        val delivered = mutableListOf<Int>()
        val swipe = HousePageSwipe(8f, 64f) { pages += it }
        fun send(action: Int, x: Float, y: Float) = event(action, x, y) {
            swipe.dispatch(it) { child -> delivered += child.actionMasked; row.dispatchTouchEvent(child) }
        }
        send(MotionEvent.ACTION_DOWN, 250f, 80f)
        send(MotionEvent.ACTION_MOVE, 200f, 82f)
        send(MotionEvent.ACTION_UP, 100f, 84f)
        assertEquals(listOf(1), pages)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), delivered)
        assertEquals("Swiping a station/file must not play it", 0, clicks)
        send(MotionEvent.ACTION_DOWN, 100f, 80f)
        send(MotionEvent.ACTION_MOVE, 200f, 82f)
        send(MotionEvent.ACTION_UP, 250f, 84f)
        assertEquals(listOf(1, -1), pages)
    }

    @Test fun verticalScrollAndRefreshKeepTheirEntireTouchSequence() {
        val pages = mutableListOf<Int>()
        val delivered = mutableListOf<Int>()
        val swipe = HousePageSwipe(8f, 64f) { pages += it }
        for ((action, x, y) in listOf(Triple(0, 100f, 100f), Triple(2, 102f, 150f), Triple(2, 220f, 250f), Triple(1, 220f, 300f))) {
            event(action, x, y) { swipe.dispatch(it) { child -> delivered += child.actionMasked; true } }
        }
        assertTrue(pages.isEmpty())
        assertEquals(listOf(0, 2, 2, 1), delivered)
    }

    @Test fun excludedSeekAndTextControlsKeepHorizontalDragsAndOrdinaryTapsStillClick() {
        var navigations = 0
        val delivered = mutableListOf<Int>()
        val swipe = HousePageSwipe(8f, 64f) { navigations++ }
        for ((action, x) in listOf(0 to 100f, 2 to 200f, 1 to 250f)) {
            event(action, x, 80f) { swipe.dispatch(it, excluded = action == 0) { child -> delivered += child.actionMasked; true } }
        }
        assertEquals(listOf(0, 2, 1), delivered)
        assertEquals(0, navigations)
        var clicks = 0
        val row = attachedButton().apply { setOnClickListener { clicks++ } }
        for (action in listOf(0, 1)) event(action, 100f, 80f) { swipe.dispatch(it) { row.dispatchTouchEvent(it) } }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, clicks)
    }

    @Test fun shortDragAndMultitouchDoNotNavigateOrBecomeClicks() {
        var navigations = 0
        val swipe = HousePageSwipe(8f, 64f) { navigations++ }
        for ((action, x) in listOf(0 to 100f, 2 to 120f, 1 to 130f,
            0 to 100f, 2 to 120f, 5 to 130f, 1 to 250f)) {
            event(action, x, 80f) { swipe.dispatch(it) { true } }
        }
        assertEquals(0, navigations)
    }

    private fun event(action: Int, x: Float, y: Float, block: (MotionEvent) -> Unit) {
        val event = MotionEvent.obtain(0, 20, action, x, y, 0)
        try { block(event) } finally { event.recycle() }
    }

    private fun attachedButton(): Button {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        return Button(activity).also {
            activity.setContentView(it)
            it.layout(0, 0, 500, 500)
        }
    }
}
