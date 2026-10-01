package com.smbmusic.player.storage

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.smbmusic.player.MainActivity
import org.json.JSONArray
import org.json.JSONObject

data class StandaloneSnapshot(val items: List<MediaItem>, val index: Int, val positionMs: Long,
                              val shuffle: Boolean, val explicitlyStopped: Boolean)

/** Retained state is local to this installation; credentials stay in CredentialStore. */
@UnstableApi
class StandaloneSessionStore(context: Context) {
    private val file = java.io.File(context.noBackupFilesDir, "standalone-session.json")
    private val journal = java.io.File(context.noBackupFilesDir, "house-handoff.json")

    fun load(): StandaloneSnapshot? = runCatching {
        val json = JSONObject(file.readText())
        val rows = json.getJSONArray("items")
        val items = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val uri = row.getString("uri")
            require(uri.startsWith("smb://"))
            val metadata = MediaMetadata.Builder().setTitle(row.optString("title"))
                .setArtist(row.optString("artist")).setAlbumTitle(row.optString("album"))
                .setExtras(Bundle().apply {
                    putString(MainActivity.EXTRA_FILENAME, row.optString("filename"))
                    putLong(MainActivity.EXTRA_MODIFIED, row.optLong("modified"))
                }).build()
            MediaItem.Builder().setMediaId(uri).setUri(uri).setMediaMetadata(metadata).build()
        }
        require(items.isNotEmpty())
        StandaloneSnapshot(items, json.optInt("index").coerceIn(items.indices),
            json.optLong("positionMs").coerceAtLeast(0), json.optBoolean("shuffle"),
            json.optBoolean("explicitlyStopped", true))
    }.getOrNull()

    fun save(snapshot: StandaloneSnapshot) {
        if (snapshot.items.isEmpty()) { file.delete(); return }
        val rows = JSONArray(snapshot.items.map { item ->
            val meta = item.mediaMetadata
            JSONObject().put("uri", item.localConfiguration?.uri?.toString() ?: item.mediaId)
                .put("title", meta.title?.toString().orEmpty()).put("artist", meta.artist?.toString().orEmpty())
                .put("album", meta.albumTitle?.toString().orEmpty())
                .put("filename", meta.extras?.getString(MainActivity.EXTRA_FILENAME).orEmpty())
                .put("modified", meta.extras?.getLong(MainActivity.EXTRA_MODIFIED) ?: 0)
        })
        write(file, JSONObject().put("items", rows).put("index", snapshot.index)
            .put("positionMs", snapshot.positionMs).put("shuffle", snapshot.shuffle)
            .put("explicitlyStopped", snapshot.explicitlyStopped))
    }

    @Synchronized fun handoffId(): String? = runCatching { JSONObject(journal.readText()).getString("handoffId") }.getOrNull()
    @Synchronized fun handoffStopRequested(): Boolean = runCatching { JSONObject(journal.readText()).optBoolean("stopRequested") }.getOrDefault(false)
    @Synchronized fun handoffStopSent(): Boolean = runCatching { JSONObject(journal.readText()).optBoolean("stopSent") }.getOrDefault(false)
    @Synchronized fun saveHandoff(id: String, stopRequested: Boolean = false) =
        write(journal, JSONObject().put("handoffId", id).put("stopRequested", stopRequested))
    @Synchronized fun markStopSent(id: String): Boolean {
        if (handoffId() != id || handoffStopSent()) return false
        write(journal, JSONObject().put("handoffId", id).put("stopRequested", true).put("stopSent", true))
        return true
    }
    @Synchronized fun clearHandoff() { journal.delete() }

    private fun write(destination: java.io.File, value: JSONObject) {
        val temp = java.io.File(destination.parentFile, destination.name + ".tmp")
        java.io.FileOutputStream(temp).use { output ->
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        check(temp.renameTo(destination)) { "Could not retain playback state" }
    }
}
