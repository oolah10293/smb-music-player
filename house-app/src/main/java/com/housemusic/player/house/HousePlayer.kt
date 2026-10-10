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
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP)
        if (house.canControl && state.canShuffle) commands.add(Player.COMMAND_SET_SHUFFLE_MODE)
        if (house.canControl && state.canRepeat) commands.add(Player.COMMAND_SET_REPEAT_MODE)
        if (house.canControl && state.canSeek) commands.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        if (house.canControl && state.canSkip) commands.addAll(Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        val playlist = state.tracks.map { track ->
            val filename = track.file.substringAfterLast('/')
            val title = if (state.isRadio) track.title.ifBlank { state.radioStationName.ifBlank { "Live radio" } }
                else track.title.ifBlank { AudioFormats.titleFromFilename(filename) }
            val artist = if (state.isRadio) listOf(track.artist, state.radioStationName)
                .filter { it.isNotBlank() && it != title }.distinct().joinToString(" · ") else track.artist
            val metadata = MediaMetadata.Builder()
                .setTitle(title).setArtist(artist).setAlbumTitle(if (state.isRadio) "" else track.album)
                .setExtras(Bundle().apply {
                    putString(MainActivity.EXTRA_FILENAME, if (state.isRadio) "" else filename)
                    putLong(MainActivity.EXTRA_MODIFIED, track.modified)
                    putBoolean(HouseRuntime.EXTRA_RADIO, state.isRadio)
                    putString(HouseRuntime.EXTRA_RADIO_STATION_NAME, state.radioStationName)
                    putString(EXTRA_RADIO_TRACK_TITLE, if (state.isRadio) track.title else "")
                    putString(EXTRA_RADIO_TRACK_ARTIST, if (state.isRadio) track.artist else "")
                }).build()
            MediaItemData.Builder(track.id).setMediaItem(MediaItem.Builder()
                .setMediaId(track.file).setMediaMetadata(metadata).build())
                .setIsSeekable(!state.isRadio && state.supportsSeek)
                .setIsDynamic(state.isRadio)
                .setLiveConfiguration(if (state.isRadio) MediaItem.LiveConfiguration.Builder().build() else null)
                .setDurationUs(if (!state.isRadio && track.durationMs > 0) track.durationMs * 1000 else C.TIME_UNSET).build()
        }
        val radioActive = state.radioPlayIntent == "play" && state.radioStatus in setOf("playing", "connecting", "retrying")
        val playing = (if (state.isRadio) radioActive else state.transport == "play") && house.connected && state.ready
        val playbackState = when {
            playlist.isEmpty() -> Player.STATE_IDLE
            state.isRadio && state.radioStatus in setOf("connecting", "retrying") && radioActive -> Player.STATE_BUFFERING
            state.isRadio && state.radioStatus in setOf("playing", "paused") -> Player.STATE_READY
            state.isRadio || state.transport == "stop" -> Player.STATE_IDLE
            else -> Player.STATE_READY
        }
        return State.Builder().setAvailableCommands(commands.build()).setPlaylist(playlist)
            .setCurrentMediaItemIndex(if (playlist.isEmpty()) C.INDEX_UNSET else state.index)
            .setContentPositionMs(state.positionMs.coerceAtLeast(0))
            .setPlaybackState(playbackState)
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
        if (!house.state.canShuffle) Futures.immediateVoidFuture()
        else house.command("/shuffle", JSONObject().put("enabled", shuffleModeEnabled))
    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> =
        if (!house.state.canRepeat) Futures.immediateVoidFuture()
        else house.command("/repeat", JSONObject().put("enabled", repeatMode != Player.REPEAT_MODE_OFF))
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (house.state.isRadio) return Futures.immediateVoidFuture()
        return when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                if (house.state.canSkip) house.command("/next") else Futures.immediateVoidFuture()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                if (house.state.canSkip) house.command("/previous") else Futures.immediateVoidFuture()
            else -> if (house.state.canSeek) house.command("/seek", JSONObject().put("seconds", positionMs.coerceAtLeast(0) / 1000.0))
                else Futures.immediateVoidFuture()
        }
    }
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    companion object {
        const val EXTRA_RADIO_TRACK_TITLE = "house.radioTrackTitle"
        const val EXTRA_RADIO_TRACK_ARTIST = "house.radioTrackArtist"
    }
}
