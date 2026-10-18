package com.smbmusic.player.storage

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.smbmusic.player.MainActivity
import com.smbmusic.player.radio.RadioUrl
import org.json.JSONArray
import org.json.JSONObject

// Version 2 is backward compatible with the original flat SMB-only snapshot.
data class StandaloneSnapshot(val items: List<MediaItem>, val index: Int, val positionMs: Long,
    val shuffle: Boolean, val explicitlyStopped: Boolean, val shouldPlay: Boolean = !explicitlyStopped,
    val radio: Boolean = false, val stationId: String = "", val stationName: String = "",
    val playlist: StandaloneSnapshot? = null, val shuffleOrder: List<Int> = emptyList(), val bluetoothRequired: Boolean = false)

@UnstableApi
class StandaloneSessionStore(context: Context) {
    private val file = java.io.File(context.noBackupFilesDir, "standalone-session.json")
    fun isResumeEligible(): Boolean = load()?.let { !it.explicitlyStopped && it.items.isNotEmpty() } ?: false
    fun load(): StandaloneSnapshot? = runCatching { decode(JSONObject(file.readText())) }.getOrNull()
    private fun decode(json: JSONObject): StandaloneSnapshot = fromRecord(SessionRecord.decode(json))
    private fun fromRecord(record: SessionRecord): StandaloneSnapshot {
        val items = record.items.map { item ->
            val metadata = MediaMetadata.Builder().setArtist(item.artist).setAlbumTitle(item.album)
                .setExtras(Bundle().apply {
                    putString(MainActivity.EXTRA_FILENAME, item.filename)
                    putLong(MainActivity.EXTRA_MODIFIED, item.modified)
                })
            if (item.title.isNotBlank()) metadata.setTitle(item.title)
            MediaItem.Builder().setMediaId(item.id).setUri(item.uri)
                .setMimeType(item.mime.takeIf { it.isNotBlank() }).setMediaMetadata(metadata.build()).build()
        }
        return StandaloneSnapshot(items, record.index, record.positionMs, record.shuffle, record.stopped,
            record.shouldPlay, record.radio, record.stationId, record.stationName,
            record.playlist?.let(::fromRecord), record.shuffleOrder, record.bluetoothRequired)
    }
    private fun toRecord(s: StandaloneSnapshot): SessionRecord = SessionRecord(s.items.map { item ->
        val meta = item.mediaMetadata
        SessionItem(item.localConfiguration?.uri?.toString() ?: item.mediaId, item.mediaId,
            item.localConfiguration?.mimeType.orEmpty(), meta.title?.toString().orEmpty(),
            meta.artist?.toString().orEmpty(), meta.albumTitle?.toString().orEmpty(),
            meta.extras?.getString(MainActivity.EXTRA_FILENAME).orEmpty(),
            meta.extras?.getLong(MainActivity.EXTRA_MODIFIED) ?: 0)
    }, s.index, s.positionMs, s.shuffle, s.explicitlyStopped, s.shouldPlay, s.radio,
        s.stationId, s.stationName, s.playlist?.let(::toRecord), s.shuffleOrder, s.bluetoothRequired)
    private fun encode(s: StandaloneSnapshot): JSONObject = toRecord(s).encode()
    fun save(snapshot: StandaloneSnapshot) {
        if (snapshot.items.isEmpty()) { file.delete(); return }
        val temp = java.io.File(file.parentFile, file.name + ".tmp")
        java.io.FileOutputStream(temp).use { output ->
            output.write(encode(snapshot).toString().toByteArray(Charsets.UTF_8)); output.fd.sync()
        }
        check(temp.renameTo(file)) { "Could not retain playback state" }
    }
}
