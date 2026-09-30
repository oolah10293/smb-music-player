# SMB Music Player

A native Android music player that streams audio directly from SMB shares using Media3/ExoPlayer and jcifs-ng. It is intentionally optimized for unreliable networks: it buffers aggressively when bandwidth is available, preserves the current track and position through SMB outages, and retries instead of treating a network failure as a bad song.

Current source version: **0.4.0** — first Android HOUSE integration plus the approved Browser polish. Phone acceptance is pending; **v0.3.8 remains the confirmed standalone hardware baseline**. See [HOUSE_VALIDATION.md](docs/HOUSE_VALIDATION.md) for installation and the combined phone/S3 checkpoint.

The final APK is delivered, including the heartbeat recovery fix in `9c89b24`. [Release record and downloads](docs/RELEASE_0.4.0.md) identify the exact APK/source artifacts, checksums, successful Android/server CI runs, and pending installation/device checks.

HOUSE uses the existing Browser and Now Playing screens, with MPD authority through the Pi HTTP service and a bundled synchronized Snapcast receiver for phone sound. Opening HOUSE starts muted. After the first phone test, home detection is being revised so **physical non-VPN Wi-Fi/Ethernet presence determines HOUSE, while normal Android routing carries MPD/HTTP/Snapcast traffic**. This keeps Tailscale from counting as home without trying to bypass the VPN for ordinary HOUSE sockets. STANDALONE retains the existing SMB/Media3 player. Live home/away handoff remains the next recovery slice.

Server **v0.8.2** adds the queue reorder operation used by Now Playing Sort; install it for this build. The service's latest confirmed hardware baseline is v0.8.1, including fresh Rap startup with an S3 already powered during restart. The optional House server address is entered locally in the existing SMB connection panel; no private deployment address is embedded in source.

## What it does

- Browses SMB folders directly from Android.
- Plays MP3, FLAC, M4A/MP4 audio, AAC, OGG, Opus, and WAV.
- Treats folders as playlists; no tag database is required.
- Sorts by name or modified date in either direction.
- Uses one shared sort mode on Browser and Now Playing.
- Searches the current folder as the user types; audio filenames and subfolder names are filtered.
- Uses whitespace-separated AND search terms: `blink 182` matches `Blink-182`, `Blink_182`, `Blink$182`, and `Blink182`.
- `PLAY LIST` queues the complete current folder when Search is blank or only the filtered tracks while Search is active.
- Rotates a newly selected or currently playing song to queue item zero while preserving the sorted wraparound order.
- Keeps Repeat All enabled for every nonempty standalone queue.
- Provides separate Browser and Now Playing screens.
- Uses stock Media3 transport controls, buffered seek indication, Shuffle, lock-screen controls, Bluetooth/Garmin controls, and foreground playback.
- Displays embedded title/artist/album metadata with filename fallback.
- Stores the SMB password locally with Android Keystore AES/GCM encryption.
- Requests the Tailscale VPN connection at startup while still using real SMB access as the reachability test.

## Search field behavior

The Browser search field is intentionally a **40dp minimum-height** white field: the v0.3.6 version was too short and the v0.3.7 48dp field was too tall.

The X at the right side is a separate, non-focusable control:

- tapping the typing area focuses Search and opens the keyboard;
- tapping X clears the query without requesting focus or opening the keyboard;
- if the keyboard is already open, X clears the query without forcing it open or closed;
- clearing immediately restores the complete folder listing in the active sort order and does not alter the active playback queue.

## The Country Buffer™

The unusually large playback buffer is deliberate, not an accidental tuning value.

Current settings:

- minimum playback buffer: **120 seconds**
- maximum playback buffer: **600 seconds**
- target buffer size: **32 MiB**
- normal initial start threshold: **3 seconds**
- outage-recovery resume threshold: **about 20 seconds**

When the link is healthy, the player intentionally banks a large amount of compressed audio so short coverage gaps and tower handoffs do not interrupt playback. See [docs/DESIGN_NOTES.md](docs/DESIGN_NOTES.md) before changing these values.

## SMB transport behavior

jcifs-ng's `SmbFileInputStream` is unbuffered. Media extractors can issue many small reads, which is inexpensive on a LAN but expensive over a high-latency tunnel or cellular path. The player wraps the SMB stream in a **64 KiB `BufferedInputStream`** and enables `tcpNoDelay` so small extractor reads are normally served from RAM instead of each becoming a network round trip.

This is separate from the Country Buffer: one reduces SMB transaction overhead; the other stores playable media ahead of the current position.

## Outage recovery

The original recovery architecture was retained because real-use testing confirmed that it can recover successfully after a prolonged outage. The apparent delay comes from two separate phases:

1. verify that the exact SMB file is reachable again;
2. reopen it at the saved position and silently rebuild about 20 seconds of playable buffer before resuming.

v0.3.8 hardens and exposes that process rather than replacing it:

- the short retry progression remains `1 s → 2 s → 5 s → 10 s → every 15 s`;
- the overall recovery session does not end because one probe fails or times out;
- a network change brings the next **real SMB probe** forward but is not treated as proof that SMB works;
- a per-attempt watchdog prevents one stuck SMB probe from blocking every later attempt;
- a no-progress watchdog returns a genuinely stuck buffer rebuild to the retry loop while allowing slow but progressing transfers to continue;
- another outage during refill returns to waiting/retry automatically;
- Now Playing reports `Waiting for SMB`, `Checking SMB`, and `Rebuilding buffer` states;
- explicit Pause, Stop, Quit, queue replacement, audio-focus loss, and output disconnection remain authoritative.

The same media item, queue, position, sort, Shuffle setting, Repeat All policy, and user play intent are preserved through recovery where applicable. See [docs/OUTAGE_RECOVERY_PLAN.md](docs/OUTAGE_RECOVERY_PLAN.md) and [docs/TESTING.md](docs/TESTING.md).

## Confirmed standalone baseline

v0.3.8 has now been exercised on the real phone and is the current standalone baseline.

Confirmed:

- Browse does not autofocus Search or raise the keyboard.
- The 40dp search field is the desired height.
- The clear-search X behaves correctly and does not raise a hidden keyboard.
- Browser and Now Playing share the same sort state.
- Deliberate sorting keeps the current/selected song as queue item zero.
- Repeat All wraps correctly from the final queued song back to the first.
- Both screens remain locked to portrait.
- Explicit Play/Resume keeps the short ExoPlayer-only fade-in.
- Garmin/Bluetooth media commands remain working.
- Tailscale startup/recovery requests remain working in practice while SMB stays the real reachability test.
- The v0.3.6 Android Auto/audio-focus behavior remains working.
- No regressions were observed in metadata handling, Country Buffer, or SMB transport tuning.

The v0.3.8 prolonged-outage recovery hardening still needs its dedicated field test.

## Whole-house audio integration

**Initial HOUSE startup/control/audio and Browser polish are implemented in v0.4.0; phone acceptance and live home/away handoff remain pending.** Read [docs/CENTRAL_PLAYBACK.md](docs/CENTRAL_PLAYBACK.md) for the full target, implementation boundary, and acceptance checklist. [Issue #1](https://github.com/oolah10293/smb-music-player/issues/1) tracks the work. The authoritative cross-project decisions are in [house-audio-server/docs/SESSION_BEHAVIOR.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/SESSION_BEHAVIOR.md).

Keep the same folder-first Browser and Now Playing interface. The Raspberry Pi owns the house session through MPD and distributes its sound through Snapserver. The Android app controls that session; it is not a required relay or the house queue owner.

Playback authority and phone sound are separate. **HOUSE output starts muted by default. Starting/replacing a playlist must preserve the phone's current mute state.** Tapping a song or tapping PLAY LIST may start/change MPD playback, but a muted phone stays muted. The existing explicit Play action while MPD is paused/stopped may still auto-unmute. Browsing, sorting, attaching to existing playback, and Next/Previous during active playback do not auto-unmute.


- **HOUSE:** automatically discover and verify the house service directly on the home LAN. Display its current playlist/track and send `PLAY LIST`, selected-track, transport, queue-sort, Shuffle, and Repeat commands to the Pi. An unmuted phone receives the synchronized house stream; **Mute output / Unmute output** affects only this phone and belongs in the lower Now Playing Media3 control strip beside the existing transport/Shuffle/Repeat/time controls, not as a separate standalone button.
- **STANDALONE:** preserve existing SMB/Tailscale → ExoPlayer playback, buffering, and recovery. Automatic same-song continuation after leaving HOUSE is approved but not implemented in v0.4.0; that later slice must continue only a phone that was audibly playing, leaving muted/paused/stopped phones silent.

Wi-Fi and Ethernet both count as home-LAN connections. The first v0.4.0 field test showed that pinning HOUSE traffic to an Android non-VPN `Network` stops working when Tailscale is enabled even though the Pi remains reachable through normal Android routing. The revised rule is therefore: use the non-VPN Wi-Fi/Ethernet `Network` and its directly connected routes to prove the configured Pi LAN address is physically on the attached LAN; then verify the expected Pi service and carry MPD/HTTP/Snapcast traffic through normal Android routing. Keep watching that qualifying physical `Network`; losing it is the departure signal, subject to the later grace policy. Tailscale/VPN-only reachability must never count as home. mDNS, SSID matching, GPS, and a custom discovery handshake are not required unless real testing later proves otherwise. A temporary failure at home means **HOUSE reconnecting**, not permission to start a competing independent playlist.

A phone connecting to a freshly idle house waits for explicit Play; **only passive nodes auto-start** a new shuffle of the configured passive default (`MP3s` or `Rap`). Joining existing playback adopts its queue without replacing or restarting it. Closing/quitting the app detaches this phone rather than sending MPD Stop/Clear. The Pi retains controller-selected queues while other nodes remain, finishes the current track when all nodes leave, and cancels that pending stop if a node returns before track end. If only a muted phone remains, it pauses and retains the session until an audible node returns or the phone unmutes.

A completed final-track drain ends the old session, including MPD's `pause @ 0.0` boundary artifact. The next passive start uses a newly randomized order of the configured default folder. The default folder setting persists; the old shuffle order/progress does not. Chance repeats of the first song are allowed, with no forced-different-first-song rule. Reconnecting before track end preserves the existing session without reshuffling. Returning home adopts the existing house session rather than overwriting it with the phone's away queue. Exact transition timing, whole-queue away continuation, and other unresolved edges are listed in the detailed plan rather than treated as decided.

### Current house-side proof

Server **v0.6.2 was tested on the permanent Pi**. On 2026-09-29, the radio returned to the same song after about 10 seconds unplugged and to a different new song after about five minutes unplugged. The captured `/session` response confirms the short-return cancellation path, with `defaultFolder: MP3s`. The persisted MP3s/Rap settings API was subsequently field-proven in v0.7.0. Android v0.4.0 implements its selector and initial HOUSE integration, with phone acceptance pending; detailed contracts and test scope are recorded in the server API documentation.

The central architecture is now proven beyond the original single-renderer stage:

- `house-audio-server` browse/queue/state/transport control is runtime-proven;
- passive renderer presence survives the real hard-power-switch use case;
- a passive radio can power on from fresh idle and start house music without a phone;
- a hard-powered renderer can return and rejoin the still-active song; about six seconds from plug-in to audible output was observed once;
- passive-radio arrival now resumes an existing paused MPD session, and this was field-proven with two radios present;
- two independent XIAO ESP32-S3 + PCM5102A nodes have produced **audibly synchronized** output through different downstream audio systems.

Android v0.4.0 now implements the first controller/backend and receiver slice, including the default selector. Its phone/S3 synchronization and controller lifecycle remain hardware checks; live home/away handoff is later work.

A small renderer reliability issue remains under investigation: occasional few-second silence on one ESP32 node or the other. Both nodes have their external antennas installed. Server v0.6.0 adds unattended diagnostics so future dropouts can be correlated without assuming a Wi-Fi cause.

Related projects:

- [house-audio-server](https://github.com/oolah10293/house-audio-server) — Raspberry Pi MPD/Snapserver backend plus shared control/discovery layer and browser controller
- [house-audio-esp32](https://github.com/oolah10293/house-audio-esp32) — ESP32-S3 synchronized renderer nodes
- [smb-player-pc](https://github.com/oolah10293/smb-player-pc) — Windows player/controller

## Build

Requirements for v0.4.0:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk / targetSdk 36
- minSdk 26
- Java 17 and Python 3
- Android NDK 28.2.13676358 and CMake 3.22.1
- Media3 1.11.0
- jcifs-ng 2.1.10

The repository intentionally does not include a redistributed Gradle wrapper JAR. On Windows, `SETUP_GRADLE_WRAPPER.bat` downloads the official Gradle 9.6 wrapper JAR and verifies its SHA-256 checksum.

Typical setup:

1. Clone the repository to a local folder.
2. Run `SETUP_GRADLE_WRAPPER.bat` once if `gradle/wrapper/gradle-wrapper.jar` is absent.
3. Open the project in Android Studio.
4. Install the SDK/NDK/CMake versions above, run `python3 native/prepare.py`, then let Gradle sync.
5. Connect an Android device with USB debugging enabled.
6. Run the `app` configuration.

See [BUILD_AND_INSTALL.txt](BUILD_AND_INSTALL.txt) for the short version and [docs/TESTING.md](docs/TESTING.md) for regression checks.

## Project history

The project evolved through saved source checkpoints from v0.1.0 onward. Detailed rationale is preserved in [CHANGELOG.md](CHANGELOG.md).

Important historical fixes include moving playback into a foreground `MediaLibraryService`, adding the Country Buffer, adding SMB read-ahead, fixing metadata-title precedence, eliminating a large-queue sort ANR, keeping Media3 out of minimal-control mode, adding instant filename/folder search, enabling explicit media audio-focus handling for vehicle playback, and preserving the validated v0.3.7 phone/vehicle controls.

For the product-level reasons behind the app, see [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md). For the line between proven behavior and changes still requiring phone testing, see [docs/VALIDATION_STATE.md](docs/VALIDATION_STATE.md).

## Planned / possible future work

- Dedicated field validation of v0.3.8 prolonged-outage recovery hardening.
- Complete the v0.4.0 phone/S3 acceptance checkpoint for HOUSE control/audio and Browser polish.
- Implement live home/away handoff after settling the documented remaining recovery choices, then complete standalone/vehicle regression acceptance.
- `.m3u` / `.m3u8` playlist-file support.
- Smart Shuffle / listening-history database.
- Metadata-assisted filename cleanup as a separate library-maintenance tool.
- Recursive or metadata-indexed search if ever needed.
- Album-art fallback/cache improvements.
- A richer Android Auto browse tree.

The current philosophy is to keep the player simple and preserve proven playback behavior rather than add features that destabilize the transport stack.

## Privacy and security

No SMB credentials, private network addresses, personal paths, or user-specific data are committed to this repository. Saved SMB passwords are encrypted locally through Android Keystore. See [docs/PRIVACY_AND_SECURITY.md](docs/PRIVACY_AND_SECURITY.md).

## License

No license has been selected for the original app code. The isolated bundled Snapclient and its dependencies retain their own licenses; see [native/README.md](native/README.md). CI distributes corresponding receiver source and build files alongside the APK, and license texts are included in APK assets.


### Passive-default API field proof

The server half of the planned HOUSE Browser `MP3s` / `Rap` button is now field-proven on the permanent Pi.

- `GET /settings` reported the current default and allowed values.
- `POST /settings` changed the default from `MP3s` to `Rap`.
- The currently playing song did not change when the setting was changed.
- After the last S3 stayed off for about ten minutes and the old session completed, powering the S3 back on started a fresh Rap session (first observed track: Ludacris — *Southern Hospitality*).

Android v0.4.0 uses this field-proven passive-default API. Controller presence/output state is implemented in the deployed server; physical phone transition validation remains pending.

### Server v0.8.0 dependency update

Controller presence and muted-phone session handling are implemented/tested in server v0.8.0 (73 tests and CI pass). v0.8.0 is installed on the permanent Pi; initial health/passive-S3 baseline checks pass, while physical controller transition tests remain pending.

Background/screen-off controllers retain presence through five-second heartbeats and fifteen-second expiry. If the last controller leaves an automatically paused session, the Pi ends it without advancing. Phone control/audio roles are counted once, and known phone renderers cannot become passive auto-starters. Android v0.4.0 now supplies HOUSE wiring, the synchronized receiver, and the approved Browser polish, pending phone acceptance; see [CENTRAL_PLAYBACK.md](docs/CENTRAL_PLAYBACK.md).


### v0.8.0 controller backend deployment

The server-side controller/output contract required by Android HOUSE mode is now deployed on the permanent Pi.

Initial validation:

- server health is `ok` on v0.8.0;
- MPD and Snapserver remain healthy;
- with no controller attached, the existing S3 is still correctly classified as a passive renderer;
- `GET /controllers` reports zero controllers, one present passive renderer, and matching present/audible counts.

Physical muted-controller pause/resume/expiry behavior still needs field testing before being called proven.

Restart behavior is now also settled: a `house-audio-server` restart is a fresh-session boundary. Android should simply reattach after restart; it must not expect the old live lease, mute/readiness report, auto-pause reason, queue/session, or pending drain to be reconstructed. Durable controller↔renderer ownership and the server's passive-default setting remain persistent. Controller reconnect alone stays idle; a passive S3 may start a new shuffled default session.

### Server v0.8.1 restart boundary — deployed / field-proven for radio-already-present restart

Server restart now clears the old MPD queue/session to fresh idle before accepting playback writes. The Pi keeps the saved MP3s/Rap default and controller↔renderer ownership. Controller reconnection alone stays idle; a passive S3 starts a newly shuffled default. Ordinary dependency reconnections within the running service do not reset its session.

Android must reattach with a new lease, preserve its own mute intent, and respect `startup.ready` / 503 `startup_pending`; it must not restore its former queue into MPD. The server has 85 passing local tests and GitHub CI passed. v0.8.1 is now the confirmed Pi deployment. With one passive S3 already powered during the service restart, startup reached ready and a fresh randomized Rap session started; the server snapshot reported `lastAction: started_default_session`. Physical controller pause/resume/expiry validation remains pending, and the all-radios-off restart variant has not yet been separately exercised.

That server release made no Android or ESP32 firmware change. The subsequent Android v0.4.0 source now implements HOUSE through the existing UI plus Browser polish and the saved-default selector. The next checkpoint is the combined phone/S3 acceptance session; no ESP32 firmware update is required.


### v0.4.0 first phone field findings

The first real-phone HOUSE startup exposed three actionable issues/decisions:

- The Pi's MPD daemon was initially listening only on loopback, so no LAN client could complete the HOUSE identity probe. The permanent configuration must keep MPD available on localhost **and** the configured home-LAN address. No private deployment address belongs in source/docs examples.
- After the LAN MPD listener was enabled, Android v0.4.0 successfully entered HOUSE with Tailscale off and Now Playing adopted the track already playing on MPD.
- Turning Tailscale on while HOUSE was active caused the app to stop updating, while the phone browser could still read the house server JSON over the same LAN address with Tailscale either on or off. This isolates the problem to v0.4.0's explicit Android-`Network` transport binding, not Pi reachability.

The corrective networking design is to use the physical non-VPN network for **presence/identity and departure detection only**, while ordinary HOUSE MPD/HTTP/Snapcast connections use normal Android routing.

Two UI/behavior corrections are also locked from this field pass:

- starting a new playlist/selected track must **not unmute the phone**; preserve the current local mute state;
- the HOUSE **Mute Output / Unmute Output** control belongs in the lower Now Playing Media3 control strip with the existing Shuffle/Repeat/time controls, not as a separate standalone button.

These are follow-up requirements to the delivered v0.4.0 build, not claims that the current APK already satisfies them.
