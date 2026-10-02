# House Music v0.1.0 — first independent release

Version name **0.1.0**, version code **1**, application ID `com.housemusic.player`, launcher label **House Music**, minimum Android **8.0 / API 26**, target SDK **36**.

## Scope

Extract the HOUSE controller and receiver from the preserved combined v0.4.3 source into an independently installable Android app. SMB Music v0.5.0 remains a separate app and build.

- Preserve Pi folder browsing, filtered PLAY LIST, shared queue/transport, metadata, search/sort and Browser/Now Playing layout.
- Preserve server-owned MP3s/Rap passive-default selection, controller registration/heartbeats, native Snapcast receiver, route-specific timing correction and older Android API guards.
- Preserve existing Bluetooth output automation and local Mute/Unmute policy. The requested manual Unmute override without Bluetooth remains explicitly deferred.
- Reconnect only to the current house session. Quit detaches this phone, releases its receiver/service and clears local presentation without global Stop/Clear.
- Remove SMB credentials/transport/decoder, retained private queue, root mapping, mode switching, heard-position transfer, transfer journal and handoff API calls from this app.
- Use a distinct application ID, private settings, service/session and notification identity. No app-to-app storage, IPC, launch/stop coordination or queue synchronization is introduced.

No server or S3 update is required. Existing **house-audio-server v0.9.0** remains compatible. Its unused handoff endpoints can be removed later, after the combined apps that still call them are retired. Server cleanup is not part of this APK release.

The optional diagnostic mini-log, node DSP/crossover/volume/phase controls and internet radio are outside this extraction.

## Build verification

Source commit: [`2130422cf65488eab045926b044a2f400711bb1e`](https://github.com/oolah10293/smb-music-player/commit/2130422cf65488eab045926b044a2f400711bb1e).

[House Music build 36943322128](https://github.com/oolah10293/smb-music-player/actions/runs/36943322128) passed:

- APK assembly and **25 unit tests**.
- Targeted NewApi/API-26 compatibility lint.
- Both native Snapclient ABIs and all Snapcast/FLAC/Boost notices.
- APK exclusion checks for the old SMB package, jcifs transport, SMB reader and handoff API paths. Source review also confirms no app-owned ExoPlayer playback engine or private queue.

Downloaded artifact ZIP digests match GitHub's published SHA-256 values. Local verification confirmed the APK v2 signature and signed content digest, `com.housemusic.player`, label **House Music**, version **0.1.0 / 1**, minimum SDK **26** and target SDK **36**. Native receivers are 2,830,048 bytes (`arm64-v8a`) and 1,466,980 bytes (`armeabi-v7a`).

The matching source archive contains the exact application sources, native source trees, pinned download markers, licenses and build scripts. It is a self-contained Gradle project and does not require the SMB Music source tree. This verification record and minor documentation clarifications were completed after that build snapshot.

| Delivered file | Bytes | SHA-256 |
| --- | ---: | --- |
| `HouseMusic-v0.1.0.apk` | 8,125,369 | `7cf5aeb4540cc459d8d0be7a755e1e1a1811e61ae76d5b4d9a63f5b58aa5b03f` |
| `HouseMusic-v0.1.0-source.zip` | 29,743,324 | `fe90e92568216903fd397087e08a665005183c7e88b70bad34be5159ed6cb7b0` |

CI artifact IDs: `11200578973` (APK), `11200703699` (source). Debug certificate SHA-256: `3fb445e31620dc65fdeeda3353cfde089a369ca2f139b9d96bba92b9e15453d3`.

The unchanged SMB application also passed its [separate build/test/compatibility/isolation workflow](https://github.com/oolah10293/smb-music-player/actions/runs/36943322326). No replacement SMB APK is delivered by this House Music release.

## Installation and first test

This new package installs alongside SMB Music. Quit any older combined app on the same phone before testing House Music. No settings or live state are imported from that app.

On first launch, enter the Pi's LAN host alone in **Server**. No deployment address is bundled, and no SMB path or credentials are needed. Re-enter each phone's previous synchronization correction by holding the output icon in Now Playing. The reported **410 ms** and **385 ms** values are known working corrections from the prior combined build, but the record does not map them to individual phones. Preserve the value from each phone's own old settings and recheck it.

Physical acceptance remains **pending**. The combined app's S8 launch and multi-node synchronization passes are not a v0.1.0 phone pass. Use [TESTING.md](TESTING.md) to record new results, especially background heartbeat, reconnect, Quit, Bluetooth eligibility and synchronization.

Keep `HouseMusic-v0.1.0-source.zip` with `HouseMusic-v0.1.0.apk` when distributing the bundled native receiver. The corresponding source includes Snapcast v0.31.0, FLAC 1.4.3 and Boost 1.85.0, their licenses and the build instructions. Debug signing is used; stable future upgrade signing remains open work.
