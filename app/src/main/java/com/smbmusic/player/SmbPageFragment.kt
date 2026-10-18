package com.smbmusic.player

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TimeBar

/** Small page-local bridge: lookups never reach a neighbouring page's controls. */
@UnstableApi
abstract class SmbPageFragment(private val layout: Int, internal val pageIndex: Int) : Fragment() {
    protected val isSelectedPage get() = (activity as? MainActivity)?.pager?.currentItem == pageIndex
    internal open fun onPagerSelectionChanged(selected: Boolean) = Unit
    protected val pageContext: Context get() = requireContext()
    protected val applicationContext: Context get() = requireContext().applicationContext
    protected val window get() = requireActivity().window
    protected val isFinishing get() = activity?.isFinishing != false
    protected val isDestroyed get() = view == null || activity?.isDestroyed != false
    protected fun <T : View> findViewById(id: Int): T = requireView().findViewById(id)
    protected fun getSystemService(name: String): Any? = requireContext().getSystemService(name)
    protected fun runOnUiThread(action: () -> Unit) { activity?.runOnUiThread(action) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SmbGestureFrame(requireContext()).apply {
            addView(inflater.inflate(layout, this, false))
        }
}

/** Controls own their whole gesture; normal content uses the pager's native arbitration. */
@UnstableApi
internal class SmbGestureFrame(context: Context) : FrameLayout(context) {
    private var controlGesture = false
    private var downX = 0f
    private var downY = 0f
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x; downY = event.y
            controlGesture = isGestureControl(this, event.rawX.toInt(), event.rawY.toInt())
        }
        val handled = super.dispatchTouchEvent(event)
        // Do this after child dispatch, because nested scrolling views may reset the flag on DOWN.
        val dx = kotlin.math.abs(event.x - downX)
        val dy = kotlin.math.abs(event.y - downY)
        // Nested RecyclerView/SwipeRefreshLayout may disallow interception on DOWN.
        // Return a horizontal content gesture to ViewPager2 once its axis is clear;
        // ViewPager2 still owns tracking, velocity and settling (no synthetic fling).
        when {
            controlGesture -> parent?.requestDisallowInterceptTouchEvent(true)
            dx > slop && dx > dy -> parent?.requestDisallowInterceptTouchEvent(false)
            dy > slop && dy > dx -> parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            controlGesture = false
        }
        return handled
    }

    private fun isGestureControl(view: View, x: Int, y: Int): Boolean {
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) return false
        if (view is EditText || view is TimeBar || view is Button || view is ImageButton ||
            view.id == R.id.controlsPlayerView) return true
        return view is ViewGroup && (0 until view.childCount).any { isGestureControl(view.getChildAt(it), x, y) }
    }
}
