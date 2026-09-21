# Roadmap / ideas

These are ideas, not commitments. Proven playback behavior takes priority over feature count.

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

### Central / synchronized house playback

Add an optional central-control mode while preserving the current local player. The Android Browser/search/sort workflow should feed either the existing local Media3 backend or a future central playback server. In central mode the server owns queue/playback state and synchronized delivery to ESP32 audio nodes; the phone is only a controller and may disconnect after starting playback.

Design details and staged integration plan: [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md)


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
