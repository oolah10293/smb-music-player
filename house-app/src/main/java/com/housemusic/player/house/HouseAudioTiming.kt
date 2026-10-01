package com.housemusic.player.house

/** Snapclient --latency is device-delay compensation: positive values play earlier. */
data class HouseAudioTiming(val bufferMs: Int? = null, val serverLatencyMs: Int = 0) {
    fun effectiveOffset(requestedMs: Int): Int {
        val bounded = requestedMs.coerceIn(-2000, 2000)
        // Retain at least 200 ms for the OpenSL callback and network/clock correction.
        // Until the actual server settings arrive, apply no positive advance.
        val maxAdvance = bufferMs?.let { (it.toLong() - serverLatencyMs.toLong() - 200).coerceIn(0L, 2000L).toInt() } ?: 0
        return bounded.coerceAtMost(maxAdvance)
    }

    companion object {
        private val settings = Regex("ServerSettings - buffer: (\\d+), latency: (-?\\d+)")
        fun fromLog(line: String): HouseAudioTiming? {
            val match = settings.find(line) ?: return null
            val buffer = match.groupValues[1].toIntOrNull() ?: return null
            val latency = match.groupValues[2].toIntOrNull() ?: return null
            return HouseAudioTiming(buffer, latency)
        }
    }
}
