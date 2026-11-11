package com.housemusic.player

import android.content.Intent
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TimeBar
import kotlin.math.abs

/** Existing pages keep their state and service bindings while changing pages. */
@UnstableApi
abstract class HousePageActivity : AppCompatActivity() {
    protected abstract val housePage: Int
    private val swipe by lazy { HousePageSwipe(ViewConfiguration.get(this).scaledTouchSlop.toFloat(),
        64 * resources.displayMetrics.density, ::changePage) }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = swipe.dispatch(event,
        excluded = event.actionMasked == MotionEvent.ACTION_DOWN &&
            isGestureControl(window.decorView, event.rawX.toInt(), event.rawY.toInt()),
        deliver = { super.dispatchTouchEvent(it) })

    private fun isGestureControl(view: View, x: Int, y: Int): Boolean {
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) return false
        if (view is EditText || view is TimeBar || view is Button || view is ImageButton ||
            view.id == R.id.controlsPlayerView) return true
        return view is ViewGroup && (0 until view.childCount).any { isGestureControl(view.getChildAt(it), x, y) }
    }

    private fun changePage(direction: Int) {
        val pages = listOf(MainActivity::class.java, NowPlayingActivity::class.java, RadioActivity::class.java)
        val target = pages.getOrNull(housePage + direction) ?: return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(window.decorView.windowToken, 0)
        currentFocus?.clearFocus()
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        @Suppress("DEPRECATION")
        overridePendingTransition(if (direction > 0) R.anim.page_enter_right else R.anim.page_enter_left,
            if (direction > 0) R.anim.page_exit_left else R.anim.page_exit_right)
    }
}

/** Once horizontal motion wins, cancel the original touch so station/file taps cannot fire. */
internal class HousePageSwipe(private val slop: Float, private val distance: Float,
    private val navigate: (Int) -> Unit) {
    private var x = 0f
    private var y = 0f
    private var horizontal = false
    private var ignored = false
    fun dispatch(event: MotionEvent, excluded: Boolean = false, deliver: (MotionEvent) -> Boolean): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x = event.x; y = event.y; horizontal = false; ignored = excluded
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                ignored = true
                // If intercepted already, keep consuming until the whole gesture ends.
            }
            MotionEvent.ACTION_MOVE -> if (!ignored && !horizontal) {
                val dx = abs(event.x - x)
                val dy = abs(event.y - y)
                if (dy > slop && dy >= dx) ignored = true
                else if (dx > slop && dx > 2 * dy) {
                    horizontal = true
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    deliver(cancel)
                    cancel.recycle()
                }
            }
            MotionEvent.ACTION_UP -> if (horizontal) {
                if (!ignored && abs(event.x - x) >= distance) navigate(if (event.x < x) 1 else -1)
                horizontal = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> if (horizontal) { horizontal = false; return true }
        }
        return if (horizontal) true else deliver(event)
    }
}
