# Independent Android SMB and HOUSE apps

This document is the **normative behavior and architecture contract for the two independent Android apps**. Current source/build identity belongs in the release record; current real-device pass/fail status belongs in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md); historical validation belongs in [VALIDATION_STATE.md](VALIDATION_STATE.md).

The authoritative product rules are in [house-audio-server/docs/SESSION_BEHAVIOR.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/SESSION_BEHAVIOR.md). This document translates those decisions into Android requirements and identifies the corresponding implementation work. Engineering proposals and unresolved details below are not additional user-approved behavior.

**2026-09-30 clarification:** HOUSE phone renderer eligibility requires Bluetooth audio. Transport commands never grant eligibility or independently unmute the phone. This supersedes the earlier pre-command audible-state auto-unmute policy. Existing server pause/retention rules remain in force. Implementation and physical acceptance are tracked separately.

**Superseding decision, 2026-10-01:** the user wants two completely independent Android apps, with no connection between them. SMB Music owns its standalone session; the HOUSE app controls/renders the Pi session. Remove automatic home/away switching and all automatic or manual session transfers from the planned product. Neither app shares a queue/song/position, resets the other session, launches the other app, or implements special cross-app stop/start coordination. Listening through SMB has no effect on HOUSE, including its next passive-node queue. This replaces the 2026-09-30 handoff requirement and the later proposed transition-coordinator redesign. SMB Music v0.5.0 implements the standalone side of this split. The combined v0.4.3 source is preserved on `house-music-pre-split`; the separate **House Music** app is still pending. Server v0.9.0 is unchanged.

Tracking: [Android Issue #1](https://github.com/oolah10293/smb-music-player/issues/1).

Current implementation/build references are in [RELEASE_0.4.3.md](RELEASE_0.4.3.md); current physical acceptance is in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md).

## 1. Separate playback authority from phone sound

There are two separate applications, each with its own session and lifecycle:

| State | Playback controls and Now Playing | Phone sound |
| --- | --- | --- |
| HOUSE, Bluetooth audio eligible and output unmuted | Control/display the Pi's one MPD session | Receive the synchronized Snapcast stream when the receiver is ready and MPD is playing |
| HOUSE, output muted | Control/display that same MPD session | Silent controller; no independent song |
| SMB Music app | Control/display its own local Media3/ExoPlayer session | Existing SMB/Tailscale playback when playing |

The phone is a **controlling node**, even when it also renders audio. It must not register as a passive radio just because its output is unmuted.

SMB Music uses its own Browser/Now Playing, foreground service, Media3/ExoPlayer, saved queue and encrypted SMB settings. The HOUSE app uses its own Browser/Now Playing, controller service and Snapcast receiver. Users choose which app to open. Ordinary Android audio-focus handling remains local platform behavior; do not add app-specific coordination between them.

HOUSE control and HOUSE audio remain separate connections and responsibilities. MPD owns the house queue and transport; the phone is not a music relay. The HOUSE app must not use SMB/ExoPlayer as a fallback source for its synchronized output.

## 2. HOUSE app home-LAN qualification and reconnection

The first v0.4.0 phone test changed one important implementation detail: **physical home-LAN presence and HOUSE packet routing are separate concerns.**

The qualifying network remains a real non-VPN Wi-Fi/Ethernet Android `Network`, but ordinary HOUSE sockets must not be pinned to that `Network`. In the tested environment, explicit `Network.openConnection` / `Network.socketFactory` traffic stopped updating when Tailscale was enabled even though the Pi remained reachable through normal Android routing.

Revised HOUSE detection is:

1. Find an available Wi-Fi or Ethernet Android `Network` that is not a VPN transport.
2. Inspect that physical network's `LinkProperties` / routes. The locally configured house LAN address must fall on a **directly connected route of that physical network**. This is the key physical-home evidence; a Tailscale route by itself does not qualify.
3. Using normal Android routing, verify the expected Pi identity at that LAN address, such as the MPD `OK MPD ` greeting and/or the expected `house-audio-server` identity.
4. Only the combination of a qualifying physical route plus the expected Pi identity qualifies a connection in the HOUSE app. It does not select or switch applications.
5. Continue watching the qualifying physical `Network`. Losing that network/route makes HOUSE unavailable; never open or start SMB Music.

Once HOUSE is selected, MPD/HTTP/Snapcast traffic uses **normal Android routing**. Do not pin those sockets to the physical `Network`; Tailscale may remain connected. The physical network establishes **HOUSE availability**, not packet egress or selection of another app.

This preserves the core rule: **Tailscale/VPN-only reachability never qualifies HOUSE.** Away from home, a VPN route to the Pi cannot substitute for the missing directly connected physical-LAN route. An unrelated Wi-Fi using a similar private subnet still has to pass the expected Pi identity check.

The house LAN address is deployment configuration, not a source-code constant. Keep the current address in local app configuration rather than committing it into Android source or public examples.

The Pi must also expose MPD on both localhost and its configured home-LAN listener. The first field test found MPD bound to localhost only, which made every remote HOUSE probe impossible until the LAN listener was added. Do not publish the private deployment address.

A home-server, Wi-Fi, MPD, HTTP, or audio failure leaves the HOUSE app unavailable/reconnecting. Loss or return of the home network never switches apps, opens an SMB file, copies a track, or changes SMB Music. SMB Music handles its own Wi-Fi/cellular outages through its existing SMB recovery, regardless of HOUSE availability.

Show the active control target and distinguish control-server failure from audio-receiver failure where possible. Suggested status wording includes `HOUSE - reconnecting`, `House server unavailable`, and `Audio reconnecting`. Start retrying when failure is detected, not only after an audio buffer empties. Recovery joins the current house position, not an old backlog.

A **physical network gain/change** that creates a qualifying directly connected home route is a high-value recovery signal. It must bring the next HOUSE identity/control probe forward immediately and reset/override an ordinary retry backoff. Do not leave a phone that has returned to home Wi-Fi sitting on a previous `Socket closed / Retrying in 15s` timer before trying the newly available home path. A network-change signal triggers a real probe; it is not itself proof that HOUSE is healthy.

## 3. Folder browsing, queue control, and shared state

Preserve **folders are playlists**, Browser/Now Playing separation, browser scroll position, current-folder search, explicit sort modes, and filename fallback. Do not replace them with a metadata-first library UI.

Required HOUSE behavior:

- Fetch the current house track, queue, position/duration, transport, shuffle, and repeat state on attachment. Show the existing playlist even when another controller selected it.
- Merely opening the HOUSE app or browsing does not issue Play or replace the house queue. SMB Music has no connection to HOUSE and never uploads its session.
- Preserve `PLAY LIST`: with blank search, submit the current folder's audio tracks in the selected order; with active search, submit only matching tracks. Preserve the current multi-term AND filename/folder search semantics.
- Selecting a track retains the normal current-list behavior and starts at that selected item. Send library-relative track identities and the selected start item/index, not phone-specific SMB URLs or Linux absolute paths as the public control contract.
- Play/Pause, Previous/Next, Seek, Shuffle, and Repeat operate on MPD through the house service. Queue-sort actions are deliberate server queue changes; merely sorting a browser view is not.
- Preserve the current song, position, and transport state when explicitly reordering the active queue. Do not restart a song or send hundreds of separate reorder commands just to reproduce a sort.
- Receive updates made by the Windows player, browser, or another controller. Server state wins over stale phone UI state. Do not replay an old queue replacement after reconnecting.
- Obtain display metadata/artwork where available, retaining filename fallback without modifying the music files. Display the server queue separately from whatever folder the user is browsing; exact queue-screen placement is not yet chosen.

The browser controller is also part of the agreed system. It and the Android/Windows clients should use one Pi-side control contract, not unrelated playback backends on the server.

## 4. HOUSE-only Mute output button

**Queued correction, 2026-10-01:** the user wants manual Mute/Unmute to work on the current Android output, including a headphone jack, without requiring Bluetooth. Bluetooth automation stays. Implementation is explicitly deferred in [ROADMAP.md](ROADMAP.md); the Bluetooth-only Unmute restriction described below is shipped v0.4.3 behavior, not the final manual-button requirement.

Show **Mute output** in HOUSE mode; change it to **Unmute output** when muted. This controls this phone's renderer only, not MPD volume, global mute, or an unconditional MPD Pause.

Keep the controller connected while muted and report its output state separately. Preserve the mute choice across ordinary network reconnections, subject to the Bluetooth-route rules below. A local Unmute request requires a connected Bluetooth audio output; it cannot enable the handset speaker. While other rooms are playing, an eligible output unmuting joins the current house position. When the server has automatically paused a retained session because only a muted controller remains, eligible output return can resume that retained session.

Keep Android media-output routing, local volume, and interruptions separate from intentional house transport commands. A call, headphone disconnection, audio-focus change, or local renderer failure must not masquerade as the user pressing house Pause. The server still applies its presence/output policy to the actual remaining nodes.

**No Bluetooth audio output means the HOUSE phone renderer is ineligible and muted.** Bluetooth audio connected means eligible; actual sound also requires a ready receiver, unmuted output, and Playing transport. Eligibility and transport are separate state. Play/Resume, selected-track/PLAY LIST, queue changes, app reopening, node joins/leaves, and network recovery must all use that same eligibility rule. None may enable phone-speaker playback or unmute merely because MPD was paused/stopped or `audibleCount == 0`.

Pause/Resume preserves the local renderer decision unless route eligibility changes. In particular, a phone without Bluetooth that pauses two audible S3 nodes must remain muted when Resume restarts those nodes. An explicit manual mute also survives transport commands; the route-connect behavior below remains a separate output event. Apply the rule to on-screen, notification, lock-screen, and media-session command paths.

Place the HOUSE-only **Mute Output / Unmute Output** control in the **lower Media3 Now Playing control strip beside the existing transport/Shuffle/Repeat/time controls** so the main Now Playing layout remains otherwise unchanged. A separate standalone mute button above that strip is not the desired UI.

### Bluetooth route policy

Bluetooth **audio** route state determines phone renderer eligibility. A paired device or an input-only watch is not a qualifying connected media output. The built-in speaker is not a HOUSE fallback.

- Bluetooth audio disconnect -> mute/unavailable phone output; never directly send MPD Pause/Stop.
- If another HOUSE output remains audible, shared playback continues.
- If the phone becomes the only remaining muted controller, the server's existing policy auto-pauses and retains the exact HOUSE queue/song/position.
- Bluetooth audio connect while HOUSE is already playing -> auto-unmute the phone and join the current synchronized stream, overriding a prior manual phone mute.
- HOUSE attach/reopen must evaluate the **current** output route. If Bluetooth is already connected and HOUSE is already playing, join unmuted immediately; do not wait for a new connection callback.
- Bluetooth connect or pre-existing Bluetooth alone must not start fresh idle or a deliberate Pause/Stop. If the user subsequently starts music, the already-eligible, ready, unmuted output can render; Play is not an unmute command.
- Bluetooth reconnect can resume a retained session automatically paused by the server because the muted phone was the only remaining node. The client reports output state; it does not turn every route callback into global Play/Pause.

### STANDALONE Bluetooth parity

In STANDALONE/SMB, Bluetooth also triggers the lifecycle of the phone-owned session:

- Bluetooth audio connect makes the output eligible and starts/resumes an available retained SMB session at its saved position.
- Bluetooth disconnect pauses/silences local playback and retains the exact standalone queue/song/position.
- Bluetooth reconnect resumes that retained standalone session.
- SMB Music app start must evaluate an already-connected Bluetooth route instead of requiring a new callback.
- Explicit Stop/Quit wins; a route event or stale callback must not resurrect playback that was explicitly ended.
- With no retained standalone session, Bluetooth connection alone starts nothing.

These are SMB Music's own Bluetooth rules. They never depend on HOUSE state, transfer its queue, reset HOUSE, or trigger a switch to the HOUSE app. The HOUSE app independently handles its own output and connection lifecycle.

### Passive-node default playlist selector

HOUSE Browser needs a compact selector for the Pi's passive-radio default folder.

Keep the familiar Browser button position in each separate app:

- SMB Music: button remains SMB/settings.
- HOUSE app: button displays `MP3s` or `Rap` and toggles the Pi's persisted passive-node default. It has no effect on SMB Music.

The setting is server-owned because it affects S3 startup even when the phone is absent. Changing it must not replace, restart, or otherwise disturb the current house queue. It applies only when the house later enters a genuinely fresh passive-renderer auto-start session.

Server v0.7.0 implements the required persisted setting: `GET /settings` returns `settings.passiveDefaultFolder` and `settings.allowedPassiveDefaultFolders`; `POST /settings` accepts exactly `{"passiveDefaultFolder":"MP3s"}` or `{"passiveDefaultFolder":"Rap"}` and returns the saved settings. The phone should read the server value and send an explicit choice, then use the acknowledged value. Refresh after reconnect instead of replaying stale edits. Neither endpoint touches playback or registers controller presence. `PASSIVE_DEFAULT_FOLDER` remains the fallback when no saved value exists. The server setting is field-proven in v0.7.0; Android v0.4.0 implements the button, with phone acceptance pending.

## 5. Controller presence and the Pi's session rules

The Android client must report **controller presence** and **renderer/output state** independently so a muted phone is not treated as either an absent device or an audible speaker. Correlate the two roles to one device; do not count its control and audio sockets as unrelated people/nodes.

The Pi, not every app independently, applies these agreed rules:

| Situation | Required result |
| --- | --- |
| Fresh idle; a phone/PC/browser merely connects first | Wait for explicit Play/selection, even with output unmuted. A passive node auto-starts the configured default under the ordinary server rules. |
| SMB Music is playing, stops, or changes network | No HOUSE queue reset, transfer, playback command, controller registration or reservation. A passive node follows the normal HOUSE rules independently. |
| Existing music; a phone joins | Adopt/display the existing queue and track; join sound only if unmuted. Do not restart or replace the queue. |
| Controller changes the queue, then leaves while other nodes remain | The new house queue remains in effect. |
| All nodes disconnect during playback | Finish the current track, then stop despite Repeat All. |
| Any node reconnects before that final track ends | Cancel the pending stop and retain the current session. Apply the muted-only pause rule if applicable. |
| Only a muted phone remains after the last audible node leaves | Pause MPD and retain the queue, track, and exact position. |
| An audible node returns or the muted phone unmutes after that automatic pause | Resume the retained session, not a new default queue. A passive radio powering on is explicitly allowed to resume an existing paused session; controller attachment alone is not. |

Every genuinely fresh passive-node session starts the configured default folder (`MP3s` or `Rap`) with Shuffle and Repeat All and a newly randomized order. The selected folder setting persists; shuffled order/progress does not survive a completed session. Chance repeats of the first song are allowed, with no forced-difference rule. Returning before the final track ends preserves the existing session unchanged. After a completed drain, MPD's possible `pause @ 0.0` on the next old-queue track is only an artifact: the next passive start must load a fresh default queue, not resume the old controller selection. Ordinary paused sessions remain resumable. Android attachment by itself must not reshape the queue or impose passive defaults on manually selected queues. Independent SMB playback does not participate in or suppress passive HOUSE startup. All HOUSE renderers still share one authoritative MPD session.

**HOUSE Quit must detach this phone, not send global Stop/Clear.** The current standalone Quit implementation stops and clears its local player; that behavior must remain standalone-only. Other rooms continue under the server's rules. If this phone was the final node, the server handles the final-track stop; the app does not implement its own competing shutdown logic.

HOUSE Quit must also clear **phone-local HOUSE presentation/cache state**: cached track/metadata/position/queue and stale HOUSE availability must not survive Quit as if they were current authoritative playback. On a later launch the app must freshly qualify HOUSE and fetch current server state before presenting a HOUSE track. If MPD is genuinely still playing the same song, that song may legitimately appear again only after the fresh server adoption.

The 2026-09-29 choices settle these edges: background/screen-off apps remain controllers while sending five-second heartbeats, with fifteen-second expiry; Quit detaches immediately. The last controller leaving an automatically paused session ends it without advancing the song. A still-audible renderer can survive control-lease expiry as an output, counted once for that device. A stale established socket alone is not presence.

## 6. No handoff or shared session

The 2026-10-01 decision removes both directions of Android handoff entirely:

- Leaving home does not copy HOUSE music into SMB Music or launch/resume that app.
- Returning home does not upload the SMB queue, reset HOUSE, or start/stop either app on behalf of the other.
- There is no manual “send to HOUSE” action, shared session snapshot, shared playback position, cross-app control channel, or background queue synchronization.
- The apps do not need a HOUSE-to-SMB root mapping. HOUSE browses library-relative paths through its server API; SMB Music browses its own configured share.
- The HOUSE app reconnects to the current server session when its own connection recovers. SMB Music retains and recovers its own session. Neither recovery depends on the other app.

The v0.4.3 mapping, heard-position handoff, transfer journal, temporary controller, and reservation/commit workflow describe the shipped combined implementation, not requirements to carry into either independent app. Server v0.9.0 handoff endpoints remain deployed implementation history; the new apps do not use them. No runtime removal or migration is claimed by this documentation update.

## 7. Required house-service contract

The basic `house-audio-server` HTTP/MPD bridge now exists and has been runtime-validated on the permanent Pi. Proven server-side operations include health/state, full queue inspection, folder-first library browsing, ordered queue replacement with a requested start index, Play/Pause/Stop, Seek, Previous/Next, Random/Shuffle, and Repeat. Transport-only commands were also confirmed not to rebuild the MPD queue unnecessarily.

The server has now advanced beyond the basic control core: Snapserver renderer presence is also runtime-proven on the permanent Pi, including abrupt hard-power-off. The service distinguishes Snapserver's stale raw `connected` state from effective renderer `present` state using `lastSeen` freshness, and power-on/off transitions are reliable enough to drive policy.

The passive-renderer side has now advanced beyond the basic primitives: fresh-idle passive-radio auto-start, active-session join/rejoin, effective hard-power presence, and passive-radio resume-through-Pause are runtime-proven on the permanent Pi. Two independent ESP32/PCM5102A renderers have also passed the real audible synchronization test.

Server v0.8.0 implements controller leases, output reporting, device/renderer association, muted-controller pause/resume, and stale lifecycle-report rejection in source/tests (Pi validation pending). Persisted passive-default selection is field-proven in v0.7.0. General transport-command deduplication remains separate work; v0.8.2 guards queue reordering by revision and IDs. Clients must not replay uncertain Next/queue writes.

The Android app should **not use MPD's native control port as its HOUSE control API**. MPD remains useful only as a tiny service-identity check after the app has already established that the configured LAN address lies on a directly connected non-VPN physical network. Android, Windows, and the browser controller should otherwise target the same house-audio-server contract so the Pi can enforce one-session lifecycle, presence/output rules, fresh-session default shuffle, consistently. SMB Music does not call this service.

The Android client targets the HTTP API documented in [house-audio-server/docs/API.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/API.md) for browse/queue/transport operations without any SMB-session transfer calls. Server v0.9.0 remains the confirmed deployed version. A push-state feed is still optional; the client polls authoritative state. The Snapcast audio connection remains distinct from the control service.

The Android client needs:

- **Identity/capabilities:** control-service identity, protocol version, supported commands, and stream connection information. HOUSE presence itself is established separately by the direct MPD LAN probe.
- **Library browsing:** relative folder/track identities, names, type, modified time for sort, and available metadata/artwork. The HOUSE app uses these endpoints; SMB Music independently retains its SMB browser.
- **Session snapshot and updates:** session/queue identity, queue entries and current entry, transport, shuffle/repeat, position/duration and timing context, pending-stop/automatic-pause reason, plus a revision or equivalent stale-state check.
- **Commands:** replace/play the selected ordered list with a start item, deliberate transport commands, queue reordering, shuffle/repeat, and per-device output-state changes. Read/attach operations must not mutate playback.
- **Presence:** stable app-device identity, controller role, associated renderer identity, mute/output state, connection lifecycle, and a defined heartbeat/expiry policy.
- **Errors and concurrency:** unsupported capability and unavailable-server responses; command acknowledgement and request identity or equivalent protection against duplicate Next/queue commands after retries. Reconcile against current server state after reconnect.

Credentials stay local and out of Git/logs. Keep existing encrypted SMB storage separate from any house-service trust/token configuration. Do not embed private network addresses or user-specific filesystem roots in Android source or public examples.

## 8. Standalone split and pending House Music extraction

SMB Music v0.5.0 implements the standalone app; the separately named **House Music** APK is still pending. Preserve independent app identities, settings, sessions, notifications and services when extracting House Music. The combined source is retained on [house-music-pre-split](https://github.com/oolah10293/smb-music-player/tree/house-music-pre-split).

Use v0.3.8 as the standalone source reference, selectively retain the later non-HOUSE improvements, and extract HOUSE from the latest combined implementation. The exact pre-HOUSE checkpoint and reviewed change inventory are maintained once in [ROADMAP.md](ROADMAP.md#split-references-and-carry-forward-inventory--reviewed-2026-10-01).

SMB Music keeps the proven ExoPlayer/SMB transport, Country Buffer, outage recovery, encrypted credentials, retained session, Bluetooth/vehicle controls and Quit behavior. Its service has no HOUSE discovery, controller lease, Snapcast receiver, mapping or handoff responsibilities.

The HOUSE app keeps `HousePlayer`/`HouseRuntime`, the control API, native Snapcast receiver, synchronization correction, S8-compatible Android API handling and HOUSE-local Bluetooth/mute behavior. Its service has no SMB decoder, credential store, private queue recovery or transfer journal. HOUSE Quit detaches only this app's controller/renderer; it never issues MPD Stop/Clear just to close the app.

Do not preserve the combined service's player-switching boundary as a hidden coordinator or recreate it through communication between apps. Packaging/source organization and installation migration are implementation details; they must preserve app independence. The manual mute-button correction remains queued and explicitly deferred in [ROADMAP.md](ROADMAP.md); splitting apps does not remove Bluetooth automation.

## 9. Acceptance checklist

These are acceptance requirements. Their current pass/fail status is maintained only in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md); source implementation alone is not field proof.

- [ ] The HOUSE app requires a qualifying directly connected non-VPN Wi-Fi/Ethernet route plus a successful Pi identity probe using normal Android routing. Loss/recovery never launches or controls SMB Music.
- [ ] Fresh-idle HOUSE controller attachment alone makes no playback/queue mutation. Passive-node default start remains server-owned and independent of SMB Music.
- [ ] Joining active playback displays the real house playlist, track, position, shuffle/repeat, and changes from other controllers.
- [ ] Folder/filtered `PLAY LIST`, selected-track start, transport, and explicit queue sort match existing semantics through the server API.
- [ ] Phone HOUSE audio uses a real synchronized receiver; no independent ExoPlayer copy plays alongside it.
- [ ] Pause/Resume and queue changes preserve local mute; a muted phone stays muted while S3 playback resumes. Automatic output follows the Bluetooth rules. Shipped v0.4.3 also gates manual Unmute on Bluetooth; that restriction is interim and subject to the explicitly deferred manual-override correction in §4.
- [ ] HOUSE-only Mute/Unmute affects this phone, survives ordinary network reconnect under the route rules, and preserves the server's muted-controller pause/resume behavior.
- [ ] HOUSE Quit/controller departure preserves other listeners and their queue; final-node and reconnect-before-track-end behavior follow the Pi rules.
- [ ] Return before drain completion preserves the active queue. After completed drain, the next passive arrival uses the configured default with fresh randomness and permits chance repeats. No saved rotation/bookmark from the completed session is required.
- [ ] Home audio/control outages recover independently as appropriate and rejoin the current stream without resetting MPD.
- [ ] SMB Music playback, pause, Quit, app launch and network changes never read/write HOUSE session state or reset its queue. HOUSE app actions never copy, launch, reset or coordinate the SMB session.
- [ ] Both apps install separately and retain their own settings/session state; no handoff controls or shared playback state remain.
- [ ] HOUSE reconnection adopts its current server session without replaying a stale queue or inventing Playing intent.
- [ ] Standalone regression tests pass: Country Buffer, read-ahead, retry/resume, filename fallback, search/sort, browser scroll, lock-screen/Bluetooth/Android Auto behavior, and Quit.

## 10. Open details and scope

Automatic departure/return, whole-queue copying, cross-library mapping and a replacement handoff coordinator are no longer open product questions: the user removed the connection between the apps. Preserve the established server lifecycle, presence/expiry, synchronization and output rules. Check app separation, each app's recovery, and retained working behavior in the next implementation; do not carry forward handoff acceptance gates.

These product decisions do not prove the split works on hardware. Current source is standalone SMB Music v0.5.0; the combined v0.4.3 field results and frozen release records retain its actual failures. AI DJ, Philco display, and room-management expansion are separate work.

Implementation and field evidence are tracked outside this contract. Use [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md) for Android acceptance and the server [API.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/API.md) for the deployed control contract.

### Controller lease and renderer-ownership contract

The server supplies `GET /controllers` and `POST /controllers/attach`, `/controllers/heartbeat`, and `/controllers/detach`; use the server API for complete schemas.

Register the stable app-device id and stable Snapcast renderer id **before connecting that receiver**. Ownership is persisted so a known phone cannot later masquerade as a passive auto-starter. Use the returned lease id and increasing sequence numbers; reject callbacks from older attachments. Expired leases require a new attachment with the current local mute choice.

Heartbeat every five seconds and treat fifteen seconds without renewal as expiry. Report `outputMuted` separately from `outputReady`. Ready means the local receiver/output path can render even if MPD is paused. The server requires both an audible associated renderer and unmuted/ready controller state before counting the phone as audible.

On HOUSE attachment, evaluate current Bluetooth audio eligibility before allowing local sound: no Bluetooth means muted. If Bluetooth is already connected and HOUSE is already playing, join unmuted without waiting for a new route callback. Existing Bluetooth alone must not start fresh idle or deliberate Pause/Stop. Report effective mute/readiness truthfully; transport commands never override eligibility.

Keep the heartbeat alive through the foreground/background service lifecycle. HOUSE Quit stops the phone receiver/heartbeat and detaches the controller; it never sends MPD Stop/Clear merely because the app is closing.

### Server restart/startup contract

A `house-audio-server` restart is a hard listening-session boundary.

Android must treat its old lease as dead, attach again, and avoid replaying stale house transport/output state or uploading an old queue. The server preserves durable settings/renderer ownership but discards live leases, old queue/session state, automatic-pause ownership and pending drain state.

While `startup.ready` is false, show startup/reconnecting and treat old queue/state reads as provisional. MPD-changing writes may return `503 startup_pending`; settings and controller-lifecycle calls remain available. Once ready, refresh authoritative state. A controller reconnecting first remains idle; a passive S3 may already have started a fresh configured default session.

## HOUSE Country Buffer

The whole-house path should adopt the same basic resilience philosophy as the standalone Country Buffer, but at a much smaller time scale: **use a deliberately generous multi-second Snapcast playout buffer so brief LAN/Wi-Fi contention does not become audible.**

This is a design requirement, not a claim that the currently deployed stack already implements the final value.

Required direction:

- Keep Snapcast source chunking small (currently about `20 ms`). **Chunk size and playout-buffer depth are separate controls.** A multi-second buffer does not imply multi-second packets.
- Increase the HOUSE synchronized playout buffer beyond the current ~`1000 ms` baseline and tune it experimentally. Exact production depth is not yet locked; start with several seconds rather than one second.
- The purpose is to absorb short Wi-Fi/LAN stalls and provide headroom for renderer-specific output-latency compensation.
- Deliberate user controls—Play/Pause, Next/Previous, Seek, selected-track/PLAY LIST changes—must not be designed around waiting for the entire old playout buffer to drain. Obsolete buffered audio should be invalidated/rebased as promptly as the Snapcast/client stack permits, then resume on the new synchronized timeline.
- Verify the actual upstream Snapcast behavior for discontinuities/flush/rejoin before treating instant stale-buffer invalidation as implemented. If the current client/server path cannot do that cleanly, choose the largest buffer that preserves acceptable control responsiveness or add an explicit reset/rejoin mechanism.
- A larger HOUSE buffer is allowed to increase node power-on/rejoin time and overall MPD-to-speaker latency within reason; continuity and synchronization are more important than sub-second command-to-sound latency for music playback.
- Phone/S3 per-client latency calibration remains a separate feature from the shared HOUSE buffer. The shared buffer provides timing headroom; the client offset compensates a repeatable output-path delay.

The reversible experiment, current deployment status, and renderer-dropout motivation are tracked in the server's [HOUSE_BUFFER_TRIAL.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/HOUSE_BUFFER_TRIAL.md) and ESP32 Issue #3 rather than duplicated here.

## Implementation status reference

Current field results are intentionally kept out of this architecture document. Use [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md) for the current pass/fail checkpoint and [VALIDATION_STATE.md](VALIDATION_STATE.md) for historical validation.
