# Roadmap / ideas

Unless explicitly linked to approved requirements, these are ideas, not commitments. Proven playback behavior takes priority over feature count.

**Current source: v0.4.1.** The first HOUSE backend/receiver and approved Browser polish are implemented, including the three corrections from the first phone pass. The next checkpoint is the combined [phone/S3 acceptance session](HOUSE_VALIDATION.md), using server v0.8.2 for queue sorting. Live home/away handoff and final regression acceptance follow; their remaining product choices are still open. Historical dependency notes below are retained as dated progress records.

**Current handoff:** [v0.4.1 correction release](RELEASE_0.4.1.md). Pi v0.8.2 is confirmed installed and healthy. Resume phone acceptance with Tailscale on before launch and by toggling it during HOUSE; then test phone/S3 audio and controller lifecycle. Build checks do not imply a physical pass.

## v0.3.8 field validation

The main standalone behavior is now confirmed on the real phone:

- app runs normally;
- 40dp search field and clear X are correct;
- Browser/Now Playing sort state stays synchronized;
- current/selected track remains item zero after sorting;
- Repeat All wraps correctly;
- no regressions were observed in Android Auto, Garmin/Bluetooth controls, Play/Resume fade, Tailscale startup, metadata, Country Buffer, or the preserved SMB tuning.

The remaining targeted v0.3.8 validation is prolonged SMB outage/recovery:

- prolonged SMB loss with the screen both on and off;
- visible transition through `Waiting for SMB`, `Checking SMB`, and `Rebuilding buffer`;
- automatic recovery after a failed/timed-out individual probe;
- recovery after connectivity returns during a scheduled wait;
- another outage during the buffer-rebuild phase;
- a slow but progressing refill that must not be killed by the no-progress watchdog;
- explicit Pause, Stop, Quit, track change, and queue replacement during recovery.

See [TESTING.md](TESTING.md) and [OUTAGE_RECOVERY_PLAN.md](OUTAGE_RECOVERY_PLAN.md).

## Browser polish — implemented in v0.4.0, phone acceptance pending

Preserve all confirmed v0.3.8 playback behavior and make only these Browser layout/content changes:

- align the left/right outer edges of **both button rows** with the left/right outer edges of the search bar;
- change the path display to show **only the current folder name**, not the full SMB path;
- swap the **SMB** and **Parent Folder** buttons, including their functions;
- swap the **PLAY LIST** and **Sort** buttons, including their functions.

Do not reopen the confirmed search-X behavior, search height, shared sort, current-track-first queue behavior, Repeat All, portrait lock, fade, vehicle/Bluetooth behavior, Tailscale behavior, metadata handling, Country Buffer, or SMB tuning while doing this UI pass.

## Next coordinated Android iteration

The first startup/control/audio slice of this change set is implemented in v0.4.0 alongside the Browser polish. Live handoff remains later work. **Keep HOUSE work and the approved Browser polish together, and preserve the proven v0.3.8 standalone transport behavior.**

### Preserve the v0.3.8 standalone baseline

STANDALONE continues to use the existing SMB/Tailscale -> Media3/ExoPlayer path, including Country Buffer, SMB read-ahead, outage recovery, metadata fallback, Android Auto/Bluetooth/Garmin behavior, explicit Play/Resume fade, Repeat All, search, and shared sort behavior.

### Apply the already-approved Browser polish

- align the left/right outer edges of both Browser button rows with the search bar;
- show only the current folder name in the path display;
- swap the SMB and Parent Folder button positions/functions;
- swap the PLAY LIST and Sort button positions/functions.

### Automatic HOUSE / STANDALONE startup

On app launch, determine HOUSE before starting the ordinary standalone startup path:

1. enumerate Android networks;
2. choose a Wi-Fi or Ethernet `Network` that is **not VPN**;
3. require a directly connected physical route covering the configured Pi address;
4. through normal Android routing, require the MPD `OK MPD ` greeting and expected `house-audio-server` health identity;
5. qualifying physical route plus verified service identity => HOUSE;
6. otherwise use STANDALONE behavior and request/use Tailscale as the existing app does.

Tailscale may already be connected when SMB Player opens. The physical non-VPN network/route qualifies and monitors home presence; ordinary MPD/HTTP/Snapcast sockets use normal Android routing. VPN-only reachability never qualifies.

### HOUSE control/UI behavior

Keep the existing Browser and Now Playing UI model. In HOUSE, the playback authority changes from the phone's local Media3 player to the Pi's MPD session through `house-audio-server`.

- Browser/search/sort semantics remain the same.
- Tapping a song or PLAY LIST sends the corresponding ordered relative paths to the house queue instead of building a local SMB queue.
- Play/Pause, Previous/Next, Seek, Shuffle, Repeat, and deliberate queue sort operate on MPD.
- HOUSE Quit detaches the phone and must not Stop/Clear the house session.
- Merely opening the app, browsing, sorting, or attaching to an existing house session must not mutate playback.

### HOUSE phone-output rule

The phone's synchronized HOUSE output starts **muted** when the app enters HOUSE.

For a muted phone, song/PLAY LIST reads MPD and audible-output state before sending the command. Preserve mute if MPD was playing with another audible output. Auto-unmute if MPD was paused/stopped or playing with no other audible output. Present-but-inaudible nodes do not suppress auto-unmute. Explicit Play from pause/stop also auto-unmutes. An already-unmuted phone stays unmuted. Unknown audibility preserves mute; failed pre-command reads do not send the playback write.

Merely opening the app, browsing, sorting, or pressing Next/Previous while music is already playing leaves the phone's output mute state unchanged.

Provide an explicit HOUSE-only **Mute Output / Unmute Output** control in the lower Now Playing control strip. It controls only the phone renderer, never MPD/global audio.

### Passive-node default playlist selector

In HOUSE, reuse the Browser button position that is SMB in STANDALONE as the passive-node default selector.

- button displays `MP3s` or `Rap`;
- tapping toggles the Pi's persisted passive-node default between those two folders;
- the setting belongs to the Pi because it controls S3 behavior even when no phone is connected;
- changing it must **not** replace or restart the currently playing queue;
- it applies to the next genuinely fresh passive-S3 auto-start session.

STANDALONE keeps the normal SMB button and settings behavior.

## Near-term candidates

- `.m3u` / `.m3u8` playlist-file support.
- Optional library filename-cleanup script that reads metadata, performs a dry run, sanitizes filenames, prevents collisions, and logs reversible renames. This should remain separate from the player so the player itself stays read-only toward the music library.

## Later candidates

### House-audio-server integration — initial slice released; acceptance and live handoff pending

The Android Browser/search/sort and Now Playing UI must control the Pi's MPD session in automatic **HOUSE** mode while preserving the existing SMB/ExoPlayer path in **STANDALONE** mode. HOUSE presence requires a directly connected route on non-VPN Wi-Fi/Ethernet and normal-routed MPD/service identity checks at the locally configured Pi address. VPN-only reachability does not count. A temporary home outage is reconnection, not automatic independent playback. No mDNS/custom discovery handshake is planned unless testing shows it is actually needed.

v0.4.0 implements live shared-queue/state display and control, a separate synchronized phone receiver, HOUSE-only **Mute output / Unmute output**, and independent controller/renderer presence. Same-song SMB/Tailscale continuation when an unmuted playing phone leaves home remains the following slice; muted/paused/stopped departure must stay silent. A phone attaching to fresh idle does not auto-start a track; only passive nodes do that. HOUSE Quit detaches the phone without sending global Stop/Clear. The Pi owns final-node finish/stop, muted-controller pause/retention, and a new random shuffle of the configured passive default for each genuinely fresh session. Completed drains end the old session; returns before track end preserve it. Chance repeats of the first song are allowed. The default folder setting persists, not the completed session's shuffled order/progress.

The browser controller and Windows player share the same Pi control contract. The basic `house-audio-server` HTTP API is runtime-proven on the permanent Pi for browse, queue/state, queue replacement/start index, Play/Pause/Stop, Seek, Previous/Next, Shuffle/Random, and Repeat. Renderer presence is runtime-proven through abrupt ESP32 hard power-off, and the passive-radio policy now works end to end: fresh-idle power-on starts default music, active-session join/rejoin preserves the queue, and v0.5.1 resumes an existing paused session when a passive radio appears.

Two independent ESP32-S3 + PCM5102A nodes have also passed the real audible synchronization test through different analog systems. Android should target the bridge rather than use MPD's native port as its normal control API. Server v0.7.0 provides the field-proven persisted passive-default selection through GET/POST `/settings`. Controller/output presence policy is implemented/tested in v0.8.0 (73 local tests and CI pass) and is installed on the permanent Pi. Initial controller-baseline validation passes; physical muted-controller transition testing remains pending. The v0.6.2 completed-drain/fresh-shuffle correction is included in the deployed v0.7.0. Initial radio tests recorded same-song return after about 10 seconds unplugged and a different new song after about five minutes. The supplied `/session` action confirms early-return cancellation; a specific manually selected CD/Rap queue-to-default drain check remains open. Android v0.4.0 supplies Snapcast receiver integration; phone acceptance and the remaining handoff decisions/implementation are still pending. These capabilities were not implemented by v0.3.8.

A small reliability issue is being tracked separately: occasional few-second silence on one ESP32 node or the other during otherwise synchronized playback. v0.6.0 adds unattended server-side diagnostics so a future occurrence can be correlated without babysitting ping windows.

Android requirements, current code hooks, and acceptance checklist: [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md). Cross-project authority: [server session behavior](https://github.com/oolah10293/house-audio-server/blob/main/docs/SESSION_BEHAVIOR.md). Tracking: [Issue #1](https://github.com/oolah10293/smb-music-player/issues/1).

### Smart Shuffle

Potential design:

- strong positive weight for time since last play
- weak play-count influence
- temporary discovery bonus for new tracks
- recent-artist penalty
- weaker recent-album penalty
- soft, decaying penalty for repeated early skips
- randomness retained so the result is not deterministic

Regular Shuffle must remain available as a separate mode.

### Search

Possible future additions only if needed:

- metadata search (title / artist / album)
- recursive search
- persistent search index/database

The current current-folder filename/folder-name filter is intentionally simple and fast.

### Artwork

Possible fallback/cache improvements without changing audio files:

- embedded artwork first
- folder artwork (`cover.jpg`, `folder.jpg`) as fallback
- local cache for remote artwork

### Android Auto

A richer MediaLibrary browse tree could be added later. Current playback already uses a `MediaLibraryService`; v0.3.6 enabled explicit media audio focus, and vehicle routing plus steering-wheel track skip were confirmed in real use. v0.3.8 must preserve that behavior.



## v0.7.0 passive-default backend validation

The server dependency for the planned HOUSE `MP3s` / `Rap` Browser button is now field-proven on the permanent Pi. Changing the default did not interrupt the active song, and after a completed drain the next S3 startup used the saved `Rap` choice with a fresh session. The first observed track was Ludacris — *Southern Hospitality*.

The next server prerequisite for Android HOUSE work is controller presence/output-state handling.

### Controller lifecycle decisions and server v0.8.0

Confirmed 2026-09-29: background/screen-off controllers stay present through five-second heartbeats with fifteen-second expiry; explicit Quit detaches immediately. When the last controller leaves an automatically paused session, end it without advancing the song.

Server v0.8.0 implements this lifecycle, durable phone-renderer association, muted/output-unavailable pause retention, audible-return resume, and stale lease/sequence rejection. Physical validation is pending. Android should use this contract for the existing Browser/Now Playing HOUSE backend, preserve its standalone engine, and include the already-approved Browser polish in that same iteration.


### v0.8.0 server deployment / restart rule

The controller/output backend is now deployed on the permanent Pi. With no controller attached, `GET /controllers` correctly reports the active S3 as one passive audible renderer and no controllers.

A server restart is now intentionally a fresh-session boundary. The Android client should reattach with a new lease after restart and must not expect live controller/output/session state to survive. The Pi still persists controller↔renderer ownership and the selected passive default. v0.8.1 implements the startup fresh-idle boundary and is deployed. The already-present passive-S3 restart path is field-proven.

### Next implementation sequence after v0.8.0 baseline

1. **DONE for source/tests and one real restart path:** server v0.8.1 normalizes MPD to fresh idle at process startup, preserves saved configuration/device ownership, gates playback writes until verified ready, and retries MPD startup without resetting later sessions. 85 local tests and GitHub CI pass. On the permanent Pi, restarting with one passive S3 already powered reached `startup.ready: true` and started a fresh randomized Rap session. Physical controller pause/resume/expiry checks remain pending.
2. **IMPLEMENTED in v0.4.0; next checkpoint is phone/S3 acceptance:** HOUSE backend and bundled synchronized phone receiver through the existing Browser/Now Playing UI, including Browser alignment/folder-label/button swaps and server-owned MP3s/Rap selector. STANDALONE retains SMB/Media3. HOUSE respects startup readiness and reattaches after restart. Server v0.8.2 adds guarded in-place queue sorting. See [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md).
3. **Next acceptance checkpoint:** silent HOUSE opening, deliberate phone unmute, phone/S3 synchronization, and Quit leaving the radio playing. Then complete home/away recovery and final device acceptance under the documented remaining decisions.

The confirmed installed server is now v0.8.2. Its health/startup baseline is good and Android v0.4.0 successfully entered HOUSE once MPD was exposed on the LAN, with Now Playing adopting the current MPD track. Physical controller pause/resume/expiry and phone/S3 synchronization checks remain pending.


### First v0.4.0 phone findings

The initial phone checkpoint produced useful corrections before the synchronization test:

- MPD had been listening on localhost only. The Pi configuration now requires both localhost and the configured LAN listener so HOUSE identity checks are possible.
- With that fixed and Tailscale off, v0.4.0 entered HOUSE and showed the current MPD track.
- Turning Tailscale on stopped HOUSE updates even though the same Pi HTTP JSON remained reachable in the phone browser. The v0.4.0 explicit Android-`Network` transport binding is therefore a known field defect.
- Revised design: qualify HOUSE using the presence/routes of a real non-VPN Wi-Fi/Ethernet network, watch that physical network for departure, but carry normal HOUSE MPD/HTTP/Snapcast traffic through normal Android routing so Tailscale may remain connected.
- Starting/replacing a playlist uses the pre-command audible-house state. If MPD was already playing with another audible output, preserve the muted phone. If nothing was audibly playing—including `audibleCount == 0` while MPD is technically playing—auto-unmute the initiating phone.
- The HOUSE Mute/Unmute control must be moved into the lower Media3 control strip alongside transport, Shuffle/Repeat, and track time.

### Android correction slice — implemented in v0.4.1

The physical-route/normal-routing separation, pre-command conditional auto-unmute, and lower-strip output icon are implemented. Loss of the physical route closes local audio and suspends HOUSE requests/heartbeats; temporary server failure remains HOUSE recovery. Stale queued commands and polls are rejected across detected network changes. Live backend handoff is not implemented in this slice.

Next: install v0.4.1, rerun silent opening with Tailscale already on and toggled during HOUSE, then complete the phone/S3 synchronization/controller-lifecycle checkpoint.

Automatic home/away same-song handoff remains the following slice. The physical network object/route, not mere Pi reachability through Tailscale, will be the departure authority.


### Bluetooth-aware phone output

Approved HOUSE behavior for Android:

- Bluetooth audio **disconnect** mutes the phone output but never directly sends MPD Pause/Stop.
- If another output remains audible, shared playback continues.
- If the phone was the only audible output, server policy auto-pauses and retains the exact session while that phone remains connected as a muted controller.
- If that last muted controller later disconnects/expires, the existing session-end rule applies.
- Bluetooth audio **connect** while house music is already playing automatically unmutes the phone and joins the current stream, even if local output had previously been manually muted.
- Bluetooth connect alone does not start idle/paused HOUSE playback. If the user subsequently starts music from the phone, Bluetooth presence means the phone should be unmuted for that start.

This is a future Android correction after the current v0.4.1 checkpoint; do not claim it is implemented yet.

### v0.4.1 physical checkpoint status

Current field results:

- PASS: Tailscale-connected HOUSE operation.
- PASS: lower-strip output icon appearance/location.
- PASS: muted phone changes song/PLAY LIST without unmuting while an S3 is already audible.
- FAIL/open: phone/S3 sync, with the phone observed about one second behind the S3.


### HOUSE Country Buffer

Approved direction: give the synchronized HOUSE stream a deliberately large **multi-second** playout buffer, while keeping small Snapcast chunks (currently ~20 ms).

Goals:

- ride through short LAN/Wi-Fi contention without audible dropouts;
- create enough timing headroom for per-client offset correction, especially the Android phone;
- keep all renderers on the same scheduled house timeline.

Do **not** equate buffer depth with packet/chunk size. Also do not accept "wait for the whole old buffer to drain" as the desired control behavior: deliberate Play/Pause/Next/Previous/Seek/playlist changes should invalidate or rebase stale buffered audio as quickly as the underlying Snapcast path supports.

Exact production buffer depth is intentionally **not locked yet**. Start with several seconds in field testing and balance continuity against radio power-on/rejoin and audible command latency. Verify Snapcast's real discontinuity/flush behavior before claiming aggressive stale-buffer invalidation is solved.

The current S3 dropout correlation with heavier LAN/Internet traffic is motivation for this experiment, not proof of cause.
