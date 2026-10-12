package com.housemusic.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.util.UnstableApi

/** Preserve old notification/task entry points without creating another pager. */
@UnstableApi
class NowPlayingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HousePageActivity.EXTRA_PAGE, 1))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

