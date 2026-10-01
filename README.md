# SMB Music Player

A native Android music player that streams audio directly from SMB shares using Media3/ExoPlayer and jcifs-ng. It is intentionally optimized for unreliable networks: it buffers aggressively when bandwidth is available, preserves the current track and position through SMB outages, and retries instead of treating a network failure as a bad song.

Current source version: **0.4.3**. See [docs/RELEASE_0.4.3.md](docs/RELEASE_0.4.3.md) for build/artifact verification and [docs/HOUSE_VALIDATION.md](docs/HOUSE_VALIDATION.md) for the current real-device pass/fail state. **v0.3.8 remains the confirmed standalone hardware baseline.**

**Next direction, confirmed 2026-10-01:** split SMB Music and HOUSE into completely independent Android apps. No automatic/manual handoff, shared playback state, or SMB-triggered HOUSE queue reset. The source/released APK is still the combined v0.4.3 implementation; the split is pending. See [the current contract](docs/CENTRAL_PLAYBACK.md) and [roadmap](docs/ROADMAP.md).

## What it does

- Browses SMB folders directly from Android.
- Plays MP3, FLAC, M4A/MP4 audio, AAC, OGG, Opus, and WAV.
- Treats folders as playlists; no tag database is required.
- Sorts by name or modified date in either direction.
- Uses one shared sort mode on Browser and Now Playing.
- Searches the current folder as the user types; audio filenames and subfolder names are filtered.
- Uses whitespace-separated AND search terms: `blink 182` matches `Blink-182`, `Blink_182`, `Blink$182`, and `Blink182`.
- `PLAY LIST` queues the complete current folder when Search is blank or only the filtered tracks while Search is active.
- Rotates a newly selected or currently playing song to queue item zero while preserving the sorted wraparound order.
- Keeps Repeat All enabled for every nonempty standalone queue.
- Provides separate Browser and Now Playing screens.
- Uses stock Media3 transport controls, buffered seek indication, Shuffle, lock-screen controls, Bluetooth/Garmin controls, and foreground playback.
- Displays embedded title/artist/album metadata with filename fallback.
- Stores the SMB password locally with Android Keystore AES/GCM encryption.
- Requests the Tailscale VPN connection at startup while still using real SMB access as the reachability test.

## Search field behavior

The Browser search field is intentionally a **40dp minimum-height** white field: the v0.3.6 version was too short and the v0.3.7 48dp field was too tall.

The X at the right side is a separate, non-focusable control:

- tapping the typing area focuses Search and opens the keyboard;
- tapping X clears the query without requesting focus or opening the keyboard;
- if the keyboard is already open, X clears the query without forcing it open or closed;
- clearing immediately restores the complete folder listing in the active sort order and does not alter the active playback queue.

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

The original recovery architecture was retained because real-use testing confirmed that it can recover successfully after a prolonged outage. The apparent delay comes from two separate phases:

1. verify that the exact SMB file is reachable again;
2. reopen it at the saved position and silently rebuild about 20 seconds of playable buffer before resuming.

v0.3.8 hardens and exposes that process rather than replacing it:

- the short retry progression remains `1 s → 2 s → 5 s → 10 s → every 15 s`;
- the overall recovery session does not end because one probe fails or times out;
- a network change brings the next **real SMB probe** forward but is not treated as proof that SMB works;
- a per-attempt watchdog prevents one stuck SMB probe from blocking every later attempt;
- a no-progress watchdog returns a genuinely stuck buffer rebuild to the retry loop while allowing slow but progressing transfers to continue;
- another outage during refill returns to waiting/retry automatically;
- Now Playing reports `Waiting for SMB`, `Checking SMB`, and `Rebuilding buffer` states;
- explicit Pause, Stop, Quit, queue replacement, audio-focus loss, and output disconnection remain authoritative.

The same media item, queue, position, sort, Shuffle setting, Repeat All policy, and user play intent are preserved through recovery where applicable. See [docs/OUTAGE_RECOVERY_PLAN.md](docs/OUTAGE_RECOVERY_PLAN.md) and [docs/TESTING.md](docs/TESTING.md).

## Confirmed standalone baseline

v0.3.8 has now been exercised on the real phone and is the current standalone baseline.

Confirmed:

- Browse does not autofocus Search or raise the keyboard.
- The 40dp search field is the desired height.
- The clear-search X behaves correctly and does not raise a hidden keyboard.
- Browser and Now Playing share the same sort state.
- Deliberate sorting keeps the current/selected song as queue item zero.
- Repeat All wraps correctly from the final queued song back to the first.
- Both screens remain locked to portrait.
- Explicit Play/Resume keeps the short ExoPlayer-only fade-in.
- Garmin/Bluetooth media commands remain working.
- Tailscale startup/recovery requests remain working in practice while SMB stays the real reachability test.
- The v0.3.6 Android Auto/audio-focus behavior remains working.
- No regressions were observed in metadata handling, Country Buffer, or SMB transport tuning.

The v0.3.8 prolonged-outage recovery hardening still needs its dedicated field test.

## Shipped combined HOUSE integration (v0.4.3; split pending)

HOUSE uses the existing Browser/Now Playing UI while the Raspberry Pi remains the authoritative playback host: MPD owns the house session, `house-audio-server` supplies control/session policy, and Snapserver distributes synchronized audio. STANDALONE continues to use the proven SMB/Tailscale -> Media3/ExoPlayer path.

The shipped v0.4.3 combined app added live home/away switching, which failed physical acceptance and is superseded as a product requirement by the independent-app decision. Its existing implementation attempts to transfer a playing SMB queue/song/position into idle HOUSE on return so a later S3 joins it. An already-active HOUSE session remains authoritative. This coordinated return requires **house-audio-server v0.9.0**. Departure attempts to carry the current estimated heard track into SMB, without copying the whole HOUSE queue. Shipped HOUSE phone output requires Bluetooth audio; transport commands do not unmute it.

For the shipped combined app only, **HOUSE music root on SMB** in Connections must point to the SMB folder corresponding to the Pi's music-library root. Hold the Browser's **SMB / MP3s / Rap** button to open Connections. For example, if HOUSE lists `MP3s/song.mp3`, the configured root must contain that `MP3s` folder. This mapping is separate from the folder you currently browse, and is required for transfers. Missing or incompatible mapping leaves the transfer paused with an explanation.

The split apps will not need this cross-library mapping or transfer workflow. Current device results for the shipped combined app are maintained only in [docs/HOUSE_VALIDATION.md](docs/HOUSE_VALIDATION.md).

To avoid duplicating state across documents:

- [docs/CENTRAL_PLAYBACK.md](docs/CENTRAL_PLAYBACK.md) is the **normative Android HOUSE behavior/architecture**.
- [docs/HOUSE_VALIDATION.md](docs/HOUSE_VALIDATION.md) is the **current physical-test checklist and live field results**.
- [docs/VALIDATION_STATE.md](docs/VALIDATION_STATE.md) is the **historical validation summary**.
- [docs/RELEASE_0.4.3.md](docs/RELEASE_0.4.3.md) records the **v0.4.3 build/artifacts**; earlier release files retain their historical records.
- [docs/ROADMAP.md](docs/ROADMAP.md) contains **open work and future features only**.
- The authoritative cross-project session rules live in [house-audio-server/docs/SESSION_BEHAVIOR.md](https://github.com/oolah10293/house-audio-server/blob/main/docs/SESSION_BEHAVIOR.md).

## Build

Requirements for v0.4.3:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk / targetSdk 36
- minSdk 26
- Java 17 and Python 3
- Android NDK 28.2.13676358 and CMake 3.22.1
- Media3 1.11.0
- jcifs-ng 2.1.10

The repository intentionally does not include a redistributed Gradle wrapper JAR. On Windows, `SETUP_GRADLE_WRAPPER.bat` downloads the official Gradle 9.6 wrapper JAR and verifies its SHA-256 checksum.

Typical setup:

1. Clone the repository to a local folder.
2. Run `SETUP_GRADLE_WRAPPER.bat` once if `gradle/wrapper/gradle-wrapper.jar` is absent.
3. Open the project in Android Studio.
4. Install the SDK/NDK/CMake versions above, run `python3 native/prepare.py`, then let Gradle sync.
5. Connect an Android device with USB debugging enabled.
6. Run the `app` configuration.

See [BUILD_AND_INSTALL.txt](BUILD_AND_INSTALL.txt) for the short version and [docs/TESTING.md](docs/TESTING.md) for regression checks.

## Project history

The project evolved through saved source checkpoints from v0.1.0 onward. Detailed rationale is preserved in [CHANGELOG.md](CHANGELOG.md).

Important historical fixes include moving playback into a foreground `MediaLibraryService`, adding the Country Buffer, adding SMB read-ahead, fixing metadata-title precedence, eliminating a large-queue sort ANR, keeping Media3 out of minimal-control mode, adding instant filename/folder search, enabling explicit media audio-focus handling for vehicle playback, and preserving the validated v0.3.7 phone/vehicle controls.

For the product-level reasons behind the app, see [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md). For the line between proven behavior and changes still requiring phone testing, see [docs/VALIDATION_STATE.md](docs/VALIDATION_STATE.md).

## Planned / possible future work

Current priorities and later ideas are maintained only in [docs/ROADMAP.md](docs/ROADMAP.md) so this README does not become a second roadmap.

The current philosophy is to keep the player simple and preserve proven playback behavior rather than add features that destabilize the transport stack.

## Privacy and security

No SMB credentials, private network addresses, personal paths, or user-specific data are committed to this repository. Saved SMB passwords are encrypted locally through Android Keystore. See [docs/PRIVACY_AND_SECURITY.md](docs/PRIVACY_AND_SECURITY.md).

## License

No license has been selected for the original app code. The isolated bundled Snapclient and its dependencies retain their own licenses; see [native/README.md](native/README.md). CI distributes corresponding receiver source and build files alongside the APK, and license texts are included in APK assets.
