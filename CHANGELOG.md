# Changelog

This history is reconstructed from the actual saved source checkpoints. Release notes have been condensed, but behavior and rationale are preserved. Public copies remove device/location-specific comments; functional code is otherwise retained.

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
- Folder queue playback, previous/next, seek, shuffle, embedded artwork when exposed by Media3.
- Android Keystore AES/GCM storage for the SMB password.
- Initial outage recovery: save queue index/position, pause, retry 1/2/5/10/15 seconds, reprepare the same file, seek back, and resume instead of skipping.
- Background `MediaLibraryService` support intentionally deferred until transport behavior was proven.
