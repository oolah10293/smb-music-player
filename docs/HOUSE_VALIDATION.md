# v0.4.1 HOUSE correction checkpoint

v0.4.1 implements corrections from the first Android HOUSE test. Server v0.8.2 is already installed and healthy on the Pi. v0.4.0 proved HOUSE entry and MPD-track adoption with Tailscale off after MPD's LAN listener was enabled. Phone rendering/controller transitions remain unproven. See the [v0.4.1 release record](RELEASE_0.4.1.md) for exact build verification and artifacts; the [v0.4.0 record](RELEASE_0.4.0.md) is historical.

## Install and configure once

1. The Pi service is already on **v0.8.2**; keep it for the guarded queue-sort endpoint. No server update or ESP32 firmware change is needed for this Android correction. MPD must listen on localhost and the configured home-LAN interface/address; localhost-only MPD cannot satisfy the HOUSE identity check. Keep the private deployment address out of public source/docs.
2. Install the delivered **SMBMusicPlayer-v0.4.1.apk**, or extract `app-debug.apk` from the exact **SMBMusicPlayer-debug** artifact linked in the release record; they are identical. Its matching **SMBMusicPlayer-v0.4.1-source** archive includes the bundled receiver's corresponding source and build files.
This debug APK has a different signing key from the delivered v0.4.0. Save the SMB connection details and House address before uninstalling the old app; a fresh install clears its settings. See the [signing record](RELEASE_0.4.1.md#installation-and-signing).

3. In the existing **SMB** connection panel, re-enter SMB credentials after a fresh install and enter the Pi's **LAN address** in the optional House server field. Save, then Quit from Now Playing and reopen. Allow notifications so the foreground controller remains visible. Long-press the MP3s/Rap button in HOUSE to reopen connection settings.

The v0.4.1 build requires a directly connected route to the configured Pi address on non-VPN Wi-Fi/Ethernet, then checks MPD and house-service identity using normal Android routing. HTTP and Snapcast use normal routing too. The physical network/route is monitored; losing it closes local audio and suspends HOUSE traffic/heartbeats, even when the Pi remains reachable over VPN. This slice stays HOUSE/reconnecting; automatic SMB handoff is still later work. Server-only failures while home remains present are recovery, not departure.

## Current result tally

Already observed on the real phone:

- **PASS:** HOUSE works with Tailscale connected.
- **PASS:** output/mute icon appearance and location are correct in the lower Media3 strip.
- **PASS:** with an S3 already audible, changing song/PLAY LIST from a muted phone changes the S3/shared queue and the phone stays muted.
- **FAIL / open:** phone/S3 sync; phone output was observed about **1 second behind** the S3.
- **PASS:** leaving the qualifying home Wi-Fi correctly makes v0.4.1 recognize the home network as unavailable and stop HOUSE operation even with cellular/Tailscale available.
- **EXPECTED FAIL / not implemented yet:** the phone did **not** continue the playing HOUSE track over standalone SMB/Tailscale after driving away. The app remained in `HOUSE — home network unavailable; reconnecting`, which matches the current v0.4.1 implementation boundary. Automatic same-song home→away continuation is the next recovery slice.

These are acceptance results only. Do not treat the sync failure as diagnosed yet.

## First: remaining corrected phone cases

- Launch at home with **Tailscale already on**, then toggle Tailscale off/on while HOUSE is active. Track/position updates, browse and control must keep working. Unmute and check audio recovery too.
- Remove the qualifying Wi-Fi/Ethernet route while Tailscale can still reach the Pi: local HOUSE audio stops, controls become unavailable and controller heartbeats cease. Rejoin home and verify recovery. No competing SMB playback should start. Cold launch away from home with only VPN reachability must select STANDALONE.
- With an S3 playing and phone muted, tap a different song and PLAY LIST: the shared queue changes but phone stays muted. Repeat with multiple audible outputs.
- With MPD paused/stopped, song/PLAY LIST and explicit Play auto-unmute the phone. With MPD playing but zero audible outputs, song/PLAY LIST auto-unmutes; a connected but inaudible renderer must not prevent this. An already-unmuted phone stays unmuted.
- Browse, browser sort, active queue sort and Next/Previous during playback preserve mute. A newer manual mute during a pending command must win; failed/uncertain writes must not auto-unmute or replay.
- Confirm the speaker icon is **inside the bottom Media3 strip**, with Shuffle/Repeat/time, and works on the target phone width. Standalone has no output icon. Check Previous/Play/Next and seeking remain visible and usable.
- **Bluetooth route policy (new, not yet implemented/accepted):** while HOUSE is active, disconnecting Bluetooth must mute the phone without issuing MPD Pause/Stop. If another audible node remains, playback continues; if the phone was the only audible node, the server auto-pauses/retains while the phone controller remains connected. Bluetooth reconnect while music is already playing elsewhere must auto-unmute/rejoin even after a prior manual mute. Bluetooth connection alone must not start an idle/paused house session; if the user then starts music from that phone, the phone should be unmuted for that deliberate start.
- **Bluetooth output policy:** while an S3/other audible node is playing, connect Bluetooth to the phone: the phone auto-unmutes and joins HOUSE. Disconnect Bluetooth: the phone mutes locally, sends no Pause/Stop, and the other node keeps playing. Repeat with the phone as the only node: Bluetooth disconnect must mute the phone and cause the server's existing muted-only policy to pause/retain the exact session; reconnecting Bluetooth must auto-unmute and resume that retained automatic pause. Also verify Bluetooth connect overrides a prior manual phone mute, but does not start a deliberately paused/stopped or fresh-idle MPD session by itself.

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

These v0.4.0 observations explain the v0.4.1 corrections; the fixes still require the repeat checks above:

- Server v0.8.2 is installed and healthy.
- MPD was originally loopback-only. After enabling its LAN listener, the remote MPD port became reachable and Android entered HOUSE with Tailscale off.
- Now Playing adopted the song already playing on MPD.
- Turning Tailscale on while HOUSE was active caused app state updates to stop.
- The phone browser could still read the Pi's `/health` JSON with Tailscale on or off, so the Pi/LAN remained reachable.
- The v0.4.0 explicit Android-`Network` binding for HOUSE HTTP/audio was the defect addressed in v0.4.1; do not work around it by requiring Tailscale to be turned off.
- Starting a new playlist in v0.4.0 unconditionally unmuted the phone. The corrected rule is: selected-track and PLAY LIST starts preserve local mute only when MPD was already playing with another audible output; otherwise the initiating phone auto-unmutes.
- The separate v0.4.0 Mute/Unmute button was not the desired UI. v0.4.1 places its icon inside the lower Media3 controller strip.

After those corrections, repeat the checkpoint with **Tailscale already on before app launch** and again by toggling Tailscale while HOUSE is active. HOUSE control/state and synchronized audio must remain functional while the physical home Wi-Fi/Ethernet network remains present.
