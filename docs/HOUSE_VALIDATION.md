# v0.4.2 HOUSE correction checkpoint

v0.4.2 implements immediate service-owned HOUSE Quit cleanup, event-triggered identity/control reacquisition with browser retry reset, and the approved Bluetooth local-output policy. Holding the existing output icon opens separate phone/wired and Bluetooth sync adjustments with reported buffer/latency diagnostics. The offset defaults to zero; the roughly one-second phone/S3 delay remains undiagnosed and needs physical measurement.

Server v0.8.2 stays installed. Build results and exact APK/source artifacts are recorded in [RELEASE_0.4.2.md](RELEASE_0.4.2.md). **v0.4.2 now has partial physical results; do not treat untested cases as passing.**

## Install and configure

Install `SMBMusicPlayer-v0.4.2.apk`; follow the release record's signing note. A different debug certificate requires saving the SMB/House settings before uninstalling the older app, then re-entering them after installation. Keep MPD listening on localhost and the configured LAN interface. The optional House address is entered locally in the existing SMB panel; do not publish private deployment addresses. Allow notifications.

The physical non-VPN direct route still qualifies home; identity, control and audio traffic use normal Android routing. A qualifying route event triggers an immediate debounced probe, not proof of availability. No competing standalone audio starts during HOUSE recovery. Automatic departure continuation remains unimplemented pending the recorded grace/queue/idle-return/heard-position decisions.

## v0.4.2 first tests

1. **Quit/reopen:** while the S3 is playing, Quit HOUSE. Phone audio, notification and local track/queue/position must disappear without MPD Stop/Clear. Reopen immediately and after several seconds, both home and away. Reappearing music must follow a fresh server read; Quit during a slow startup/read must not revive old state.
2. **Return home:** reach the old 15-second retry state, then regain the qualifying physical route with Tailscale on. Verify prompt identity/control and browser attempts. Keep the Pi unavailable for a second pass: Wi-Fi alone must not report success. Verify recovery after the Pi returns without replaying uncertain writes.
3. **Bluetooth:** opening with an already-connected audio device remains muted. A subsequent new audio-device connection overrides manual mute locally; disconnection mutes locally. Neither event sends MPD Play/Pause/Stop. Other audible outputs continue; sole-phone muted-only automatic pause/resume remains server policy. Connection alone must preserve deliberate pause/stop/fresh idle. With Bluetooth connected, a deliberate Play/song/PLAY LIST unmutes even if another output is audible. Paired watches and input-only devices must not trigger this policy.
4. **Timing:** hold the output icon after unmuting. Record shared buffer, server latency and applied correction; start at zero. Compare phone/wired and Bluetooth separately. Positive advances the phone, negative delays it. Applying rejoins this phone only. Verify bounded advance and repeatability before treating an offset as a fix. See the server's [buffer trial](https://github.com/oolah10293/house-audio-server/blob/main/docs/HOUSE_BUFFER_TRIAL.md) for the optional 3000 ms experiment and rollback; it is not automatically deployed.
5. Repeat the established Tailscale, mute, background and standalone checks below. Preserve confirmed v0.4.1 passes while recording v0.4.2 results separately.

## Historical v0.4.1 field results

Already observed on the real phone:

- **PASS:** HOUSE works with Tailscale connected.
- **PASS:** output/mute icon appearance and location are correct in the lower Media3 strip.
- **PASS:** with an S3 already audible, changing song/PLAY LIST from a muted phone changes the S3/shared queue and the phone stays muted.
- **FAIL / open:** phone/S3 sync; phone output was observed about **1 second behind** the S3.
- **PASS:** leaving the qualifying home Wi-Fi correctly makes v0.4.1 recognize the home network as unavailable and stop HOUSE operation even with cellular/Tailscale available.
- **EXPECTED FAIL / not implemented yet:** the phone did **not** continue the playing HOUSE track over standalone SMB/Tailscale after driving away. The app remained in `HOUSE — home network unavailable; reconnecting`, which matches the current v0.4.1 implementation boundary. Automatic same-song home→away continuation is the next recovery slice.
- **FAIL:** explicit HOUSE Quit can leave the previous HOUSE song cached/displayed locally. Quit must clear phone-local HOUSE track/queue/position presentation while leaving the actual MPD session untouched for other nodes.
- **PARTIAL/FAIL:** return-home detection eventually recovers, but can sit on `HOUSE unavailable: Socket closed / Retrying in 15s...` after the phone is already back on the qualifying home Wi-Fi. Physical home-route gain must trigger an immediate real HOUSE probe/reconnect rather than waiting for the existing backoff timer.

These are acceptance results only. Do not treat the sync failure as diagnosed yet.

## First: remaining corrected phone cases

- Launch at home with **Tailscale already on**, then toggle Tailscale off/on while HOUSE is active. Track/position updates, browse and control must keep working. Unmute and check audio recovery too.
- Remove the qualifying Wi-Fi/Ethernet route while Tailscale can still reach the Pi: local HOUSE audio stops, controls become unavailable and controller heartbeats cease. Rejoin home and verify **immediate** recovery probing on physical route gain; do not wait for a stale 15-second retry timer. No competing SMB playback should start. Cold launch away from home with only VPN reachability must select STANDALONE.
- With an S3 playing and phone muted, tap a different song and PLAY LIST: the shared queue changes but phone stays muted. Repeat with multiple audible outputs.
- With MPD paused/stopped, song/PLAY LIST and explicit Play auto-unmute the phone. With MPD playing but zero audible outputs, song/PLAY LIST auto-unmutes; a connected but inaudible renderer must not prevent this. An already-unmuted phone stays unmuted.
- Browse, browser sort, active queue sort and Next/Previous during playback preserve mute. A newer manual mute during a pending command must win; failed/uncertain writes must not auto-unmute or replay.
- Confirm the speaker icon is **inside the bottom Media3 strip**, with Shuffle/Repeat/time, and works on the target phone width. Standalone has no output icon. Check Previous/Play/Next and seeking remain visible and usable.
- **Bluetooth route policy (implemented in v0.4.2, physical acceptance pending):** while HOUSE is active, disconnecting Bluetooth must mute the phone without issuing MPD Pause/Stop. If another audible node remains, playback continues; if the phone was the only audible node, the server auto-pauses/retains while the phone controller remains connected. Bluetooth reconnect while music is already playing elsewhere must auto-unmute/rejoin even after a prior manual mute. Bluetooth connection alone must not start an idle/paused house session; if the user then starts music from that phone, the phone should be unmuted for that deliberate start.
- **Bluetooth output policy:** while an S3/other audible node is playing, connect Bluetooth to the phone: the phone auto-unmutes and joins HOUSE. Disconnect Bluetooth: the phone mutes locally, sends no Pause/Stop, and the other node keeps playing. Repeat with the phone as the only node: Bluetooth disconnect must mute the phone and cause the server's existing muted-only policy to pause/retain the exact session; reconnecting Bluetooth must auto-unmute and resume that retained automatic pause. Also verify Bluetooth connect overrides a prior manual phone mute, but does not start a deliberately paused/stopped or fresh-idle MPD session by itself.

## One phone/S3 acceptance session

- **Silent opening:** with the S3 playing, open the app. Now Playing adopts the current house song/position and says HOUSE/output muted. Browse/search/sort the folder view: the house song and queue stay unchanged.
- **Synchronized output:** the Mute/Unmute control must be in the **lower Media3 control strip beside the existing transport/Shuffle/Repeat/time controls**. Tap Unmute Output there. Compare phone and S3 audio; record any consistent offset or drifting/echo. Mute the phone: the S3 continues. Unmute again: join the current position, without old audio. Check headphone/Bluetooth/call interruptions affect only local output.
- **Existing controls:** song tap and PLAY LIST use the pre-command audible-house state: if MPD is already playing with at least one other audible output, a muted phone stays muted; if nothing is audibly playing (including `audibleCount == 0` while MPD is technically playing), the initiating muted phone auto-unmutes. Play from pause/stop also auto-unmutes; Next/Previous during already-audible playback do not. Seek/Shuffle/Repeat and notification/Bluetooth controls target MPD. Sort Now Playing while paused and while playing; preserve song, position, state, and shuffle. Change MP3s/Rap: the current queue is unaffected.
- **Controller lifecycle:** mute the phone and turn the last S3 off. MPD should pause and retain its position. With the screen off, wait beyond fifteen seconds; the foreground phone must still hold the session. Unmute or power the S3 on to resume it. Quit with the S3 playing: only the phone detaches, and its local HOUSE Now Playing/cache is cleared; reopening must freshly adopt server state rather than resurrect cached metadata. Quit as the last muted controller: the retained session ends. Force-stop/drop the phone network to exercise lease expiry.
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


## v0.4.2 live field results — 2026-09-30

- **PASS:** Bluetooth connect automatically unmutes the HOUSE phone output.
- **PASS:** Bluetooth disconnect automatically mutes the HOUSE phone output.
- **PASS, route-specific:** **+400 ms** timing correction is audibly correct against the S3 on the currently tested phone/output path. Keep the adjustment exposed until other devices/routes are measured.
- **FAIL:** leaving the physical home LAN while audibly playing HOUSE still does not hand the same track to STANDALONE SMB/Tailscale. The app detects departure and remains in HOUSE reconnecting.
- **FAIL:** returning physically home while in STANDALONE does not reliably transition back into HOUSE. The phone can remain on its private SMB player even though the qualifying home LAN is present.
- **PASS only after restart/reopen:** closing/reopening the app after that failed return can detect HOUSE and show the shared Now Playing state.
- **FAIL:** when Bluetooth was already connected during that HOUSE reopen and the HOUSE/S3 session was already playing, the phone attached muted. Existing Bluetooth route state must count as current output intent; do not depend solely on a new connection callback.
- **Observed consequence of failed return transition:** while the phone remained STANDALONE, manually starting SMB playback and then powering an S3 produced simultaneous independent phone and HOUSE queues. This is the split-brain condition automatic mode selection is supposed to prevent.
- **FAIL:** Galaxy S8 launch: v0.4.2 crashes when opened. No diagnosis is recorded yet.
- **PENDING:** dedicated HOUSE Quit/reopen stale-state validation remains open.

### Additional STANDALONE Bluetooth requirement

STANDALONE should use Bluetooth output intent consistently with HOUSE while retaining standalone ownership:

- Bluetooth disconnect -> pause/silence local playback and retain the exact standalone queue/song/position.
- Bluetooth reconnect -> resume that retained standalone session.
- If Bluetooth is already connected when the app starts or transitions into STANDALONE, recognize the existing route state rather than requiring a fresh callback.
- Explicit Stop/Quit remains authoritative and must not be undone merely because Bluetooth is connected.
- If there is no retained standalone session, Bluetooth connection alone has nothing to start.

This behavior is **not** a substitute for fixing live STANDALONE -> HOUSE return-home transition.
