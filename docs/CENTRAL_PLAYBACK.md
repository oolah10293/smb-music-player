# Android integration with house-audio-server

**Status: required HOUSE functionality to add; not implemented or runtime-tested in the Android app.** The standalone v0.3.8 player is now the confirmed baseline for normal phone use: search X/height, shared sort, current-track-first sorting, Repeat All, vehicle/Bluetooth behavior, fade, Tailscale startup, metadata, Country Buffer, and SMB tuning are working. Prolonged-outage recovery hardening still needs its dedicated field test.

The authoritative product rules are in [house-audio-server/docs/SESSION_BEHAVIOR.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/SESSION_BEHAVIOR.md). This document translates those decisions into Android requirements and identifies the corresponding implementation work. Engineering proposals and unresolved details below are not additional user-approved behavior.

Tracking: [Android Issue #1](https://github.com/oolah10293/smb-music-player/issues/1).

## 1. Separate playback authority from phone sound

There are two independent decisions:

| State | Playback controls and Now Playing | Phone sound |
| --- | --- | --- |
| HOUSE, output unmuted | Control/display the Pi's one MPD session | Receive the synchronized Snapcast stream |
| HOUSE, output muted | Control/display that same MPD session | Silent controller; no independent song |
| STANDALONE | Control/display the existing local Media3/ExoPlayer session | Existing SMB/Tailscale playback when playing |

The phone is a **controlling node**, even when it also renders audio. It must not register as a passive radio just because its output is unmuted.

```text
Browser / Search / Sort / Now Playing / media controls
                         |
                 playback backend boundary
                    /               \
       STANDALONE backend          HOUSE control client
       existing Media3 path        house-audio-server -> MPD
       SMB -> ExoPlayer                    |
              |                      Snapserver
              |                           |
              |                 separate phone Snapcast receiver
              |                    + local output mute
              +------------- phone audio output --------+
```

The proposed backend boundary lets the same UI target either session. HOUSE control and HOUSE audio are separate connections and responsibilities. MPD remains the queue/transport authority; the phone is not a music relay. A remote-control API alone does not implement synchronized phone playback, and playing an independent SMB copy at approximately the same seek position is not a substitute for a Snapcast receiver.

## 2. Automatic home-LAN detection and reconnection

Use the simplest direct test of the actual condition we care about: **can this phone reach the house MPD instance through a real non-VPN Wi-Fi/Ethernet network?**

HOUSE detection is:

1. Find an available Wi-Fi or Ethernet Android `Network` that is not a VPN transport.
2. Through that specific network, open a short TCP connection to the app's locally configured/reserved house LAN address on MPD port `6600`.
3. Require MPD's normal greeting, which begins `OK MPD `. A subsequent `ping` / `OK` may be used as an additional trivial liveness check.
4. A valid MPD response over that bound non-VPN LAN path selects HOUSE.

This is an **identity/reachability probe only**. It is not the HOUSE control path. Normal HOUSE browsing, queue changes, transport commands, presence, and session policy still go through the shared `house-audio-server` control layer.

Do not add mDNS/DNS-SD, SSID matching, GPS, a separate discovery service, or a custom handshake unless a later real-world problem proves they are needed. Tailscale/VPN-only reachability must never select HOUSE because the probe is deliberately bound to a non-VPN LAN network rather than the system's arbitrary route to the Pi.

The house LAN address is deployment configuration, not a source-code constant. Keep the current address in local app configuration rather than committing it into Android source or public examples.

Tailscale may already be connected when SMB Player opens. HOUSE detection must therefore run against an explicitly selected non-VPN Wi-Fi/Ethernet Android `Network`, not the default route. Existing VPN state is ignored for the HOUSE decision. If HOUSE is not established, the app then follows the existing STANDALONE/Tailscale startup behavior.

A temporary home-server, Wi-Fi, MPD-probe, or audio failure must not silently start an independent local playlist. Remain in HOUSE recovery while the phone is still plausibly on the home LAN. On confirmed departure, transition to STANDALONE under the handoff rules below. The departure grace period and ambiguous-network policy are still open, not a hard-coded timeout in this requirements record.

Show the active control target and distinguish control-server failure from audio-receiver failure where possible. Suggested status wording includes `HOUSE - reconnecting`, `House server unavailable`, and `Audio reconnecting`. Start retrying when failure is detected, not only after an audio buffer empties. Recovery joins the current house position, not an old backlog.

## 3. Folder browsing, queue control, and shared state

Preserve **folders are playlists**, Browser/Now Playing separation, browser scroll position, current-folder search, explicit sort modes, and filename fallback. Do not replace them with a metadata-first library UI.

Required HOUSE behavior:

- Fetch the current house track, queue, position/duration, transport, shuffle, and repeat state on attachment. Show the existing playlist even when another controller selected it.
- Opening the app or browsing does not issue Play, replace the house queue, or upload the phone's former standalone queue.
- Preserve `PLAY LIST`: with blank search, submit the current folder's audio tracks in the selected order; with active search, submit only matching tracks. Preserve the current multi-term AND filename/folder search semantics.
- Selecting a track retains the normal current-list behavior and starts at that selected item. Send library-relative track identities and the selected start item/index, not phone-specific SMB URLs or Linux absolute paths as the public control contract.
- Play/Pause, Previous/Next, Seek, Shuffle, and Repeat operate on MPD through the house service. Queue-sort actions are deliberate server queue changes; merely sorting a browser view is not.
- Preserve the current song, position, and transport state when explicitly reordering the active queue. Do not restart a song or send hundreds of separate reorder commands just to reproduce a sort.
- Receive updates made by the Windows player, browser, or another controller. Server state wins over stale phone UI state. Do not replay an old queue replacement after reconnecting.
- Obtain display metadata/artwork where available, retaining filename fallback without modifying the music files. Display the server queue separately from whatever folder the user is browsing; exact queue-screen placement is not yet chosen.

The browser controller is also part of the agreed system. It and the Android/Windows clients should use one Pi-side control contract, not unrelated playback backends on the server.

## 4. HOUSE-only Mute output button

Show **Mute output** in HOUSE mode; change it to **Unmute output** when muted. This controls this phone's renderer only, not MPD volume, global mute, or an unconditional MPD Pause.

Keep the controller connected while muted and report its output state separately. Preserve the mute choice across reconnections. While other rooms are playing, unmuting joins the current house position. When the server has automatically paused a retained session because only a muted controller remains, unmuting can resume that retained session under the server rule.

Keep Android media-output routing, local volume, and interruptions separate from intentional house transport commands. A call, headphone disconnection, audio-focus change, or local renderer failure must not masquerade as the user pressing house Pause. The server still applies its presence/output policy to the actual remaining nodes.

The initial HOUSE output rule is now settled: **entering HOUSE starts the phone renderer muted.** The phone automatically unmutes only when that phone initiates playback by tapping a song, tapping PLAY LIST, or pressing Play while MPD is paused/stopped. Merely opening the app, browsing, sorting, or using Next/Previous while music is already playing does not auto-unmute.

Place the HOUSE-only **Mute Output / Unmute Output** control in the lower Now Playing transport/control strip so the main Now Playing layout remains otherwise unchanged.

### Passive-node default playlist selector

HOUSE Browser needs a compact selector for the Pi's passive-radio default folder.

Reuse the Browser button position that is **SMB** in STANDALONE:

- STANDALONE: button remains SMB/settings;
- HOUSE: button displays `MP3s` or `Rap` and toggles the Pi's persisted passive-node default.

The setting is server-owned because it affects S3 startup even when the phone is absent. Changing it must not replace, restart, or otherwise disturb the current house queue. It applies only when the house later enters a genuinely fresh passive-renderer auto-start session.

Server v0.7.0 implements the required persisted setting: `GET /settings` returns `settings.passiveDefaultFolder` and `settings.allowedPassiveDefaultFolders`; `POST /settings` accepts exactly `{"passiveDefaultFolder":"MP3s"}` or `{"passiveDefaultFolder":"Rap"}` and returns the saved settings. The phone should read the server value and send an explicit choice, then use the acknowledged value. Refresh after reconnect instead of replaying stale edits. Neither endpoint touches playback or registers controller presence. `PASSIVE_DEFAULT_FOLDER` remains the fallback when no saved value exists. The server setting is field-proven in v0.7.0; Android button integration remains pending.

## 5. Controller presence and the Pi's session rules

The Android client must report **controller presence** and **renderer/output state** independently so a muted phone is not treated as either an absent device or an audible speaker. Correlate the two roles to one device; do not count its control and audio sockets as unrelated people/nodes.

The Pi, not every app independently, applies these agreed rules:

| Situation | Required result |
| --- | --- |
| Fresh idle; a phone/PC/browser connects first | Wait for explicit Play/selection, even with output unmuted. Only a passive node auto-starts a newly shuffled queue of the configured default folder. |
| Existing music; a phone joins | Adopt/display the existing queue and track; join sound only if unmuted. Do not restart or replace the queue. |
| Controller changes the queue, then leaves while other nodes remain | The new house queue remains in effect. |
| All nodes disconnect during playback | Finish the current track, then stop despite Repeat All. |
| Any node reconnects before that final track ends | Cancel the pending stop and retain the current session. Apply the muted-only pause rule if applicable. |
| Only a muted phone remains after the last audible node leaves | Pause MPD and retain the queue, track, and exact position. |
| An audible node returns or the muted phone unmutes after that automatic pause | Resume the retained session, not a new default queue. A passive radio powering on is explicitly allowed to resume an existing paused session; controller attachment alone is not. |

Every genuinely fresh passive-node session starts the configured default folder (`MP3s` or `Rap`) with Shuffle and Repeat All and a newly randomized order. The selected folder setting persists; shuffled order/progress does not survive a completed session. Chance repeats of the first song are allowed, with no forced-difference rule. Returning before the final track ends preserves the existing session unchanged. After a completed drain, MPD's possible `pause @ 0.0` on the next old-queue track is only an artifact: the next passive start must load a fresh default queue, not resume the old controller selection. Ordinary paused sessions remain resumable. Android attachment must not reshape the queue or impose passive defaults on manually selected queues.

**HOUSE Quit must detach this phone, not send global Stop/Clear.** The current standalone Quit implementation stops and clears its local player; that behavior must remain standalone-only. Other rooms continue under the server's rules. If this phone was the final node, the server handles the final-track stop; the app does not implement its own competing shutdown logic.

The 2026-09-29 choices settle these edges: background/screen-off apps remain controllers while sending five-second heartbeats, with fifteen-second expiry; Quit detaches immediately. The last controller leaving an automatically paused session ends it without advancing the song. A still-audible renderer can survive control-lease expiry as an output, counted once for that device. A stale established socket alone is not presence.

## 6. Automatic continuation when leaving home

Confirmed behavior: an unmuted phone that was hearing playing house music automatically continues the **same song** through its existing SMB/Tailscale player after leaving the home LAN.

Cache the library-relative track identity and last heard position while still connected. Do not wait until the Pi is unreachable to ask which file to open. Map that identity to the user's locally stored SMB library-root configuration; title/artist matching is not reliable identity.

The handoff must preserve what the phone was actually hearing. The raw MPD position may be ahead of a buffered renderer, so a server-state snapshot alone needs an agreed timing relationship to the heard track/position. Keep enough recent identity/timing context for track boundaries. Exact timestamp fields and clock mapping are part of the API/renderer work, not already implemented functionality.

| Before departure | Standalone result |
| --- | --- |
| Output unmuted and actually playing house music | Open the same file, seek to the retained heard position, buffer, and resume automatically. |
| Output muted | Remain silent; a network change must not start sound. |
| Paused or stopped | Remain paused/stopped; being unmuted is not permission to start. |

Do not send Stop, Seek, or a queue replacement to MPD as part of leaving. Remaining home listeners continue; the Pi applies its ordinary node-disconnection policy independently.

The handoff must use the existing SMB buffering/retry path and suppress stale asynchronous callbacks from a superseded mode. Avoid simultaneous standalone and house audio. Automatic continuation is required; gapless switching is **not** proven or promised. Copying the entire house queue into the away player, rather than just the current track, remains undecided.

On returning home, adopt the existing house session without overwriting it with the away queue. If the house is playing, an unmuted phone joins it; a muted phone remains silent. A controller returning to a freshly idle house still must not auto-start MPD. What to do with still-playing private phone audio at that idle-house boundary remains an explicit open question.

## 7. Required house-service contract

The basic `house-audio-server` HTTP/MPD bridge now exists and has been runtime-validated on the permanent Pi. Proven server-side operations include health/state, full queue inspection, folder-first library browsing, ordered queue replacement with a requested start index, Play/Pause/Stop, Seek, Previous/Next, Random/Shuffle, and Repeat. Transport-only commands were also confirmed not to rebuild the MPD queue unnecessarily.

The server has now advanced beyond the basic control core: Snapserver renderer presence is also runtime-proven on the permanent Pi, including abrupt hard-power-off. The service distinguishes Snapserver's stale raw `connected` state from effective renderer `present` state using `lastSeen` freshness, and power-on/off transitions are reliable enough to drive policy.

The passive-renderer side has now advanced beyond the basic primitives: fresh-idle passive-radio auto-start, active-session join/rejoin, effective hard-power presence, and passive-radio resume-through-Pause are runtime-proven on the permanent Pi. Two independent ESP32/PCM5102A renderers have also passed the real audible synchronization test.

Server v0.8.0 implements controller leases, output reporting, device/renderer association, muted-controller pause/resume, and stale lifecycle-report rejection in source/tests (Pi validation pending). Persisted passive-default selection is field-proven in v0.7.0. Transport-command deduplication/revision checks remain separate work; clients must not replay uncertain Next/queue writes.

The Android app should **not use MPD's native control port as its HOUSE control API**. The one deliberate exception is the short LAN-presence probe described above, which reads MPD's `OK MPD ...` greeting to prove that the expected service is reachable over a bound non-VPN LAN path. Android, Windows, and the browser controller should otherwise target the same house-audio-server contract so the Pi can enforce one-session lifecycle, presence/output rules, fresh-session default shuffle, and HOUSE/STANDALONE behavior consistently.

The Android client should now target the real v0.2.0 HTTP API documented in [house-audio-server/docs/API.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/API.md) for the basic browse/queue/transport operations rather than invent parallel endpoints. A push-state feed is still optional; initial integration can poll authoritative state. The Snapcast audio connection remains distinct from the custom control service.

The Android client needs:

- **Identity/capabilities:** control-service identity, protocol version, supported commands, and stream connection information. HOUSE presence itself is established separately by the direct MPD LAN probe.
- **Library browsing:** relative folder/track identities, names, type, modified time for sort, and available metadata/artwork. A small browse-source abstraction can keep the existing UI while using the house service at home and SMB away.
- **Session snapshot and updates:** session/queue identity, queue entries and current entry, transport, shuffle/repeat, position/duration and timing context, pending-stop/automatic-pause reason, plus a revision or equivalent stale-state check.
- **Commands:** replace/play the selected ordered list with a start item, deliberate transport commands, queue reordering, shuffle/repeat, and per-device output-state changes. Read/attach operations must not mutate playback.
- **Presence:** stable app-device identity, controller role, associated renderer identity, mute/output state, connection lifecycle, and a defined heartbeat/expiry policy.
- **Errors and concurrency:** unsupported capability and unavailable-server responses; command acknowledgement and request identity or equivalent protection against duplicate Next/queue commands after retries. Reconcile against current server state after reconnect.

Credentials stay local and out of Git/logs. Keep existing encrypted SMB storage separate from any house-service trust/token configuration. Do not embed private network addresses or user-specific filesystem roots in Android source or public examples.

### Proven house-side milestones relevant to Android

Current permanent-Pi/hardware facts that Android integration may rely on:

- a passive radio can power on with no controller present and automatically start house music;
- an arriving passive renderer joins the current song/queue instead of restarting it;
- a hard-powered node can return after more than ten seconds and rejoin the still-active song; about six seconds from plug-in to audible output was observed once;
- passive-radio arrival resumes an existing paused MPD session rather than replacing the queue;
- v0.6.1 established that MPD `single oneshot` can land paused at 0.0 on the next old-queue track. Its old-queue resume behavior is superseded: v0.6.2 treats a completed drain as fresh idle and starts a new default shuffle on the next passive arrival. v0.6.2 is installed on the Pi with initial radio results: same song after about 10 seconds unplugged, different new song after about five minutes. The supplied `/session` snapshot confirms the early-return path; exact CD/Rap-to-default replacement after drain remains a separate field check;
- two independent ESP32-S3 + PCM5102A outputs have been heard playing in sync through different analog systems;
- effective renderer presence is based on fresh Snapcast activity, not raw stale TCP connection state;
- occasional few-second single-node dropouts are still being diagnosed; v0.6.0 records renderer timing/presence and global stream events for later inspection.

These facts reduce risk in the Android project: the remaining phone problem is principally controller integration plus packaging a synchronized phone renderer, not proving that the central synchronized-audio architecture can work.

## 8. Integration points in the existing app

These are code-level implementation notes based on the current source, not completed changes:

| Existing code | Required adaptation |
| --- | --- |
| [MainActivity.kt](../app/src/main/java/com/smbmusic/player/MainActivity.kt): `browse`, `playCurrentFolder`, `sortedTracks` | Preserve browsing/search/sort semantics; route ordered queue selection through the active backend rather than always preparing/playing the local MediaController. |
| [NowPlayingActivity.kt](../app/src/main/java/com/smbmusic/player/NowPlayingActivity.kt): controller binding, `sortCurrentQueue`, metadata/status | Display authoritative house state and queue, add output mute/target status, and route explicit sort/transport operations correctly. |
| `NowPlayingActivity.kt`: `quitCleanly` | Currently calls Stop and Clear on its controller. In HOUSE, detach/stop only the phone renderer and presence; never clear or stop MPD as a side effect. Preserve standalone Quit. |
| [PlaybackService.kt](../app/src/main/java/com/smbmusic/player/PlaybackService.kt) | Preserve the existing ExoPlayer/SMB path for standalone. Add the mode/backend boundary, handoff coordination, and separate house receiver lifecycle without allowing local recovery callbacks to start a competing song in HOUSE. |
| Media3 session / notification / Bluetooth control integration | Use the selected authority consistently, not just the on-screen buttons. Keep deliberate house commands separate from local audio interruptions; preserve existing standalone and vehicle behavior. |
| Existing SMB, credential, and UI components | Reuse their standalone behavior; do not rewrite transport or rename/reorganize the user's music to support HOUSE. |

A `PlaybackBackend` abstraction remains the proposed integration boundary. Keep the house control client and synchronized receiver independently testable. Do not assume ExoPlayer or the current MediaLibrarySession already supplies a Snapcast receiver.

## 9. Acceptance checklist

All items below are **unimplemented/unverified Android integration work**:

- [ ] A TCP probe bound to a non-VPN Wi-Fi/Ethernet Android `Network` reaches the configured house LAN address on MPD port 6600 and receives `OK MPD ...`; that selects HOUSE. Tailscale/VPN-only reachability does not. Brief outages do not silently start standalone music.
- [ ] Fresh-idle controller attachment makes no playback/queue mutation. Passive-node default start remains server-owned.
- [ ] Joining active playback displays the real house playlist, track, position, shuffle/repeat, and changes from other controllers.
- [ ] Folder/filtered `PLAY LIST`, selected-track start, transport, and explicit queue sort match existing semantics through the server API.
- [ ] Phone HOUSE audio uses a real synchronized receiver; no independent ExoPlayer copy plays alongside it.
- [ ] HOUSE-only Mute/Unmute affects this phone, survives reconnect, and preserves the server's muted-controller pause/resume behavior.
- [ ] HOUSE Quit/controller departure preserves other listeners and their queue; final-node and reconnect-before-track-end behavior follow the Pi rules.
- [ ] Return before drain completion preserves the active queue. After completed drain, the next passive arrival uses the configured default with fresh randomness and permits chance repeats. No saved rotation/bookmark from the completed session is required.
- [ ] Home audio/control outages recover independently as appropriate and rejoin the current stream without resetting MPD.
- [ ] Unmuted playing departure continues the same track at the last heard position over SMB/Tailscale; muted/paused/stopped departure stays silent.
- [ ] Returning to active HOUSE playback adopts it without replacing the queue; returning to fresh idle does not auto-start MPD.
- [ ] Standalone regression tests pass: Country Buffer, read-ahead, retry/resume, filename fallback, search/sort, browser scroll, lock-screen/Bluetooth/Android Auto behavior, and Quit.

## 10. Open details and scope

Retain the unresolved choices in the canonical server document: home/away network grace periods, whole-queue away continuation, return-to-idle-house handling, and service-restart recovery. Controller background presence, five-second heartbeats/fifteen-second expiry, and ending the session when the last controller leaves an automatic pause were confirmed on 2026-09-29. Control-service protocol schema, library-path mapping setup, exact heard-position timing, and Android receiver packaging also need engineering decisions and validation. The home/away probe itself no longer needs a custom discovery or handshake protocol.

These gaps do not undo the approved behavior. They must not be filled with silent assumptions. **Two physical ESP32/PCM5102A renderers are now audibly synchronized**, so the Snapcast multi-renderer architecture itself is proven. That does **not** prove Android rendering, Android timing, or seamless phone handoff; those still require separate implementation and tests. AI DJ, Philco display, and room-management expansion are separate work, not prerequisites for this client integration.

**Current Android status:** the server-side basic MPD API, renderer presence, passive-radio appliance behavior, ordinary paused-session resume, the MPD boundary-pause discovery, and two-node audible synchronization are runtime-proven. The v0.6.2 correction is unit-tested and running on the Pi, with initial short/long radio power-cycle results recorded above. No Android HOUSE runtime code has been added yet. Persisted passive-default selection is field-proven in v0.7.0. Controller-presence/output-state handling is implemented in v0.8.0 source/tests, awaiting Pi validation and Android integration. The server also has unattended renderer diagnostics for the occasional few-second single-node dropout investigation.


### Proven passive-default selector dependency

The Pi-side runtime selector required by the HOUSE Browser is now field-proven in `house-audio-server` v0.7.0.

- reading the current default works;
- changing `MP3s` -> `Rap` leaves active playback untouched;
- after a completed no-renderer drain, the next S3 power-on uses the saved choice;
- the observed fresh Rap session began with Ludacris — *Southern Hospitality*.

The Android button can therefore be implemented against the existing `GET /settings` / `POST /settings` contract rather than waiting on further server design.

### v0.8.0 controller contract for the Android implementation

The server supplies `GET /controllers` and `POST /controllers/attach`, `/controllers/heartbeat`, `/controllers/detach`; see the server API for complete schemas.

Register the stable app-device id and its own stable Snapcast renderer id **before connecting that receiver**. Ownership is saved on the Pi so a known phone cannot become a passive auto-starter after a control disconnect or service restart. Use the returned lease id and increasing sequence numbers; reject stale callbacks from an older attachment. Expired leases require a new attachment, with the current local mute choice reported explicitly.

The five-second heartbeat reports `outputMuted` separately from `outputReady`. Ready means the receiver/output path can render, even if MPD is paused. Report calls, route failures, or renderer failures as not-ready without discarding the mute choice. The Pi requires a live audible associated renderer as well as unmuted/ready reports before treating the phone as audible. This API reports state; Android must implement the actual local mute and synchronized receiver.

New HOUSE attachment still starts muted. Auto-unmute remains limited to phone-initiated song/PLAY LIST/Play actions. Keep the heartbeat alive in the background/screen-off service. On HOUSE Quit, stop the phone's receiver and heartbeat, then detach; do not send MPD Stop/Clear. Attachment and output reports never auto-start a fresh session.

The Pi auto-pauses when controllers remain without audible output, resumes that automatic pause when an audible output returns, and ends it without advancing when the last controller leaves. Explicit Pause/Stop are respected. Combined presence counts deduplicate phone control/audio roles. Snapserver outage does not imply that listeners departed.

Server v0.8.0 has 73 passing local tests; installation/physical testing is pending. Android code and Browser polish remain the next integration work, with the existing SMB Player UI preserved. Automatic-pause reason recovery across a server restart remains open; the phone must not guess whether a retained Pause is automatic.


### Restart contract for Android HOUSE

A `house-audio-server` restart is a hard session boundary.

Android should:

- treat its old controller lease as dead and attach again;
- preserve its own local preference/UI state only where appropriate, but not replay stale house transport/output reports;
- not expect the Pi to restore the old live controller lease, output-ready report, automatic-pause reason, pending drain, or old house queue/session;
- accept fresh idle after server restart;
- remain silent/idle when it is the first controller to reconnect;
- allow a passive S3 present/arriving after restart to start the configured default with a fresh shuffle.

The Pi continues to persist the passive default and controller↔renderer ownership so the phone receiver cannot be misclassified as a passive radio after restart.

This replaces the former idea of recovering automatic-pause ownership across service restart. The remaining server implementation gap is explicit startup normalization of MPD to fresh idle.
