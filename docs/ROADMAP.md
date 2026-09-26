# Roadmap / ideas

Unless explicitly linked to approved requirements, these are ideas, not commitments. Proven playback behavior takes priority over feature count.

## Next revision

- **Restore Browser search-field height:** keep the white background introduced in v0.3.6, but restore the previous visual height with an explicit minimum height (target: about 48dp) without disturbing the rest of the Browser layout.
- **Do not show the keyboard when Browse opens:** the search field must not auto-focus. The keyboard should appear only after the user taps the search field.
- **Lock the app to portrait orientation:** there is no useful landscape workflow for the current UI, and landscape can make the on-screen keyboard consume most of the display.
- **Soft Play/Resume fade-in:** when the user explicitly presses Play/Resume, ramp ExoPlayer's own volume from near-silent to full over a short interval (initial target: roughly 1-2 seconds). Do not change Android's system/media volume, and do not fade ordinary automatic track-to-track transitions.
- **Bluetooth media-control cleanup:** test and correct Bluetooth play/pause/next/previous behavior while preserving the proven Media3 session and Android Auto behavior. Do not guess at the failure mode; reproduce it first and make the smallest targeted change.
- **Tailscale auto-connect / recovery:** SMB Music depends on Tailscale for remote use, so startup should be able to request a Tailscale connection. Because Tailscale on Android has shown intermittent connected-but-broken networking behavior, do not implement this as a blind one-shot connect. Verify SMB reachability and use a sensible retry/reconnect path. Preserve the existing SMB retry logic.
- **Preserve the v0.3.6 Android Auto/audio-focus fix:** vehicle audio routing is confirmed working, and steering-wheel track skip is confirmed working. Do not revert this while investigating unrelated network issues.

## Near-term candidates

- `.m3u` / `.m3u8` playlist-file support.
- Optional library filename-cleanup script that reads metadata, performs a dry run, sanitizes filenames, prevents collisions, and logs reversible renames. This should remain separate from the player so the player itself stays read-only toward the music library.

## Later candidates

### House-audio-server integration — approved behavior, implementation pending

The Android Browser/search/sort and Now Playing UI must control the Pi's MPD session in automatic **HOUSE** mode while preserving the existing SMB/ExoPlayer path in **STANDALONE** mode. Direct verified home-LAN detection selects HOUSE; VPN-only reachability does not. A temporary home outage is reconnection, not automatic independent playback.

Required additions include live shared-queue/state display and control, a separate synchronized phone receiver, HOUSE-only **Mute output / Unmute output**, independent controller/renderer presence, and same-song SMB/Tailscale continuation when an unmuted playing phone leaves home. Muted/paused/stopped phones remain silent. A phone attaching to fresh idle does not auto-start a track; only passive nodes do that. HOUSE Quit detaches the phone without sending global Stop/Clear. The Pi owns final-node finish/stop, muted-controller pause/retention, and persistent default MP3s shuffle progress.

The browser controller and Windows player share the same Pi control contract. The exact API, Android receiver integration, and remaining edge decisions need definition/testing; these capabilities are not implemented by this documentation update.

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

Regular Shuffle should remain available as a separate mode.

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

A richer MediaLibrary browse tree could be added later. Current playback already uses a `MediaLibraryService`; v0.3.6 additionally enables explicit media audio focus, and vehicle routing plus steering-wheel track skip have been confirmed in real use.
