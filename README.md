# SMB Music Player

A native Android music player that streams audio directly from SMB shares using Media3/ExoPlayer and jcifs-ng. It is intentionally optimized for unreliable networks: it buffers aggressively when bandwidth is available, preserves the current track and position through SMB outages, and retries instead of treating a network failure as a bad song.

Current version: **0.3.8**.

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

## Future whole-house audio integration

**Approved requirements; not implemented in the current Android player.** Read [docs/CENTRAL_PLAYBACK.md](docs/CENTRAL_PLAYBACK.md) for the Android functionality, code integration points, server-contract needs, and acceptance checklist. [Issue #1](https://github.com/oolah10293/smb-music-player/issues/1) tracks the work. The authoritative cross-project decisions are in [house-audio-server/docs/SESSION_BEHAVIOR.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/SESSION_BEHAVIOR.md).

Keep the same folder-first Browser and Now Playing interface. The Raspberry Pi owns the house session through MPD and distributes its sound through Snapserver. The Android app controls that session; it is not a required relay or the house queue owner.

Playback authority and phone sound are separate:

- **HOUSE:** automatically discover and verify the house service directly on the home LAN. Display its current playlist/track and send `PLAY LIST`, selected-track, transport, queue-sort, Shuffle, and Repeat commands to the Pi. An unmuted phone receives the synchronized house stream; **Mute output / Unmute output** affects only this phone.
- **STANDALONE:** away from home, preserve existing SMB/Tailscale → ExoPlayer playback, buffering, and recovery. An unmuted phone that was hearing house music automatically continues the same song at its last heard position; muted/paused/stopped phones stay silent.

Wi-Fi and Ethernet both count as home-LAN connections. HOUSE detection is intentionally simple: bind a short connection to a **non-VPN** Wi-Fi/Ethernet Android network, probe the locally configured house LAN address on MPD port **6600**, and require MPD's normal `OK MPD ...` greeting. That probe identifies home presence only; normal HOUSE control still uses the shared house-audio-server layer. Tailscale/VPN-only reachability must not count as home. mDNS, SSID matching, GPS, and a custom discovery handshake are not required unless real testing later proves otherwise. A temporary failure at home means **HOUSE reconnecting**, not permission to start a competing independent playlist.

A phone connecting to a freshly idle house waits for explicit Play; **only passive nodes auto-start** the saved default `MP3s` shuffle. Joining existing playback adopts its queue without replacing or restarting it. Closing/quitting the app detaches this phone rather than sending MPD Stop/Clear. The Pi retains controller-selected queues while other nodes remain, finishes the current track when all nodes leave, and cancels that pending stop if a node returns before track end. If only a muted phone remains, it pauses and retains the session until an audible node returns or the phone unmutes.

The default MP3s shuffle progress is intended to be stored on the Pi separately from controller-selected queues, so reconnecting the app never resets the rotation. **Durable saved shuffle order/progress is not implemented yet.** Returning home adopts the existing house session rather than overwriting it with the phone's away queue. Exact transition timing, initial mute preference, whole-queue away continuation, and other unresolved edges are listed in the detailed plan rather than treated as decided.

### Current house-side proof

The central architecture is now proven beyond the original single-renderer stage:

- `house-audio-server` browse/queue/state/transport control is runtime-proven;
- passive renderer presence survives the real hard-power-switch use case;
- a passive radio can power on from fresh idle and start house music without a phone;
- a hard-powered renderer can return and rejoin the still-active song; about six seconds from plug-in to audible output was observed once;
- passive-radio arrival now resumes an existing paused MPD session, and this was field-proven with two radios present;
- two independent XIAO ESP32-S3 + PCM5102A nodes have produced **audibly synchronized** output through different downstream audio systems.

Therefore Android HOUSE work is no longer blocked on proving that multi-room Snapcast playback can work. The remaining phone-specific work is the controller/backend integration, controller/output presence rules, durable default-shuffle state, and packaging a synchronized Android renderer.

A small renderer reliability issue remains under investigation: occasional few-second silence on one ESP32 node or the other. Both nodes have their external antennas installed. Server v0.6.0 adds unattended diagnostics so future dropouts can be correlated without assuming a Wi-Fi cause.

Related projects:

- [house-audio-server](https://github.com/oolah10293/house-audio-server) — Raspberry Pi MPD/Snapserver backend plus shared control/discovery layer and browser controller
- [house-audio-esp32](https://github.com/oolah10293/house-audio-esp32) — ESP32-S3 synchronized renderer nodes
- [smb-player-pc](https://github.com/oolah10293/smb-player-pc) — Windows player/controller

## Build

Requirements used by v0.3.8:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk / targetSdk 36
- minSdk 26
- Java 17
- Media3 1.11.0
- jcifs-ng 2.1.10

The repository intentionally does not include a redistributed Gradle wrapper JAR. On Windows, `SETUP_GRADLE_WRAPPER.bat` downloads the official Gradle 9.6 wrapper JAR and verifies its SHA-256 checksum.

Typical setup:

1. Clone the repository to a local folder.
2. Run `SETUP_GRADLE_WRAPPER.bat` once if `gradle/wrapper/gradle-wrapper.jar` is absent.
3. Open the project in Android Studio.
4. Let Gradle sync.
5. Connect an Android device with USB debugging enabled.
6. Run the `app` configuration.

See [BUILD_AND_INSTALL.txt](BUILD_AND_INSTALL.txt) for the short version and [docs/TESTING.md](docs/TESTING.md) for regression checks.

## Project history

The project evolved through saved source checkpoints from v0.1.0 onward. Detailed rationale is preserved in [CHANGELOG.md](CHANGELOG.md).

Important historical fixes include moving playback into a foreground `MediaLibraryService`, adding the Country Buffer, adding SMB read-ahead, fixing metadata-title precedence, eliminating a large-queue sort ANR, keeping Media3 out of minimal-control mode, adding instant filename/folder search, enabling explicit media audio-focus handling for vehicle playback, and preserving the validated v0.3.7 phone/vehicle controls.

For the product-level reasons behind the app, see [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md). For the line between proven behavior and changes still requiring phone testing, see [docs/VALIDATION_STATE.md](docs/VALIDATION_STATE.md).

## Planned / possible future work

- Dedicated field validation of v0.3.8 prolonged-outage recovery hardening.
- Small Browser UI pass: align both button rows with the search-bar outer edges, show only the current folder name in the path display, swap SMB/Parent Folder, and swap PLAY LIST/Sort.
- House-audio-server control and synchronized phone output; approved behavior and implementation checklist in [docs/CENTRAL_PLAYBACK.md](docs/CENTRAL_PLAYBACK.md).
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

No open-source license has been selected yet. Until one is added, normal copyright rules apply.
