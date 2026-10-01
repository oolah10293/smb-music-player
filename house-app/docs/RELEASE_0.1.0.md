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

Source implementation is present. Exact source commit, successful CI run, test/lint/package results, artifact digests and signing certificate will be recorded here after verification. Do not treat this pending record as a successful APK build.

Required release checks:

- APK assembly and app unit tests.
- Targeted NewApi/API-26 compatibility lint.
- Manifest identity and independent package verification.
- No SMB transport/credential classes, app-owned ExoPlayer playback engine or handoff API paths in the House Music APK. Media3 UI dependencies may include unused upstream library classes.
- Native Snapclient executables for both `arm64-v8a` and `armeabi-v7a`, with Snapcast/FLAC/Boost notices.
- Matching source archive containing the pinned native source trees, licenses and build scripts.
- Downloaded artifact digest checks and local APK signature/content verification.

## Installation and first test

This new package installs alongside SMB Music. Quit any older combined app on the same phone before testing House Music. No settings or live state are imported from that app.

On first launch, enter the Pi's LAN host alone in **Server**. No deployment address is bundled, and no SMB path or credentials are needed. Re-enter each phone's previous synchronization correction by holding the output icon in Now Playing. The reported **410 ms** and **385 ms** values are known working corrections from the prior combined build, but the record does not map them to individual phones. Preserve the value from each phone's own old settings and recheck it.

Physical acceptance remains **pending**. The combined app's S8 launch and multi-node synchronization passes are not a v0.1.0 phone pass. Use [TESTING.md](TESTING.md) to record new results, especially background heartbeat, reconnect, Quit, Bluetooth eligibility and synchronization.

Keep `HouseMusic-v0.1.0-source.zip` with `HouseMusic-v0.1.0.apk` when distributing the bundled native receiver. The corresponding source includes Snapcast v0.31.0, FLAC 1.4.3 and Boost 1.85.0, their licenses and the build instructions. Debug signing is used; stable future upgrade signing remains open work.
