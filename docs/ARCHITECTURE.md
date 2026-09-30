# Architecture

## Playback backend boundary

`PlaybackService` owns the selected playback authority behind the same Activities, notification, and Media3 controller surface:

- **STANDALONE:** ExoPlayer / `SmbDataSource` / jcifs-ng owns local playback.
- **HOUSE:** `HouseRuntime` / `HousePlayer` adapts the Pi-owned session into the app while the bundled Snapcast receiver supplies synchronized phone audio when enabled.

HOUSE qualification uses the physical non-VPN LAN as presence evidence, while ordinary HOUSE control/audio traffic follows normal Android routing. Product rules and transition behavior are intentionally not duplicated here; see [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md). Current physical acceptance belongs in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md).

## High-level flow

```text
MainActivity / NowPlayingActivity
        -> MediaController
        -> PlaybackService
             -> STANDALONE: ExoPlayer -> SmbDataSource -> jcifs-ng -> SMB
             -> HOUSE: HousePlayer / HouseRuntime -> house-audio-server / MPD
                                      -> bundled Snapcast receiver -> local output
```

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
- Implements clean standalone Quit behavior.

### `PlaybackService`

- Owns the single ExoPlayer instance and `MediaLibrarySession`.
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

This file describes code structure. HOUSE/STANDALONE product behavior, output intent, home/away transitions, and server-session semantics are defined in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md) and the server's [SESSION_BEHAVIOR.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/SESSION_BEHAVIOR.md). Do not copy live validation results into this architecture document.

