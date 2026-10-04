# Changelog

This history is reconstructed from the actual saved source checkpoints. Release notes are condensed, but behavior and rationale are preserved. Public copies remove device/location-specific comments; functional code is otherwise retained.

## 0.5.1 — SMB connection and background Bluetooth corrections

- Send paced, explicit Tailscale connection requests from browser startup/recovery and service Play/Bluetooth/recovery paths. Remove the unconditional two-second duplicate; SMB reads remain the success check.
- Receive system Bluetooth audio-connection events with Nearby devices permission, restore only an eligible retained SMB session, and wait for an actual audio route.
- Start the foreground playback service before resume, retain blocked resume intent, retry briefly, and preserve Stop/Quit/disconnect cancellation.
- Add a bounded connection log by holding Browser/Now Playing status text; expose foreground/focus blocks rather than swallowing them.
- Device verification remains pending. See [release record](docs/RELEASE_0.5.1.md) and [targeted tests](docs/TESTING.md).

## 0.5.0 — standalone SMB Music

- Separate SMB Music completely from the planned House Music app. Remove HOUSE probing, player switching, session transfers, mapping settings, custom controls and the native Snapcast receiver/build.
- Restore the v0.3.8 standalone recovery/playback core while retaining the later Browser polish and standalone saved-session/Bluetooth behavior.
- Preserve Country Buffer, 64 KiB SMB read-ahead, same-track recovery, Repeat All, search/sort, metadata, explicit Play fade and external media controls.
- Keep explicit Stop/Quit authoritative and clean up the service immediately on Quit. Retained Bluetooth behavior still needs phone acceptance.
- Keep API-26/NewApi compatibility lint and add an APK isolation check. No server or ESP32 update is required.
- Preserve the combined source on `house-music-pre-split` for the future House Music app. Exact release verification: [RELEASE_0.5.0.md](docs/RELEASE_0.5.0.md).

## 0.4.3 — live home/away session handoff

- Keep one Media3 session while switching live between HOUSE and standalone SMB.
- Transfer a playing phone queue to idle HOUSE using the server v0.9.0 reservation/receipt protocol. Passive nodes arriving during the reservation join the transferred session; an already-active HOUSE remains authoritative.
- Continue the current estimated heard track through SMB after physical home departure. Explicit HOUSE music-root mapping prevents guessing paths; the entire HOUSE queue is not copied away.
- Require Bluetooth audio for HOUSE phone output on every path. Transport commands preserve mute; existing Bluetooth is recognized on attach. Add retained standalone sessions with Bluetooth connect/resume and disconnect/pause; Stop/Quit wins.
- Guard route APIs by Android version and add an API-26 compatibility lint gate. The Galaxy S8 launch result still requires device testing.
- Build evidence and limitations: [docs/RELEASE_0.4.3.md](docs/RELEASE_0.4.3.md). Current physical acceptance: [docs/HOUSE_VALIDATION.md](docs/HOUSE_VALIDATION.md).

## 0.4.2 — HOUSE recovery/output-intent correction build

- Added service-owned HOUSE Quit teardown/cleanup protections so local receiver/session state can be cleared without sending MPD Stop/Clear.
- Added event-triggered HOUSE identity/control reacquisition and browser retry reset around physical-network changes.
- Added Bluetooth media-output intent handling for HOUSE: media-capable output addition unmutes locally; final removal/noisy-route loss mutes locally; route events do not directly issue MPD transport commands.
- Added separate phone/wired and Bluetooth synchronization adjustments through the existing output control, with reported Snapcast buffer/latency diagnostics and bounded correction.
- Retained server v0.8.2 as the control/session dependency; the optional larger shared Snapcast-buffer trial is separate and not part of the Android APK.
- Build/artifact/signing details are frozen in [docs/RELEASE_0.4.2.md](docs/RELEASE_0.4.2.md). Live physical acceptance is maintained only in [docs/HOUSE_VALIDATION.md](docs/HOUSE_VALIDATION.md).

## 0.4.1 — first HOUSE phone-test corrections (physical acceptance pending)

- Qualify home using non-VPN Wi-Fi/Ethernet and a directly connected route to the configured Pi address, then check MPD/house-service identity. Use normal Android routing for MPD/HTTP/Snapcast so Tailscale can remain on.
- Monitor physical network/route loss, stop local output and suspend HOUSE requests/heartbeats without accepting VPN-only reachability. Reject stale work across detected network changes. Automatic home/away song handoff remains later work.
- Decide song/PLAY LIST auto-unmute from fresh pre-command MPD and audible-output state. Preserve a muted phone when another output was already playing; unmute from paused/stopped or otherwise inaudible playback. Ignore the phone's own stale audible report. Unknown presence preserves mute, failed reads abort the write, and failed commands/newer manual mute cannot trigger auto-unmute.
- Place the HOUSE output speaker icon inside the lower Media3 controller strip with the existing time, Shuffle and Repeat controls; hide it in standalone mode.
- Add direct-route and mute-policy unit coverage. Retain heartbeat recovery, both bundled Snapclient ABIs, and the existing standalone engine.
- Server v0.8.2 is already installed; no server or ESP32 firmware update is required. Build evidence/artifacts: [release record](docs/RELEASE_0.4.1.md). Next is the Tailscale-on phone/S3 checkpoint.

## 0.4.0 — first HOUSE integration (phone acceptance pending)

Released for device testing on 2026-09-29. Final Android build: `9c89b24`; server dependency: v0.8.2 (`9c98973`). Android CI (3 state tests plus APK/native packaging) and server CI (90 tests) passed. The APK was delivered; installation and phone/S3 acceptance remain pending. [Exact builds, downloads and checksums](docs/RELEASE_0.4.0.md).

- Retained Browser/Now Playing and the standalone SMB/Media3 implementation; added a service-owned HOUSE adapter so on-screen, notification, and media controls target the Pi's MPD state.
- Added optional local house address, non-VPN LAN MPD greeting probe before Browser startup, bound HTTP/audio connections, server readiness handling, stable controller/renderer identity, background heartbeats, reconnect/re-attach, and phone-only HOUSE Quit.
- Fixed recovery after a transient heartbeat failure: a successful renewal restores HOUSE controls while the lease is still valid, without Quit/reopen or waiting for expiry.
- Bundled upstream Snapclient 0.31.0 built from checksum-pinned source for arm64-v8a and armeabi-v7a, using FLAC/PCM and OpenSL ES. Native timing/clock correction stays upstream; no independent SMB audio plays in HOUSE.
- HOUSE starts muted, with Mute/Unmute Output in the lower controls. Song/PLAY LIST and Play from pause/stop unmute; browse/sort/Next/Previous during playback do not. Audio-focus/noisy-route interruptions stop only local output.
- Added MPD browse/queue/metadata/state, deliberate transport/shuffle/repeat, saved MP3s/Rap selector, and guarded active-queue Sort (server v0.8.2). No stale transport writes are replayed after reconnect.
- Aligned Browser button edges with Search, showed only the current folder, swapped SMB/Parent Folder, and swapped PLAY LIST/Sort. Shared sort, search X, portrait behavior, and scroll memory remain.
- Live home/away handoff follows separately. Hardware sync/background/standalone acceptance is required; see docs/HOUSE_VALIDATION.md.

## 0.3.8

- Based the revision on the separately tested v0.3.7 source rather than rebuilding directly from the older v0.3.6 repository tree.
- Added one shared `SortModeStore` for Browser and Now Playing. Changing either sort control is reflected on the other page; ordinary page navigation does not itself reorder the active queue.
- Made Repeat All a fixed policy for every nonempty standalone queue, including search-filtered and one-track queues. External attempts to select Repeat Off or Repeat One are corrected back to Repeat All; explicit Pause, Stop, and Quit remain authoritative.
- Changed new-queue construction so a tapped start track becomes queue item zero and the remaining sorted order wraps from it. For example, selecting `D` in `A B C D E F` produces `D E F A B C`.
- Changed deliberate active-queue sorting so the current track likewise becomes item zero while preserving its playback position, play/pause state, and Shuffle setting.
- Confirmed that a newly requested folder or filtered playlist replaces the old queue outright; an old current track excluded from the new list is not retained.
- Retuned the white Browser search field from v0.3.7's 48dp minimum to a 40dp minimum.
- Added an X inside the right side of Search. It is a separate non-focusable control: tapping it clears the query without opening the keyboard, while tapping the typing area still opens the keyboard normally.
- Retained the v0.3.7 no-autofocus, portrait-only, explicit Play/Resume fade-in, Garmin/Bluetooth command authorization, and Tailscale connection-request behavior.
- Retained the v0.3.6 Android Auto/audio-focus fix, Country Buffer, 64 KiB SMB read-ahead, metadata fallback, stock Media3 controls, and large-queue replacement strategy.
- Hardened the existing outage-recovery state machine rather than replacing it. Now Playing reports waiting, probing, and buffer-rebuild phases; network changes bring a real SMB probe forward; individual probe timeouts schedule another attempt; a no-progress watchdog returns a genuinely stuck refill to the retry loop; and a second outage during refill does not require another Play press.
- Added a GitHub Actions debug-build check.

## 0.3.7

- Added `android:minHeight="48dp"` to the white Browser search field. Phone testing later found this too tall, leading to the 40dp v0.3.8 target.
- Prevented Search from auto-focusing or opening the keyboard when Browse appears.
- Locked Browser and Now Playing to portrait orientation.
- Added an approximately 1.5-second ExoPlayer-only fade for explicit Play/Resume without changing Android's system media volume or fading ordinary automatic track transitions.
- Authorized Garmin Connect as a Media3 controller so watch/phone Bluetooth Play/Pause/Previous/Next commands could reach the player.
- Added Tailscale `CONNECT_VPN` requests at startup and after repeated SMB browse failures while retaining SMB access as the real reachability test.
- These changes were confirmed working in real use before v0.3.8 work began.

## 0.3.6

- Swapped the Now Playing **sort** and **Browse** button labels/functions while keeping the existing physical button positions and sizes.
- Renamed Browser **PLAY FOLDER** to **PLAY LIST**.
- Changed search from one literal phrase to whitespace-separated AND terms. `blink 182` requires both terms but does not care whether the filename uses spaces, hyphens, underscores, em dashes, dollar signs, or no separator.
- Made the search field white with dark text.
- Added explicit Media3 music `AudioAttributes` with ExoPlayer audio-focus handling enabled to address vehicle/Android Auto routing that previously could require another media app to establish audio first.
- Preserved v0.3.5 transport, buffering, recovery, sorting, metadata, and Media3 controls.

## 0.3.5

- Added instant current-folder filename/subfolder filtering.
- Search remained non-recursive and name-based; no metadata index or database was added.
- While filtering, the play button queues only matching playable tracks; tapping a result starts within the filtered queue.
- Fixed mixed folder/track date sorting: name sorts remain folders-first, but date sorts compare folders and tracks together so a newly modified track can appear above older folders.
- Adjusted Now Playing vertical spacing only; kept the 200dp stock Media3 control region intact.

## 0.3.4

- Restored the full stock Media3 control set by increasing the dedicated controller region from 132dp to 200dp.
- Root cause: Media3's `PlayerControlViewLayoutManager` entered minimal mode in the smaller region and intentionally hid Previous/Next and the normal bottom bar.
- No playback/SMB logic changed.

## 0.3.3

- Replaced repeated `moveMediaItem()` queue sorting with one in-memory sort plus a single `setMediaItems(...)` replacement, preventing ANR/freezes on very large folders.
- Preserved the current media item, playback position, and play/pause state through queue replacement.
- Sort button now shows only the active mode: `A–Z`, `Z–A`, `New–Old`, or `Old–New`.
- Returned controls to `PlayerView`'s built-in Media3 controller mechanism instead of a standalone `PlayerControlView`.

## 0.3.2

- Added 64 KiB `BufferedInputStream` read-ahead around jcifs-ng `SmbFileInputStream`.
- Enabled SMB `tcpNoDelay` while leaving existing connection/socket/response timeouts unchanged.
- This dramatically improved high-latency cellular/tunneled SMB throughput without changing the large ExoPlayer playback buffer.
- Fixed metadata-title precedence by no longer pre-populating Media3 title with the filename. Embedded title can now win; filename is a UI fallback only.
- Restored stock Media3 controls and native buffered-ahead seek indication.

## 0.3.1

- Rebuilt Now Playing layout: header/status, Browse/sort/Quit row, metadata, artwork, transport controls, seek bar.
- Added explicit system-bar inset handling on Browser and Now Playing.
- Used filename-without-extension only as a title fallback and hid absent secondary metadata instead of showing filler text.
- Preserved the v0.3 transport/recovery stack.

## 0.3.0

- Added the aggressive **Country Buffer™**: 120 s minimum, 600 s maximum, 32 MiB target, 3 s normal start threshold, ~20 s recovery threshold.
- Recovery no longer resumes immediately after SMB comes back; it rebuilds a useful buffer first to avoid rapid play/stall cycling.
- Added Browser SMB auto-retry on the same 1/2/5/10/15-second schedule.
- Kept playback controls visible.
- Added explicit queue sort modes and active-queue sorting from Now Playing.
- Added explicit Quit that stops playback, clears the queue, stops the service, and closes the UI.

## 0.2.0

- Moved ExoPlayer out of the Activity into a foreground `MediaLibraryService`.
- Added `WAKE_MODE_NETWORK` to fix screen-off playback stopping on an older test device.
- Split Browser and Now Playing into separate screens while preserving browser state.
- Added MP3, FLAC, M4A/MP4 audio, AAC, OGG, Opus, and WAV support.
- Kept exact-track/same-position SMB outage recovery from v0.1.

## 0.1.0

- First bench build.
- Direct SMB browsing and MP3 playback.
- Name/date sorting.
- Folder queue playback, previous/next, seek, Shuffle, embedded artwork when exposed by Media3.
- Android Keystore AES/GCM storage for the SMB password.
- Initial outage recovery: save queue index/position, pause, retry 1/2/5/10/15 seconds, reprepare the same file, seek back, and resume instead of skipping.
- Background `MediaLibraryService` support intentionally deferred until transport behavior was proven.
