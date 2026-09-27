# Roadmap / ideas

Unless explicitly linked to approved requirements, these are ideas, not commitments. Proven playback behavior takes priority over feature count.

## v0.3.8 field validation

The v0.3.8 implementation is complete in source and must now be exercised on the real phones, SMB server, Tailscale path, vehicle, and Bluetooth devices before its new behavior is classified as proven.

Priority checks:

- prolonged SMB loss with the screen both on and off;
- visible transition through `Waiting for SMB`, `Checking SMB`, and `Rebuilding buffer`;
- automatic recovery after a failed/timed-out individual probe;
- recovery after connectivity returns during a scheduled wait;
- another outage during the buffer-rebuild phase;
- a slow but progressing refill that must not be killed by the no-progress watchdog;
- explicit Pause, Stop, Quit, track change, and queue replacement during recovery;
- shared sort state on Browser and Now Playing;
- current/selected-track-first rotation (`D E F A B C`);
- new folder/filter replacing the old queue outright;
- Repeat All for normal, filtered, and one-track queues;
- 40dp search-field size and clear-X keyboard behavior;
- regressions in Country Buffer, metadata, lock-screen controls, Garmin/Bluetooth, Android Auto, fade-in, and Tailscale startup.

See [TESTING.md](TESTING.md) and [OUTAGE_RECOVERY_PLAN.md](OUTAGE_RECOVERY_PLAN.md).

## Near-term candidates

- `.m3u` / `.m3u8` playlist-file support.
- Optional library filename-cleanup script that reads metadata, performs a dry run, sanitizes filenames, prevents collisions, and logs reversible renames. This should remain separate from the player so the player itself stays read-only toward the music library.

## Later candidates

### House-audio-server integration — approved behavior, implementation pending

The Android Browser/search/sort and Now Playing UI must control the Pi's MPD session in automatic **HOUSE** mode while preserving the existing SMB/ExoPlayer path in **STANDALONE** mode. Direct verified home-LAN detection selects HOUSE; VPN-only reachability does not. A temporary home outage is reconnection, not automatic independent playback.

Required additions include live shared-queue/state display and control, a separate synchronized phone receiver, HOUSE-only **Mute output / Unmute output**, independent controller/renderer presence, and same-song SMB/Tailscale continuation when an unmuted playing phone leaves home. Muted/paused/stopped phones remain silent. A phone attaching to fresh idle does not auto-start a track; only passive nodes do that. HOUSE Quit detaches the phone without sending global Stop/Clear. The Pi owns final-node finish/stop, muted-controller pause/retention, and persistent default MP3s shuffle progress.

The browser controller and Windows player share the same Pi control contract. The exact API, Android receiver integration, and remaining edge decisions need definition/testing; these capabilities are not implemented by v0.3.8.

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
