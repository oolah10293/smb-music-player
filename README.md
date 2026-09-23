# SMB Music Player

A native Android music player that streams audio directly from SMB shares using Media3/ExoPlayer and jcifs-ng. The project is intentionally optimized for unreliable networks: it buffers aggressively when bandwidth is available, preserves the current track and position through SMB outages, and retries instead of treating a network failure as a bad song.

Current version: **0.3.6**.

## What it does

- Browse SMB folders directly from Android.
- Play MP3, FLAC, M4A/MP4 audio, AAC, OGG, Opus, and WAV.
- Sort by name or modified date in either direction.
- Search the current folder as you type; both audio filenames and subfolder names are filtered.
- Multi-term search uses AND semantics: `blink 182` matches any name containing both terms, regardless of punctuation or separators between them.
- `PLAY LIST` queues the complete current folder when search is blank, or only the filtered tracks while search is active.
- Separate Browser and Now Playing screens.
- Stock Media3 transport controls, buffered seek bar, shuffle, lock-screen controls, Bluetooth controls, and foreground playback.
- Embedded title/artist/album metadata with filename fallback.
- Encrypted local SMB credential storage using Android Keystore AES/GCM.
- Resilient SMB/Tailscale playback designed for variable Wi-Fi/cellular links.

## The Country Buffer™

The unusually large playback buffer is deliberate, not an accidental tuning value.

Current settings:

- minimum playback buffer: **120 seconds**
- maximum playback buffer: **600 seconds**
- target buffer size: **32 MiB**
- normal initial start threshold: **3 seconds**
- outage-recovery resume threshold: **about 20 seconds**

When the link is healthy, the player intentionally banks a large amount of compressed audio so short coverage gaps and tower handoffs do not interrupt playback. See [docs/DESIGN_NOTES.md](docs/DESIGN_NOTES.md) before changing these values.

## SMB transport behavior

jcifs-ng's `SmbFileInputStream` is unbuffered. Media extractors can issue many small reads, which is inexpensive on a LAN but expensive over a high-latency tunnel or cellular path. The player wraps the SMB stream in a **64 KiB `BufferedInputStream`** and enables `tcpNoDelay` so small extractor reads are normally served from RAM instead of each becoming a network round trip.

This is separate from the Country Buffer: one reduces SMB transaction overhead; the other stores playable media ahead of the current position.

## Outage recovery

A network failure does **not** advance to the next song. The player records the current media item and playback position, pauses, and retries the exact file on this schedule:

`1 s → 2 s → 5 s → 10 s → every 15 s`

Once the file is reachable again, playback is prepared at the saved position and allowed to rebuild a useful buffer before automatic resume.

## Future whole-house audio integration

This app is planned to become one controller/client for the synchronized house-audio system while preserving its current standalone behavior.

The mode should be selected automatically:

- **HOUSE** — the app discovers and verifies the house-audio service directly on the local home LAN. The existing folder-first UI controls the **one shared house playback session** instead of creating a separate phone playback session.
- **STANDALONE** — the house service is not present on the local LAN, so the app behaves exactly as it does today: SMB/Tailscale -> ExoPlayer -> phone.

Do not make GPS, SSID name, or mere server reachability the authority. Preferred detection is local mDNS/DNS-SD discovery plus a short LAN handshake. **Tailscale/VPN reachability alone must not trigger HOUSE mode**, because the phone may be hundreds of miles away while still able to reach home.

The folder-first model remains unchanged in either mode: **folders are playlists**. House playback must also remain alive if the phone closes, reboots, or leaves the network.

Related projects:

- [house-audio-server](https://github.com/oolah10293/house-audio-server) — central queue/session authority and synchronized stream
- [house-audio-esp32](https://github.com/oolah10293/house-audio-esp32) — ESP32-S3 synchronized renderer nodes
- [smb-player-pc](https://github.com/oolah10293/smb-player-pc) — Windows player/controller

## Build

Requirements used by v0.3.6:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk / targetSdk 37
- minSdk 26
- Java 17
- Media3 1.11.0
- jcifs-ng 2.1.10

The repository intentionally does not include a redistributed Gradle wrapper JAR. On Windows, `SETUP_GRADLE_WRAPPER.bat` downloads the official Gradle 9.6 wrapper JAR and verifies its SHA-256 checksum.

Typical setup:

1. Clone the repository to a local folder.
2. Run `SETUP_GRADLE_WRAPPER.bat` once if `gradle/wrapper/gradle-wrapper.jar` is absent.
3. Open the project in Android Studio.
4. Let Gradle sync.
5. Connect an Android device with USB debugging enabled.
6. Run the `app` configuration.

See [BUILD_AND_INSTALL.txt](BUILD_AND_INSTALL.txt) for the short version and [docs/TESTING.md](docs/TESTING.md) for regression checks.

## Project history

The project evolved through nine source checkpoints from v0.1.0 through v0.3.6. Detailed rationale is preserved in [CHANGELOG.md](CHANGELOG.md).

Important historical fixes include moving playback into a foreground `MediaLibraryService`, adding the Country Buffer, adding SMB read-ahead, fixing metadata-title precedence, eliminating a large-queue sort ANR, keeping Media3 out of minimal-control mode, adding instant filename/folder search, and enabling explicit media audio-focus handling for vehicle playback.

For the product-level reasons behind the app, see [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md). For the line between already-proven behavior and v0.3.6 changes that still need targeted testing, see [docs/VALIDATION_STATE.md](docs/VALIDATION_STATE.md).

## Planned / possible future work

- `.m3u` / `.m3u8` playlist-file support.
- Smart Shuffle / listening-history database.
- Metadata-assisted filename cleanup as a separate library-maintenance tool.
- Recursive or metadata-indexed search if ever needed.
- Album-art fallback/cache improvements.
- A richer Android Auto browse tree.

The current philosophy is to keep the player simple and preserve proven playback behavior rather than add features that destabilize the transport stack.

## Privacy and security

No SMB credentials, private network addresses, personal paths, or user-specific data are committed to this repository. Saved SMB passwords are encrypted locally through Android Keystore. See [docs/PRIVACY_AND_SECURITY.md](docs/PRIVACY_AND_SECURITY.md).

## License

No open-source license has been selected yet. Until one is added, normal copyright rules apply.
