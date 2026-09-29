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

The initial/default mute choice has not been settled. Do not turn an earlier suggestion of silent-by-default into an implemented decision.

## 5. Controller presence and the Pi's session rules

The Android client must report **controller presence** and **renderer/output state** independently so a muted phone is not treated as either an absent device or an audible speaker. Correlate the two roles to one device; do not count its control and audio sockets as unrelated people/nodes.

The Pi, not every app independently, applies these agreed rules:

| Situation | Required result |
| --- | --- |
| Fresh idle; a phone/PC/browser connects first | Wait for explicit Play/selection, even with output unmuted. Only a passive node auto-starts the default rotation. |
| Existing music; a phone joins | Adopt/display the existing queue and track; join sound only if unmuted. Do not restart or replace the queue. |
| Controller changes the queue, then leaves while other nodes remain | The new house queue remains in effect. |
| All nodes disconnect during playback | Finish the current track, then stop despite Repeat All. |
| Any node reconnects before that final track ends | Cancel the pending stop and retain the current session. Apply the muted-only pause rule if applicable. |
| Only a muted phone remains after the last audible node leaves | Pause MPD and retain the queue, track, and exact position. |
| An audible node returns or the muted phone unmutes after that automatic pause | Resume the retained session, not a new default queue. A passive radio powering on is explicitly allowed to resume an existing paused session; controller attachment alone is not. |

A future fresh passive-node session starts `MP3s` with Shuffle and Repeat All, continuing the saved default rotation. The Pi preserves that shuffled order/progress separately from controller-selected Rap/CD queues. Complete the remaining order, then generate a fresh shuffle without immediately repeating the last track. A completed track advances to the next; a genuinely unfinished track can resume at its bookmark. The Android app must neither reset this record on attachment nor force these default settings onto every manually selected queue.

**HOUSE Quit must detach this phone, not send global Stop/Clear.** The current standalone Quit implementation stops and clears its local player; that behavior must remain standalone-only. Other rooms continue under the server's rules. If this phone was the final node, the server handles the final-track stop; the app does not implement its own competing shutdown logic.

Precise background-app presence, heartbeat/lease expiry, and the last-muted-controller leaving an already-paused session remain open in the canonical behavior document. A stale established TCP socket is not sufficient proof of live presence.

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

The remaining server work relevant to Android is now concentrated on **controller-aware session policy**: controller presence/output state, durable default-`MP3s` shuffle progress, muted-controller pause/retention edges, and concurrency/stale-command protection appropriate for multiple controllers.

The Android app should **not use MPD's native control port as its HOUSE control API**. The one deliberate exception is the short LAN-presence probe described above, which reads MPD's `OK MPD ...` greeting to prove that the expected service is reachable over a bound non-VPN LAN path. Android, Windows, and the browser controller should otherwise target the same house-audio-server contract so the Pi can enforce one-session lifecycle, presence/output rules, persistent default shuffle, and HOUSE/STANDALONE behavior consistently.

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
- v0.6.1 is field-proven for the final-track drain edge where MPD `single oneshot` lands paused at 0.0 on the next track; a returning passive radio resumes that retained queue automatically;
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
- [ ] Controller-selected queues do not erase the server's saved default MP3s rotation.
- [ ] Home audio/control outages recover independently as appropriate and rejoin the current stream without resetting MPD.
- [ ] Unmuted playing departure continues the same track at the last heard position over SMB/Tailscale; muted/paused/stopped departure stays silent.
- [ ] Returning to active HOUSE playback adopts it without replacing the queue; returning to fresh idle does not auto-start MPD.
- [ ] Standalone regression tests pass: Country Buffer, read-ahead, retry/resume, filename fallback, search/sort, browser scroll, lock-screen/Bluetooth/Android Auto behavior, and Quit.

## 10. Open details and scope

Retain the unresolved choices in the canonical server document: default output mute, departure/heartbeat grace periods and background presence, whole-queue away continuation, return-to-idle-house handling, and the last muted controller leaving a paused session. Control-service protocol schema, library-path mapping setup, exact heard-position timing, and Android receiver packaging also need engineering decisions and validation. The home/away probe itself no longer needs a custom discovery or handshake protocol.

These gaps do not undo the approved behavior. They must not be filled with silent assumptions. **Two physical ESP32/PCM5102A renderers are now audibly synchronized**, so the Snapcast multi-renderer architecture itself is proven. That does **not** prove Android rendering, Android timing, or seamless phone handoff; those still require separate implementation and tests. AI DJ, Philco display, and room-management expansion are separate work, not prerequisites for this client integration.

**Current Android status:** the server-side basic MPD API, renderer presence, passive-radio appliance behavior, ordinary paused-session resume, the v0.6.1 final-track-boundary pause/resume edge, and two-node audible synchronization are all real and runtime-proven. No Android HOUSE runtime code has been added yet. Durable default-shuffle state plus controller-presence/output-state handling remain the main server prerequisites before Android HOUSE integration is considered complete. The server also has unattended renderer diagnostics for the occasional few-second single-node dropout investigation.
