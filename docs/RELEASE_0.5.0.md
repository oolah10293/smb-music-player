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

- Source commit: [`c3efbab9cc63520577eb4b924f4d6dabc209be2b`](https://github.com/oolah10293/smb-music-player/commit/c3efbab9cc63520577eb4b924f4d6dabc209be2b).
- [GitHub Actions run 36939800048](https://github.com/oolah10293/smb-music-player/actions/runs/36939800048): **passed** APK assembly, unit tests (five standalone intent cases), API-26/NewApi compatibility lint and standalone APK exclusion check.
- Downloaded artifacts match the SHA-256 digests published by that run. APK v2 signature and signed content digest were verified locally.
- APK manifest verifies `com.smbmusic.player`, version **0.5.0**, code **16**. No native libraries, Snapclient entry or HOUSE package reference exists in the delivered APK.
- No physical v0.5.0 pass is claimed. Bluetooth screen-off reconnect, outage recovery and Stop/Quit still require [device acceptance](TESTING.md). Mobile-data consumption has not been measured or declared fixed.

| Delivered file | Bytes | SHA-256 |
| --- | ---: | --- |
| `SMBMusic-v0.5.0.apk` | 11,230,373 | `313ac4d5970a6b97d05b3418ca779d7d0d638e46a17a3d23c2e3d9feb469f640` |
| `SMBMusic-v0.5.0-source.zip` | 116,801 | `4f58421d711a30253eea7ab156e68e0e602ef32b4bf19aa421a6eddbf9bf1caa` |

The source archive is the exact CI source snapshot above; this verification record was completed afterward. CI artifact IDs are `11199084412` (APK) and `11198959961` (source).

Debug certificate SHA-256: `a98b7693115aa96e5aad226cc960f308e1f480977bb05d685f235996e127693d`.

## Installation

This remains the existing `com.smbmusic.player` application and replaces the combined app on that phone; both cannot be installed together. Its debug certificate differs from the previously delivered v0.4.3 certificate (`61671a45a88bdb17ac203edf26309c605169bb28db20e6039c2766f1101062d2`). Android requires uninstalling that APK before installing this one. Save the SMB address, username and password first; uninstalling clears local settings.

Open SMB Music, enter the existing SMB connection, and play a folder. HOUSE address/root mapping are gone. House Music remains a separate later APK; existing phones that need the combined HOUSE build can keep v0.4.3 until that app is available.
