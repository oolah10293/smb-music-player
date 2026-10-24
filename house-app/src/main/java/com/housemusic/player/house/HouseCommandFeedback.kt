package com.housemusic.player.house

import java.io.IOException

/** Command failures survive read-only polling, until the next deliberate house command. */
class HouseCommandFeedback {
    @Volatile private var failure: String? = null

    fun clear() { failure = null }

    fun failed(path: String, error: Exception) {
        val action = when (path) {
            "/radio/resume-playlist" -> "Resume Playlist"
            "/radio/play" -> "Start station"
            "/play" -> "Play"
            "/pause" -> "Pause"
            "/stop" -> "Stop"
            else -> "Playback command"
        }
        val detail = when (error) {
            is HouseApiException -> clean(error.message.orEmpty()).ifBlank { "Server error ${error.status}." }
            is IOException -> "Connection interrupted."
            else -> "Could not confirm the server response."
        }
        failure = "$action failed: $detail Check playback before trying again."
    }

    fun status(resume: PlaylistResume, fallback: String, canControl: Boolean): String {
        // A stale snapshot or command error must not hide an actual connection problem.
        if (!canControl) return fallback
        if (resume.status == "uncertain") {
            val detail = clean(resume.error)
            return "Playlist restore could not be confirmed." +
                (if (detail.isBlank()) "" else " $detail") + " Select a station or folder."
        }
        return failure ?: fallback
    }

    private fun clean(value: String) = value.replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(240)
}
