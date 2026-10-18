package com.housemusic.player.house

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject

/** The Pi owns the saved queue. A phone holds only its opaque request ID. */
data class PlaylistResume(
    val available: Boolean = false,
    val snapshotId: String = "",
    val status: String = "none",
    val error: String = "",
) {
    val canResume: Boolean get() = available && status == "ready" &&
        snapshotId.matches(Regex("[A-Za-z0-9_-]{32}"))

    companion object {
        fun parse(source: JSONObject?): PlaylistResume {
            val resume = source?.optJSONObject("resumePlaylist") ?: return PlaylistResume()
            return PlaylistResume(resume.optBoolean("available"),
                if (resume.isNull("snapshotId")) "" else resume.optString("snapshotId"),
                resume.optString("status", "none"),
                if (resume.isNull("lastError")) "" else resume.optString("lastError"))
        }
    }
}

@UnstableApi
internal fun HousePlaybackState.resumePlaylist(snapshotId: String): ListenableFuture<SessionResult> {
    val current = state
    if (!canControl || !current.isRadio || !current.playlistResume.canResume ||
        snapshotId != current.playlistResume.snapshotId) {
        return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
    }
    return command("/radio/resume-playlist", JSONObject().put("snapshotId", snapshotId))
}
