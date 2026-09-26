# Roadmap / ideas

Unless explicitly linked to approved requirements, these are ideas, not commitments. Proven playback behavior takes priority over feature count.

## Next revision

- **Persistent prolonged-outage recovery — approved for implementation/testing:** use both network-change events and timed retries, verified by actual SMB access and refill progress rather than signal bars. Actively retry for at least ten minutes; after that continue more slowly on battery (initial target: about once per minute), while charging continues active retries without a fixed ten-minute cutoff. An individual failed/timed-out attempt must not end the recovery session. Add a no-progress watchdog that retries genuinely stuck I/O without discarding a slow but progressing refill, preserve same-track/position and user intent, keep the useful approximately 20-second recovery buffer, and make screen-off lifecycle/wake handling plus status/diagnostics reliable. Explicit Pause/Stop/Quit and audio-focus/output-disconnection behavior remain respected. Country Buffer and proven transport tuning stay intact. Full approved behavior and field tests: [OUTAGE_RECOVERY_PLAN.md](OUTAGE_RECOVERY_PLAN.md). These changes are not yet implemented or validated.
- **Synchronize Browse and Now Playing sort:** both sort controls must read and change one shared sort mode, not maintain independent modes or merely matching labels. Changing the sort on either page must be reflected on the other page. Navigating between pages must not itself rebuild or reorder the playback queue. This request concerns sort order, not a new Shuffle policy.
- **Always-on Repeat All for the existing standalone player:** every non-empty active playlist must loop instead of stopping at its end. Repeat the actual queued tracks, including a search-filtered playlist, rather than reloading the entire source folder. Keep Repeat All enabled when the player is created and through queue loading/replacement, explicit sorting, page navigation, and playback recovery. Shuffle remains a separate user-controlled setting; do not introduce a new reshuffle policy as part of this change. A one-track queue repeats that track; an empty queue stays idle. Explicit Pause, Stop, and Quit must still work normally—Repeat All must not restart intentionally stopped playback. Do not expose or accept an accidental switch to Repeat Off/Repeat One through the local UI or external media controls. This is a recorded next-revision requirement, not an implemented change, and does not override the future HOUSE server's session-lifecycle rules.
- **Current track first when rebuilding a queue:** on an explicit re-sort of the active playlist, the currently playing song must become the first entry, not remain near the end because of its ordinary sorted position. Keep its playback position and playing/paused state when retaining that song. When starting a new playlist, its starting song must likewise be the first queue entry rather than starting at an interior/end index. Do not silently drop or duplicate tracks. Before implementation, confirm the order of the remaining tracks (rotate the sorted sequence from the current song versus place the current song first followed by the sorted remainder), and the behavior when a newly selected folder/filter excludes the old current song. Those edge decisions are not yet confirmed. Source inspection of the repository found separate page-local sort states and active-queue sorting that retains the current song's new interior index; it did not establish that page navigation itself triggers sorting. These are recorded requirements, not an implemented fix.
- **Retune Browser search-field height between v0.3.6 and v0.3.7:** the white v0.3.6 search field was too short; the v0.3.7 enlargement is now reported too tall. Direct comparison of the supplied source ZIPs confirms v0.3.5 used `wrap_content` with the default background, v0.3.6 used `wrap_content` plus a plain white background and `10dp` horizontal padding with no explicit minimum height, and v0.3.7 added `android:minHeight="48dp"`. The next implementation target is `android:minHeight="40dp"` while retaining `wrap_content`, white background, dark text, existing text size, horizontal padding, margins, search behavior, and no-autofocus behavior. The old rendered height was theme/font dependent; 40dp is a proposed intermediate size for phone testing, not a measured exact midpoint. Preserve text/font scaling rather than forcing a clipping fixed height. This supersedes the earlier 48dp target and is not yet a delivered app change.
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
