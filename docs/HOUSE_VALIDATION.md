# v0.4.0 HOUSE checkpoint

This is the first Android HOUSE build. The Pi's v0.8.1 restart with an already-powered S3 is field-proven; phone rendering and controller transitions are not yet field-proven.

## Install and configure once

1. Update the Pi service to **v0.8.2** for the guarded queue-sort endpoint. Other HOUSE endpoints use the existing v0.8.1 contract. Use the server repository's existing install/update procedure.
2. Install **SMBMusicPlayer-debug** from the Android build artifacts. Its matching **SMBMusicPlayer-v0.4.0-source** archive includes the bundled receiver's corresponding source and build files.
3. In the existing **SMB** connection panel, keep the saved SMB credentials and enter the Pi's **LAN address** in the optional House server field. Save, then Quit from Now Playing and reopen. Allow notifications so the foreground controller remains visible. Long-press the MP3s/Rap button in HOUSE to reopen connection settings.

The app probes MPD port 6600 through a non-VPN Wi-Fi/Ethernet Network before normal Browser startup. HOUSE HTTP and audio also use that selected LAN. Tailscale may remain connected. A failed connection after HOUSE selection stays HOUSE/reconnecting; this build does not implement live home/away handoff.

## One phone/S3 acceptance session

- **Silent opening:** with the S3 playing, open the app. Now Playing adopts the current house song/position and says HOUSE/output muted. Browse/search/sort the folder view: the house song and queue stay unchanged.
- **Synchronized output:** tap Unmute Output. Compare phone and S3 audio; record any consistent offset or drifting/echo. Mute the phone: the S3 continues. Unmute again: join the current position, without old audio. Check headphone/Bluetooth/call interruptions affect only local output.
- **Existing controls:** song tap and PLAY LIST select the ordered/filtered folder queue and unmute the phone. Play from pause/stop unmutes; Next/Previous during playback do not unmute a muted phone. Seek/Shuffle/Repeat and notification/Bluetooth controls target MPD. Sort Now Playing while paused and while playing; preserve song, position, state, and shuffle. Change MP3s/Rap: the current queue is unaffected.
- **Controller lifecycle:** mute the phone and turn the last S3 off. MPD should pause and retain its position. With the screen off, wait beyond fifteen seconds; the foreground phone must still hold the session. Unmute or power the S3 on to resume it. Quit with the S3 playing: only the phone detaches. Quit as the last muted controller: the retained session ends. Force-stop/drop the phone network to exercise lease expiry.
- **Fresh/reconnect cases:** phone alone into fresh idle stays idle until explicit playback. Restart the control service: the phone gets a new lease, preserves local mute intent, and adopts fresh state without uploading an old queue. A short radio absence before song end still preserves the running session. Check brief control/audio outages do not start competing SMB playback. Include a failed heartbeat followed by a successful renewal before the lease expires: HOUSE controls must become available again without Quit/reopen.
- **Standalone/UI regression:** with HOUSE unavailable at initial startup (or its optional address blank), test the existing SMB/Tailscale player, Country Buffer/recovery, Android Auto/Garmin, metadata, fade, search X, portrait layout, scroll restoration, Repeat All, and Quit. Both Browser button rows should align with Search; only the current folder name is shown; SMB/Parent Folder and PLAY LIST/Sort are swapped.

## Later in the agreed sequence

Live home/away departure, last-heard-position SMB continuation, and return-to-idle-house behavior remain the next recovery slice. Grace timing/whole-queue continuation choices remain open. Close/reopen selects the startup backend in this build. Cold launch through an external media browser remains part of later mode-selection work; launch SMB Music normally to establish the target for this checkpoint. Server artwork is unavailable in the current API, so HOUSE shows supplied text metadata and filename fallback.

Build and unit checks establish source/package consistency, not audible synchronization or real Android background behavior. Record phone model/Android version, server version, relevant /state and /controllers output, and the audible result before marking hardware checks complete.
