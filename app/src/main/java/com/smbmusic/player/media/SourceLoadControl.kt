package com.smbmusic.player.media

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection

/** One allocator and the existing file policy, with short live buffering for HTTP radio. */
@UnstableApi
class SourceLoadControl(private val radio: () -> Boolean,
    private val files: DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(120_000, 600_000, 3_000, 20_000)
        .setPrioritizeTimeOverSizeThresholds(true).setTargetBufferBytes(32 * 1024 * 1024).build()
) : LoadControl {
    // Explicit forwarding is required: Kotlin delegation does not override Java default methods.
    override fun onPrepared(playerId: PlayerId) = files.onPrepared(playerId)
    override fun onTracksSelected(parameters: LoadControl.Parameters, trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>) = files.onTracksSelected(parameters, trackGroups, trackSelections)
    override fun onStopped(playerId: PlayerId) = files.onStopped(playerId)
    override fun onReleased(playerId: PlayerId) = files.onReleased(playerId)
    override fun getAllocator(playerId: PlayerId) = files.getAllocator(playerId)
    override fun getBackBufferDurationUs(playerId: PlayerId) = files.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: PlayerId) = files.retainBackBufferFromKeyframe(playerId)
    override fun shouldContinuePreloading(playerId: PlayerId, timeline: Timeline,
        mediaPeriodId: MediaPeriodId, bufferedDurationUs: Long) =
        files.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        if (radio()) parameters.bufferedDurationUs < 8_000_000 else files.shouldContinueLoading(parameters)
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        if (radio()) parameters.bufferedDurationUs >= 1_500_000 else files.shouldStartPlayback(parameters)
}
