# House Music v0.2.1

Version **0.2.1**, code **4**, package `com.housemusic.player`, Android **8.0 / API 26+**, target SDK **36**.

## Required scope

| Issue | Change | Regression evidence |
| --- | --- | --- |
| [#4](https://github.com/oolah10293/smb-music-player/issues/4) | Short folder names center between MP3s/Rap and Parent Folder. Longer names move to a row below, aligned with the visible left edge of MP3s/Rap, with end truncation. Changing folders remeasures the header. | Actual layouts at 320, 360 and 411dp; short → long → short transitions. |
| [#5](https://github.com/oolah10293/smb-music-player/issues/5) | An explicit Play, local queue play, or station play requests audio focus again after loss/denial on an unmuted phone. Background polls do not keep requesting focus. Manual mute survives Play and late focus callbacks. | Denial → explicit retry, loss → retry/denial/gain, manual mute, and startup readiness tests. |
| [#6](https://github.com/oolah10293/smb-music-player/issues/6) | The app Play/Pause button uses service-published server/lease readiness and transport, independently of Media3's cached startup timeline and commands. Its custom transport action goes through the same guarded server command path. Own-app standard player permissions are granted explicitly. | Real MediaSession/MediaController startup and lease-recovery test, plus the actual button recovering while the controller deliberately still has no queue or Play command. Loss of authority disables the button and suppresses writes. |
| [#9](https://github.com/oolah10293/smb-music-player/issues/9) | Horizontal swipes replace page navigation buttons: Library → Now Playing → Radio, with reverse swipes back. Existing Activities are reused. A horizontal gesture cancels the original child touch before navigation; text/seek/control gestures and vertical scrolling retain their behavior. | Left/right navigation, child cancellation/no accidental playback tap, vertical scroll/refresh delivery, excluded controls, ordinary taps, short drags and multitouch. Existing Radio Add & Play navigation guard remains covered. |

The user's latest instruction authorizes implementation of all four, superseding issue #9's earlier instruction to record the swipe request without building it yet.

## Validation and limits

Automated build/test results and signed artifact details are recorded below. No phone or Bluetooth hardware is attached to this build environment. The precise field sequence behind #6 was not reproduced here; this revision removes the app button's dependence on the stale Media3 state implicated by the report and tests that recovery path directly. Physical checks for all four are in [TESTING.md](TESTING.md).

The user already confirmed v0.2.0 Radio URL entry, saved-station selection and playback. Those observations remain v0.2.0 evidence; they are not claimed as v0.2.1 hardware passes.

## Install and source

Install `HouseMusic-v0.2.1.apk` over the delivered v0.2.0 or v0.1.1 APK. The retained signing certificate allows an in-place upgrade preserving settings. Distribute the matching `HouseMusic-v0.2.1-source.zip`, including pinned Snapcast, FLAC and Boost sources and notices. The private signing key is excluded.

## Verified build and artifact

- `assembleDebug testDebugUnitTest lintDebug`: **passed** on 2026-10-10. **55 tests in 16 suites**, zero failures/errors/skips. Full lint: **zero errors, 156 warnings**.
- `lintDebug -PapiCompatibilityCheck`: **passed**, including the API-26 compatibility gate.
- APK: **8,297,758 bytes**, SHA-256 `7891d5dfee360e0851b87950066c870babe0df01da03eaf552166c40c93fde25`.
- Manifest: `com.housemusic.player`, version **0.2.1 / 4**, minimum API **26**, target API **36**.
- APK v2/v3 signatures verified. Signing certificate SHA-256: `7935fde3a71e87e5964399facd15f01d620ad0a6f684442be8e9ce353d10475e`, matching the delivered v0.1.1/v0.2.0 APKs.
- ZIP alignment, both `arm64-v8a` and `armeabi-v7a` receiver binaries, and bundled Snapcast/FLAC/Boost notices verified.
- Source archive checksum and exact commit are recorded in the PR artifact record; `SOURCE_COMMIT.txt` inside the archive identifies its revision.

A stale Kotlin incremental cache initially reported a duplicate extension; recompiling with `-Pkotlin.incremental=false` cleared it. The final test run uses Robolectric's supported legacy graphics mode, with an unambiguously long text fixture and attached touch targets. An attempted native-graphics test run terminated in this environment; it is not counted as a pass or visual phone validation.
