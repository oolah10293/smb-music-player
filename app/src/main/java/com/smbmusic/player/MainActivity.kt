package com.smbmusic.player

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
open class MainActivity : AppCompatActivity() {
    internal lateinit var pager: ViewPager2
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        pager = ViewPager2(this).apply {
            id = R.id.smbPager
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            // Keep all three existing view trees ready. Only the selected page is RESUMED.
            offscreenPageLimit = 2
            adapter = object : FragmentStateAdapter(this@MainActivity) {
                override fun getItemCount() = 3
                override fun createFragment(position: Int): Fragment = createPage(position)
            }
        }
        setContentView(pager)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(pager) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(pager)
        val initialPage = intent.getIntExtra(EXTRA_PAGE, getSharedPreferences(PREFS_UI, MODE_PRIVATE).getInt("page", 0))
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                getSharedPreferences(PREFS_UI, MODE_PRIVATE).edit().putInt("page", position).apply()
                supportFragmentManager.fragments.filterIsInstance<SmbPageFragment>().forEach {
                    it.onPagerSelectionChanged(it.pageIndex == position)
                }
            }
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
        val permissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            permissions += android.Manifest.permission.BLUETOOTH_CONNECT
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            permissions += android.Manifest.permission.POST_NOTIFICATIONS
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 30)
        if (savedInstanceState == null) pager.setCurrentItem(initialPage.coerceIn(0, 2), false)
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

    companion object {
        const val EXTRA_PAGE = "com.smbmusic.player.PAGE"
        const val EXTRA_FILENAME = "com.smbmusic.player.filename"
        const val EXTRA_MODIFIED = "com.smbmusic.player.modified"
        const val PREFS_UI = SortModeStore.PREFS_UI
        const val PREF_QUEUE_SORT = SortModeStore.PREF_SORT_MODE
    }
}
