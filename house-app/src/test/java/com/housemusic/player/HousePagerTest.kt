package com.housemusic.player

import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HousePagerTest {
    @Test fun draggingMovesThePageBeforeReleaseAndCanBeReversed() {
        val activity = host()
        val pager = activity.pager
        val offsets = mutableListOf<Float>()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, offset: Float, pixels: Int) { offsets += offset }
        })
        val down = SystemClock.uptimeMillis()
        send(pager, down, MotionEvent.ACTION_DOWN, 450f, 300f)
        send(pager, down, MotionEvent.ACTION_MOVE, 380f, 301f)
        send(pager, down, MotionEvent.ACTION_MOVE, 200f, 301f)
        assertEquals(ViewPager2.SCROLL_STATE_DRAGGING, pager.scrollState)
        assertTrue("Real page motion must precede finger release", offsets.any { it > 0f && it < 1f })
        send(pager, down, MotionEvent.ACTION_MOVE, 440f, 301f)
        send(pager, down, MotionEvent.ACTION_CANCEL, 440f, 301f)
        settle()
        assertEquals(0, pager.currentItem)
    }

    @Test fun nextGestureInterruptsSettlingAndBothDirectionsReachTheNextPage() {
        val activity = host()
        val pager = activity.pager
        swipe(pager, 460f, 30f)
        // ViewPager2 publishes the fling target on its next scroll frame.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        assertEquals(ViewPager2.SCROLL_STATE_SETTLING, pager.scrollState)
        assertEquals(1, pager.currentItem)
        // Do not wait for the first animation: reproduce the user's rapid second swipe.
        swipe(pager, 460f, 30f)
        settle()
        assertEquals(2, pager.currentItem)
        swipe(pager, 30f, 460f)
        swipe(pager, 30f, 460f)
        settle()
        assertEquals(0, pager.currentItem)
        assertNull("Swiping must not launch another activity", shadowOf(activity).nextStartedActivity)
    }

    @Test fun verticalGesturesAndButtonsDoNotChangePages() {
        val pager = host().pager
        var down = SystemClock.uptimeMillis()
        send(pager, down, MotionEvent.ACTION_DOWN, 250f, 300f)
        send(pager, down, MotionEvent.ACTION_MOVE, 252f, 450f)
        send(pager, down, MotionEvent.ACTION_UP, 253f, 600f)
        settle()
        assertEquals(0, pager.currentItem)
        down = SystemClock.uptimeMillis()
        // Top button owns horizontal drags just like seek/edit controls.
        send(pager, down, MotionEvent.ACTION_DOWN, 450f, 40f)
        send(pager, down, MotionEvent.ACTION_MOVE, 200f, 40f)
        send(pager, down, MotionEvent.ACTION_UP, 30f, 40f)
        settle()
        assertEquals(0, pager.currentItem)
    }

    @Test fun activityRecreationRestoresSelectedPage() {
        val controller = Robolectric.buildActivity(PagerHost::class.java).setup()
        val activity = controller.get()
        layout(activity.window.decorView)
        layout(activity.pager)
        activity.pager.setCurrentItem(2, false)
        settle()
        controller.recreate()
        layout(controller.get().window.decorView)
        layout(controller.get().pager)
        settle()
        assertEquals(2, controller.get().pager.currentItem)
    }

    private fun host(): PagerHost = Robolectric.buildActivity(PagerHost::class.java).setup().get().also {
        layout(it.window.decorView)
        layout(it.pager)
        settle()
        layout(it.pager)
    }
    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 500, 800)
    }
    private fun settle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2)) }
    private fun swipe(pager: ViewPager2, from: Float, to: Float) {
        val down = SystemClock.uptimeMillis()
        send(pager, down, MotionEvent.ACTION_DOWN, from, 300f)
        for (i in 1..6) send(pager, down, MotionEvent.ACTION_MOVE, from + (to - from) * i / 6, 301f)
        send(pager, down, MotionEvent.ACTION_UP, to, 301f)
    }
    private fun send(view: View, down: Long, action: Int, x: Float, y: Float) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }
}

@UnstableApi
class PagerHost : HousePageActivity() {
    override fun createPage(position: Int): Fragment = PagerTestPage()
}

@UnstableApi
class PagerTestPage : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        HouseGestureFrame(requireContext()).apply {
            addView(View(context).apply { isClickable = true }, FrameLayout.LayoutParams(-1, -1))
            addView(Button(context).apply { text = "Control" }, FrameLayout.LayoutParams(-1, 100))
        }
}
