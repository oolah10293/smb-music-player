package com.smbmusic.player.media

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/** One allocator and the existing file policy, with short live buffering for HTTP radio. */
@UnstableApi
class SourceLoadControl(private val radio: () -> Boolean,
    private val files: DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(120_000, 600_000, 3_000, 20_000)
        .setPrioritizeTimeOverSizeThresholds(true).setTargetBufferBytes(32 * 1024 * 1024).build()
) : LoadControl by files {
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        if (radio()) parameters.bufferedDurationUs < 8_000_000 else files.shouldContinueLoading(parameters)
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        if (radio()) parameters.bufferedDurationUs >= 1_500_000 else files.shouldStartPlayback(parameters)
}
