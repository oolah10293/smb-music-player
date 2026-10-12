package com.housemusic.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

/** Native touch tracking, velocity-based settling and interruptible consecutive swipes. */
@UnstableApi
open class HousePageActivity : AppCompatActivity() {
    internal lateinit var pager: ViewPager2
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        pager = ViewPager2(this).apply {
            id = R.id.housePager
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            // Keep all three existing view trees ready. Only the selected page is RESUMED.
            offscreenPageLimit = 2
            adapter = object : FragmentStateAdapter(this@HousePageActivity) {
                override fun getItemCount() = 3
                override fun createFragment(position: Int): Fragment = createPage(position)
            }
        }
        setContentView(pager)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 30)
        }
        if (savedInstanceState == null) pager.setCurrentItem(intent.getIntExtra(EXTRA_PAGE, 0).coerceIn(0, 2), false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pager.currentItem > 0) showPage(pager.currentItem - 1)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
    }

    protected open fun createPage(position: Int): Fragment = when (position) {
        0 -> LibraryFragment()
        1 -> NowPlayingFragment()
        else -> RadioFragment()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_PAGE)) showPage(intent.getIntExtra(EXTRA_PAGE, 0))
    }

    fun showPage(page: Int) {
        hideKeyboard()
        pager.setCurrentItem(page.coerceIn(0, 2), true)
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
        currentFocus?.clearFocus()
    }

    companion object { const val EXTRA_PAGE = "com.housemusic.player.PAGE" }
}

