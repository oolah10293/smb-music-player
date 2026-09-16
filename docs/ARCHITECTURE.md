# Architecture

## High-level flow

`MainActivity` → browser / filter / queue construction → `MediaController` → `PlaybackService` → ExoPlayer → `SmbDataSource` → jcifs-ng → SMB server

`NowPlayingActivity` is another controller-facing UI for the same service/player.

## Main components

### `MainActivity`

- Restores/saves SMB connection details.
- Tests connection and browses the current SMB directory.
- Retries failed folder loads.
- Maintains current sort mode.
- Filters loaded `RemoteEntry` objects as the user types.
- Builds a Media3 queue from the sorted/filtered playable entries.
- Launches Now Playing without owning the player lifecycle.

### `NowPlayingActivity`

- Connects to the same `MediaLibraryService` through `MediaController`.
- Displays title, album artist/artist, album, artwork, and playback status.
- Hosts stock Media3 controls.
- Re-sorts the active queue with one queue replacement while preserving the current item and playback position.
- Implements clean Quit behavior.

### `PlaybackService`

- Owns the single ExoPlayer instance and `MediaLibrarySession`.
- Applies the Country Buffer load-control policy.
- Uses media/music audio attributes with audio-focus handling.
- Holds network wake mode while actively playing/buffering.
- Detects playback failures and owns same-track/same-position SMB recovery.

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

## Dependencies

- AndroidX AppCompat / RecyclerView
- AndroidX Media3 ExoPlayer / UI / Session
- jcifs-ng
- slf4j-nop
