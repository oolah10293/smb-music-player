# v0.4.0 HOUSE checkpoint

This is the first Android HOUSE build. Server v0.8.2 is installed on the Pi. Android v0.4.0 has completed the first startup/state portion of the phone checkpoint: after exposing MPD on the home LAN, the app entered HOUSE with Tailscale off and Now Playing adopted the current MPD track. Phone rendering/controller transitions are not yet field-proven.

Test the final `9c89b24` APK, including heartbeat recovery, with server v0.8.2 (`9c98973`). Both CI runs passed. The first phone pass found a Tailscale/network-binding defect plus two mute/UI corrections; see the known-findings section below before continuing the audible checkpoint. [Release record, downloads and checksums](RELEASE_0.4.0.md).

## Install and configure once

1. Update the Pi service to **v0.8.2** for the guarded queue-sort endpoint. MPD must listen on localhost and the configured home-LAN interface/address; localhost-only MPD cannot satisfy the HOUSE identity check. Keep the private deployment address out of public source/docs.
2. Install the delivered **SMBMusicPlayer-v0.4.0.apk**, or extract `app-debug.apk` from the exact **SMBMusicPlayer-debug** artifact linked in the release record; they are identical. Its matching **SMBMusicPlayer-v0.4.0-source** archive includes the bundled receiver's corresponding source and build files.
3. In the existing **SMB** connection panel, keep the saved SMB credentials and enter the Pi's **LAN address** in the optional House server field. Save, then Quit from Now Playing and reopen. Allow notifications so the foreground controller remains visible. Long-press the MP3s/Rap button in HOUSE to reopen connection settings.

The delivered v0.4.0 build probes and carries HOUSE traffic through an explicitly selected non-VPN Android `Network`. Real testing showed that approach stalls when Tailscale is enabled even though the Pi remains reachable through normal Android routing. The required correction is to use the physical non-VPN Wi-Fi/Ethernet network/routes only to qualify and monitor **home presence**, while MPD/HTTP/Snapcast traffic uses normal Android routing. Tailscale must be allowed to remain connected. A failed connection after HOUSE selection stays HOUSE/reconnecting while the qualifying home network is still present; live home/away handoff remains a later slice.

## One phone/S3 acceptance session

- **Silent opening:** with the S3 playing, open the app. Now Playing adopts the current house song/position and says HOUSE/output muted. Browse/search/sort the folder view: the house song and queue stay unchanged.
- **Synchronized output:** the Mute/Unmute control must be in the **lower Media3 control strip beside the existing transport/Shuffle/Repeat/time controls**. Tap Unmute Output there. Compare phone and S3 audio; record any consistent offset or drifting/echo. Mute the phone: the S3 continues. Unmute again: join the current position, without old audio. Check headphone/Bluetooth/call interruptions affect only local output.
- **Existing controls:** song tap and PLAY LIST use the pre-command audible-house state: if MPD is already playing with at least one other audible output, a muted phone stays muted; if nothing is audibly playing (including `audibleCount == 0` while MPD is technically playing), the initiating muted phone auto-unmutes. Play from pause/stop also auto-unmutes; Next/Previous during already-audible playback do not. Seek/Shuffle/Repeat and notification/Bluetooth controls target MPD. Sort Now Playing while paused and while playing; preserve song, position, state, and shuffle. Change MP3s/Rap: the current queue is unaffected.
- **Controller lifecycle:** mute the phone and turn the last S3 off. MPD should pause and retain its position. With the screen off, wait beyond fifteen seconds; the foreground phone must still hold the session. Unmute or power the S3 on to resume it. Quit with the S3 playing: only the phone detaches. Quit as the last muted controller: the retained session ends. Force-stop/drop the phone network to exercise lease expiry.
- **Fresh/reconnect cases:** phone alone into fresh idle stays idle until explicit playback. Restart the control service: the phone gets a new lease, preserves local mute intent, and adopts fresh state without uploading an old queue. A short radio absence before song end still preserves the running session. Check brief control/audio outages do not start competing SMB playback. Include a failed heartbeat followed by a successful renewal before the lease expires: HOUSE controls must become available again without Quit/reopen.
- **Standalone/UI regression:** with HOUSE unavailable at initial startup (or its optional address blank), test the existing SMB/Tailscale player, Country Buffer/recovery, Android Auto/Garmin, metadata, fade, search X, portrait layout, scroll restoration, Repeat All, and Quit. Both Browser button rows should align with Search; only the current folder name is shown; SMB/Parent Folder and PLAY LIST/Sort are swapped.

## Later in the agreed sequence

Live home/away departure, last-heard-position SMB continuation, and return-to-idle-house behavior remain the next recovery slice. Grace timing/whole-queue continuation choices remain open. Close/reopen selects the startup backend in this build. Cold launch through an external media browser remains part of later mode-selection work; launch SMB Music normally to establish the target for this checkpoint. Server artwork is unavailable in the current API, so HOUSE shows supplied text metadata and filename fallback.

Build and unit checks establish source/package consistency, not audible synchronization or real Android background behavior. Record phone model/Android version, server version, relevant /state and /controllers output, and the audible result before marking hardware checks complete.


## Known findings from the first phone pass

These are already observed on real hardware and should not be re-diagnosed as unknowns:

- Server v0.8.2 is installed and healthy.
- MPD was originally loopback-only. After enabling its LAN listener, the remote MPD port became reachable and Android entered HOUSE with Tailscale off.
- Now Playing adopted the song already playing on MPD.
- Turning Tailscale on while HOUSE was active caused app state updates to stop.
- The phone browser could still read the Pi's `/health` JSON with Tailscale on or off, so the Pi/LAN remained reachable.
- Therefore the current explicit Android-`Network` binding for HOUSE HTTP/audio is the defect to replace; do not work around it by requiring Tailscale to be turned off.
- Starting a new playlist currently unmutes the phone. That is now explicitly wrong: selected-track and PLAY LIST starts preserve local mute only when MPD was already playing with another audible output; otherwise the initiating phone auto-unmutes.
- The current separate Mute/Unmute button is also not the desired UI. It belongs in the lower Media3 controller strip.

After those corrections, repeat the checkpoint with **Tailscale already on before app launch** and again by toggling Tailscale while HOUSE is active. HOUSE control/state and synchronized audio must remain functional while the physical home Wi-Fi/Ethernet network remains present.
