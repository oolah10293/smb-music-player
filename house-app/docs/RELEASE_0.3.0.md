# House Music v0.3.0

Version **0.3.0**, code **5**, package `com.housemusic.player`, Android **8.0 / API 26+**,
target SDK **36**. Current companion server: **house-audio-server v0.11.2**;
v0.11.0 was the original history release. The APK is unchanged by the server fixes.

## All four requested changes

| Request | Delivered behavior |
| --- | --- |
| More radio Now Playing details (#10) | Separate station, title, artist and album lines, supplied broadcast name, bitrate/sample format. Fresh state extras update independently of the cached Media3 queue. Long details scroll above fixed playback/mute/sync controls. |
| Retain the previous radio song (#10; server #9) | Last played shows the previous identified song that accumulated strictly more than ten seconds, including station and local date/time. The Pi saves it while the app is closed and across restarts. The current qualifying song stays current until it ends/changes. Short or blank metadata does not erase the previous record. |
| Smaller folder block and matching gaps (#11) | Folder-panel vertical padding reduced from 8dp to 2dp; space above Sort/Play List reduced to 2dp to match the space above Search, with the buttons' existing drawable insets retained. Short/long folder labels and touch targets retain their behavior. |
| Station URL height matches filename search (#11) | URL field now uses the existing search field's 40dp minimum. Actual inflated field heights match at 320, 360 and 411dp; the filename search remains unchanged. |

The existing working Radio page and swipes remain. Earlier focus recovery and
startup Play fixes remain covered by regression tests. Selecting local music
restores its artwork, metadata and transport controls.

History requires server v0.11.0; on v0.10.0 radio playback/details still work and
history is hidden. Missing station metadata stays unavailable. Supplied combined
artist/title strings are retained without guessing their split. There is no song
recognition or retrospective recovery of songs played before this update.

## Validation

- `assembleDebug testDebugUnitTest lintDebug` passed: **58 tests in 17 suites**,
  zero failures/errors/skips. Full lint: zero errors, 157 warnings.
- `lintDebug -PapiCompatibilityCheck` passed, including the API-26 gate.
- Layout checks inflate real layouts at 320/360/411dp, compare URL and search
  heights, exercise long metadata with fixed controls, and preserve earlier
  folder-label, swipe, audio-focus and actual MediaController recovery checks.
- State/presentation tests cover unchanged-queue radio metadata refresh, separate
  fields, stable previous-song display, missing metadata and older servers.
- Companion server: **174 tests pass**, including strict timing, passive playback
  without a phone, short/blank metadata, station/local switching, persistence,
  checkpoint recovery, paused/stalled intervals and disk failures.

The user confirmed v0.2.1 swiping and station add/save/play. Initial v0.3.0 field
testing exposed raw WXDX metadata; server v0.11.1 addressed the first format but
missed another. Server v0.11.2 handles both, with **192 server tests passing** and
successful GitHub CI, including exact live song and station-announcement captures.

**Field confirmation, 2026-10-10:** after the v0.11.2 fix, the user reported:
“ok, that seems to work pretty well. The UI looks really good.” Record this as
good observed behavior after the metadata fix and positive UI acceptance. Detailed
history timing/app-closed/restart cases and earlier focus/startup checks have no
separate physical confirmation yet.
See [TESTING.md](TESTING.md#v030-radio-details-and-spacing-acceptance).

## Signed artifact and source

- `HouseMusic-v0.3.0.apk`: **8,240,500 bytes**.
- SHA-256: `9f452e6f8ecba54f2e845385c38d9562e47f25976b20b48a55120e5d1b019042`.
- Verified manifest: `com.housemusic.player`, version 0.3.0/code 5, min API 26,
  target API 36; verified APK v2/v3 signatures and ZIP alignment.
- Certificate SHA-256: `7935fde3a71e87e5964399facd15f01d620ad0a6f684442be8e9ce353d10475e`.
  This matches delivered v0.1.1/v0.2.0/v0.2.1, permitting an in-place update.
- Both arm64-v8a and armeabi-v7a receiver binaries and Snapcast/FLAC/Boost notices
  are present. Distribute matching `HouseMusic-v0.3.0-source.zip` alongside the APK.
  It includes all app sources, pinned native dependencies and build scripts.
  `SOURCE_COMMIT.txt` identifies the exact revision. Private signing keys are excluded.

Install the server v0.11.2 update first, then install this APK over the delivered
v0.2.1 APK. Saved stations, phone settings and sync corrections are retained.
No S3 firmware update is required. Source/checksum provenance is recorded on PR #8.
