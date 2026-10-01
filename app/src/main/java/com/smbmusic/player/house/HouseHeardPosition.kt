package com.smbmusic.player.house

import kotlin.math.abs

data class HouseHeardTrack(val file: String, val positionMs: Long)

/**
 * Approximate the rendered track from fresh MPD observations on the same monotonic clock.
 * Snapclient v0.31.0 controller.cpp uses buffer - server latency - player latency;
 * a positive effective offset therefore advances this timeline. MPD/FIFO lead, HTTP
 * sampling error and unreported Bluetooth/device latency still need hardware calibration.
 * This is not a PCM timestamp or an assurance that a ready receiver was audible.
 */
class HouseHeardPosition(private val maxAgeMs: Long = 10_000) {
    private data class Sample(val track: HouseTrack, val transport: String, val positionMs: Long, val at: Long)
    private data class Segment(var sample: Sample, val beginsAt: Long, var endsAt: Long? = null)
    private val history = ArrayList<Segment>()

    /** Call only for a successful fresh state poll, not for cached player/UI refreshes. */
    @Synchronized fun observe(state: HouseState, observedAtMs: Long) {
        val previous = history.lastOrNull()?.sample
        if (previous != null && observedAtMs < previous.at) return
        val track = state.tracks.firstOrNull { it.id == state.songId }
        if (track == null || track.file.isBlank()) {
            history.clear() // Never reuse a previous queue's song after stop/clear.
            return
        }
        val next = Sample(track, state.transport, state.positionMs.coerceAtLeast(0), observedAtMs)
        val elapsed = previous?.let { observedAtMs - it.at } ?: 0
        val sameTrack = previous != null && previous.track.id == track.id && previous.track.file == track.file
        val uninterrupted = previous != null && sameTrack && next.transport == previous.transport &&
            abs(next.positionMs - previous.positionMs - (if (next.transport == "play") elapsed else 0)) <= 1_000
        if (uninterrupted) {
            history.last().sample = next
            return
        }

        // A newly observed song near its beginning supplies the boundary even in shuffle.
        // A seek/resume's precise instant is unknown between polls: leave that gap unknown.
        val startsFromBeginning = next.transport == "play" && (previous == null ||
            (previous.transport == "play" && next.positionMs <= elapsed && (!sameTrack ||
                (previous.track.durationMs > 0 &&
                    abs(previous.positionMs + elapsed - previous.track.durationMs - next.positionMs) <= 1_000))))
        val beginsAt = if (startsFromBeginning) observedAtMs - next.positionMs else observedAtMs
        history.lastOrNull()?.endsAt = if (startsFromBeginning) beginsAt else previous!!.at
        history.add(Segment(next, beginsAt))
        // Only identities around recent boundaries are retained; never copy the HOUSE queue.
        while (history.size > 32) history.removeAt(0)
    }

    /**
     * Null means timing/identity is not established. Do not invent a shuffled successor.
     * Callers may retain their last valid audible checkpoint, but decide play intent
     * separately using current Bluetooth, mute, transport and receiver state.
     */
    @Synchronized fun snapshot(nowMs: Long, timing: HouseAudioTiming, effectiveOffsetMs: Int): HouseHeardTrack? {
        val latest = history.lastOrNull()?.sample ?: return null
        if (nowMs < latest.at || nowMs - latest.at > maxAgeMs) return null
        if (latest.transport != "play") return HouseHeardTrack(latest.track.file, bounded(latest))
        val buffer = timing.bufferMs ?: return null
        val delay = (buffer.toLong() - timing.serverLatencyMs - effectiveOffsetMs).coerceAtLeast(0)
        val sourceAt = nowMs - delay
        for (segment in history.asReversed()) {
            if (sourceAt < segment.beginsAt) continue
            if (segment.endsAt?.let { sourceAt >= it } == true) return null
            val sample = segment.sample
            if (sample.transport != "play") return null
            val position = sample.positionMs + sourceAt - sample.at
            if (position < 0 || (sample.track.durationMs > 0 && position >= sample.track.durationMs)) return null
            return HouseHeardTrack(sample.track.file, position)
        }
        return null
    }

    private fun bounded(sample: Sample): Long = if (sample.track.durationMs > 0)
        sample.positionMs.coerceAtMost(sample.track.durationMs) else sample.positionMs
}
