# SMB Music v0.5.0 — standalone release

Version name **0.5.0**, version code **16**, application ID `com.smbmusic.player`, launcher label **SMB Music**, minimum Android **8.0 / API 26**.

## Scope

- Restore the pre-HOUSE v0.3.8 service/playback core, selectively retaining later standalone improvements.
- Keep Country Buffer (120–600 seconds, 32 MiB target, 3-second start and 20-second recovery thresholds), 64 KiB SMB read-ahead, same-track recovery, explicit Play fade, Repeat All and external controls.
- Keep the later browser layout/button swaps, current-folder label, shared sort/search/scroll behavior and artwork/metadata.
- Keep app-local retained queue/index/position/Shuffle and Bluetooth pause/resume with explicit Stop/Quit protection. Quit releases local service/audio/notification immediately.
- Remove HOUSE discovery, API/controller, native Snapcast output, mode changes, queue handoff, transfer journal/settings and custom HOUSE controls. No Pi or S3 update is needed.
- Preserve the previous combined code on [house-music-pre-split](https://github.com/oolah10293/smb-music-player/tree/house-music-pre-split) for the independent **House Music** app, which is not delivered here.

The optional mini-log and deferred House Music manual mute fix are not part of this standalone extraction.

## Verification

Build, unit tests, API-26/NewApi compatibility lint, and APK isolation check are required before delivery. Exact CI/artifact/signature results will be recorded after the build finishes. No physical v0.5.0 pass is claimed; use [TESTING.md](TESTING.md).

## Installation

This remains the existing `com.smbmusic.player` application. The CI debug signer may differ from the previously installed APK. Save SMB address, username and password before an uninstall, which clears local settings. The release handoff will state whether an in-place update is possible after comparing certificates.

Open SMB Music, enter the existing SMB connection, and play a folder. HOUSE address/root mapping are gone. House Music remains a separate later APK; existing phones that need the combined HOUSE build can keep v0.4.3 until that app is available.
