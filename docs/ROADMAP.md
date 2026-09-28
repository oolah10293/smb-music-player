# Roadmap / ideas

Unless explicitly linked to approved requirements, these are ideas, not commitments. Proven playback behavior takes priority over feature count.

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

## Next standalone UI polish

Preserve all confirmed v0.3.8 playback behavior and make only these Browser layout/content changes:

- align the left/right outer edges of **both button rows** with the left/right outer edges of the search bar;
- change the path display to show **only the current folder name**, not the full SMB path;
- swap the **SMB** and **Parent Folder** buttons, including their functions;
- swap the **PLAY LIST** and **Sort** buttons, including their functions.

Do not reopen the confirmed search-X behavior, search height, shared sort, current-track-first queue behavior, Repeat All, portrait lock, fade, vehicle/Bluetooth behavior, Tailscale behavior, metadata handling, Country Buffer, or SMB tuning while doing this UI pass.

## Near-term candidates

- `.m3u` / `.m3u8` playlist-file support.
- Optional library filename-cleanup script that reads metadata, performs a dry run, sanitizes filenames, prevents collisions, and logs reversible renames. This should remain separate from the player so the player itself stays read-only toward the music library.

## Later candidates

### House-audio-server integration — approved behavior, implementation pending

The Android Browser/search/sort and Now Playing UI must control the Pi's MPD session in automatic **HOUSE** mode while preserving the existing SMB/ExoPlayer path in **STANDALONE** mode. HOUSE presence is detected by binding a short probe to a non-VPN Wi-Fi/Ethernet network, connecting to the locally configured house LAN address on MPD port 6600, and requiring the normal `OK MPD ...` greeting. VPN-only reachability does not count. A temporary home outage is reconnection, not automatic independent playback. No mDNS/custom discovery handshake is planned unless testing shows it is actually needed.

Required additions include live shared-queue/state display and control, a separate synchronized phone receiver, HOUSE-only **Mute output / Unmute output**, independent controller/renderer presence, and same-song SMB/Tailscale continuation when an unmuted playing phone leaves home. Muted/paused/stopped phones remain silent. A phone attaching to fresh idle does not auto-start a track; only passive nodes do that. HOUSE Quit detaches the phone without sending global Stop/Clear. The Pi owns final-node finish/stop, muted-controller pause/retention, and persistent default MP3s shuffle progress.

The browser controller and Windows player share the same Pi control contract. The basic `house-audio-server` HTTP API is runtime-proven on the permanent Pi for browse, queue/state, queue replacement/start index, Play/Pause/Stop, Seek, Previous/Next, Shuffle/Random, and Repeat. Renderer presence is runtime-proven through abrupt ESP32 hard power-off, and the passive-radio policy now works end to end: fresh-idle power-on starts default music, active-session join/rejoin preserves the queue, and v0.5.1 resumes an existing paused session when a passive radio appears.

Two independent ESP32-S3 + PCM5102A nodes have also passed the real audible synchronization test through different analog systems. Android should target the bridge rather than use MPD's native port as its normal control API. The main remaining server prerequisites are durable default-`MP3s` shuffle progress and controller/output presence policy. Android Snapcast receiver integration and the remaining phone-specific edge decisions still need implementation/testing. These capabilities are not implemented by v0.3.8.

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
