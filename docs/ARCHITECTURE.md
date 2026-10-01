# Architecture

## Standalone playback service

SMB Music v0.5.0 owns one ExoPlayer and one MediaLibrarySession. Both Activities, notification and external media controls target that same standalone session. It has no HOUSE authority selection or player switching.

SMB bytes reach ExoPlayer through `SmbDataSource` and jcifs-ng. The recovery and buffering core comes from the verified pre-HOUSE v0.3.8 source; later standalone UI and retained-session/Bluetooth behavior are preserved. House Music remains a separate pending app; its source is preserved on `house-music-pre-split`.

## Main components

### `MainActivity`

- Restores/saves SMB connection details.
- Requests Tailscale connection at startup and after repeated SMB browse failures, while treating actual SMB access as the reachability authority.
- Tests connection and browses the current SMB directory.
- Retries failed folder loads.
- Reads and writes the shared `SortModeStore`.
- Filters loaded `RemoteEntry` objects as the user types.
- Hosts the 40dp search field and a separate non-focusable clear-X control.
- Builds a Media3 queue from the sorted/filtered playable entries.
- Rotates a tapped starting track to queue item zero.
- Replaces the previous queue outright when a new folder/filter is played.
- Enables Repeat All before queue playback.
- Launches Now Playing without owning the player lifecycle.

### `SortMode` / `SortModeStore`

- Defines the four explicit modes: `A–Z`, `Z–A`, `New–Old`, and `Old–New`.
- Stores one shared mode in app preferences.
- Lets Browser and Now Playing reflect each other's sort choice without page navigation itself rebuilding the queue.

### `NowPlayingActivity`

- Connects to the same `MediaLibraryService` through `MediaController`.
- Displays title, album artist/artist, album, artwork, and playback/recovery status.
- Hosts stock Media3 controls.
- Re-sorts the active queue with one queue replacement while preserving the current item, exact position, play/pause state, and Shuffle setting.
- Rotates the current item to queue item zero after an explicit active-queue sort.
- Reads recovery phase/progress from Media3 session extras.
- Requests immediate service-owned Quit cleanup for this standalone player.

### `PlaybackService`

- Owns one ExoPlayer and one `MediaLibrarySession`.
- Retains standalone queue/index/position/Shuffle and explicit Stop intent; handles Bluetooth connect/disconnect around that retained session.
- Applies the Country Buffer load-control policy.
- Uses media/music audio attributes with audio-focus handling.
- Holds network wake mode while actively playing/buffering.
- Keeps Repeat All as a fixed standalone policy.
- Implements the short explicit Play/Resume fade.
- Grants Garmin Connect the standard player/session commands required for media control.
- Detects playback failures and owns same-track/same-position SMB recovery.
- Publishes recovery phase, retry countdown, and buffered-ahead progress through session extras.
- Uses Android network changes to advance a real SMB probe, never as proof of SMB success.
- Applies per-probe and no-buffer-progress watchdogs while rejecting stale asynchronous results.

### `SmbClient`

- Creates/caches jcifs-ng contexts from the currently saved credentials.
- Lists directories.
- Tests credentials/paths.
- Probes exact files during recovery.
- Configures SMB client timeouts and transport options.

### `SmbDataSource`

- Adapts SMB files to Media3's `DataSource` interface.
- Seeks to requested byte positions.
- Adds 64 KiB local read-ahead around `SmbFileInputStream`.
- Reports stream length and current URI to Media3.

### `CredentialStore`

- Stores address, username, last folder, and encrypted password in app preferences.
- Encrypts/decrypts the password with an AES key held by Android Keystore.
- No credentials are compiled into the application.

### Supporting classes

- `AudioFormats`: supported extensions, extension labels, filename title fallback.
- `RemoteEntry`: browser model for directory/audio entries.
- `SmbUrl`: SMB URL normalization, display, and parent traversal.
- `FileAdapter`: RecyclerView adapter for browser entries.
- `StandaloneSessionStore`: app-private retained queue/state; no handoff journal or SMB password duplication.
- `StandalonePlaybackIntent`: standalone Stop/Quit and Bluetooth intent, independent of network routing.

## Queue semantics

Browser sort order defines the new playlist order. When the user taps a start track at index `i`, the sorted queue is rotated rather than retaining an interior start index:

`sorted[i..end] + sorted[0..<i]`

For example, `A B C D E F` started at `D` becomes `D E F A B C`.

The same rotation is applied after an explicit active-queue sort using the current track. A newly selected folder or search result is a new playlist request and replaces the old queue outright.

## Dependencies

- AndroidX AppCompat / RecyclerView
- AndroidX Media3 ExoPlayer / UI / Session
- jcifs-ng
- slf4j-nop

## Behavior ownership

This file describes the standalone implementation. The independent-app product boundary is defined in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md). Physical checks belong in [TESTING.md](TESTING.md); historical combined-app results remain in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md).
