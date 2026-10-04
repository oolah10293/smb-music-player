package com.housemusic.player.house

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.housemusic.player.MainActivity
import com.housemusic.player.media.AudioFormats
import org.json.JSONObject

/** Media3 adapter for the server-owned MPD queue and transport. */
@UnstableApi
class HousePlayer(private val house: HousePlaybackState) : SimpleBasePlayer(Looper.getMainLooper()) {
    fun refresh() = invalidateState()

    // Keep showing confirmed server state while a command is pending. In particular,
    // a rejected Next must not leave a guessed next track/position or transport behind.
    override fun getPlaceholderState(suggestedPlaceholderState: State): State = getState()

    override fun getState(): State {
        // The last successful poll is not a live queue during a lost connection.
        val state = if (house.connected) house.state else HouseState()
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_RELEASE)
        if (house.canControl) commands.addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_REPEAT_MODE)
        if (house.canControl && state.canNavigate) commands.addAll(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        val playlist = state.tracks.map { track ->
            val filename = track.file.substringAfterLast('/')
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title.ifBlank { AudioFormats.titleFromFilename(filename) })
                .setArtist(track.artist).setAlbumTitle(track.album)
                .setExtras(Bundle().apply {
                    putString(MainActivity.EXTRA_FILENAME, filename)
                    putLong(MainActivity.EXTRA_MODIFIED, track.modified)
                }).build()
            MediaItemData.Builder(track.id).setMediaItem(MediaItem.Builder()
                .setMediaId(track.file).setMediaMetadata(metadata).build())
                .setIsSeekable(true).setDurationUs(if (track.durationMs > 0) track.durationMs * 1000 else C.TIME_UNSET).build()
        }
        val playing = state.transport == "play" && house.connected && state.ready
        return State.Builder().setAvailableCommands(commands.build()).setPlaylist(playlist)
            .setCurrentMediaItemIndex(if (playlist.isEmpty()) C.INDEX_UNSET else state.index)
            .setContentPositionMs(state.positionMs.coerceAtLeast(0))
            .setPlaybackState(if (playlist.isEmpty() || state.transport == "stop") Player.STATE_IDLE else Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setShuffleModeEnabled(state.shuffle)
            .setRepeatMode(if (state.repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        return house.command(if (playWhenReady) "/play" else "/pause")
    }
    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleStop(): ListenableFuture<*> = house.command("/stop")
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> =
        house.command("/shuffle", JSONObject().put("enabled", shuffleModeEnabled))
    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> =
        house.command("/repeat", JSONObject().put("enabled", repeatMode != Player.REPEAT_MODE_OFF))
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> = when (seekCommand) {
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> house.command("/next")
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> house.command("/previous")
        else -> house.command("/seek", JSONObject().put("seconds", positionMs.coerceAtLeast(0) / 1000.0))
    }
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
