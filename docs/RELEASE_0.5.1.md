# SMB Music v0.5.1 — connection and Bluetooth resume corrections

Version **0.5.1**, code **17**, package `com.smbmusic.player`, minimum Android **8 / API 26**, target **36**.

## Changes

The v0.5.0 split retained Tailscale broadcasts in Browser but gave service-only resume/recovery no connection request. The new shared connector sends directly to Tailscale's exported `IPNReceiver` from startup, media Play, Bluetooth resume and active SMB recovery. Requests are at least 30 seconds apart; it removes the prior unconditional two-second duplicate which can replace Tailscale's still-running connect worker. It never sends Disconnect, and only actual SMB access establishes recovery. Tailscale must already be installed and authorized by Android.

Bluetooth uses a manifest receiver for system A2DP-connected and audio-class ACL-connected events. Android 12+ requests Nearby devices permission. The receiver wakes only a non-stopped retained queue; the service waits for an actual media audio sink and promotes a started media-playback foreground service before requesting focus. In-progress resume protects foreground standby, retains failures across duplicate route callbacks, and has a bounded retry burst. Disconnect, Pause, Stop and Quit cancel pending retry work as applicable. Once playback is audible the pending request clears. Force-stopped apps still require a user launch, as required by Android.

Holding Browser or Now Playing status opens the last 50 diagnostic events. Foreground/focus blocks are surfaced; no SMB URLs, credentials or filenames are logged. Country Buffer, SMB transport, recovery thresholds, Android Auto/Garmin controls and House Music runtime remain unchanged.

## Validation

Local APK assembly and all **12 unit tests pass** (10 standalone-intent cases, 2 shared connect-throttle cases). API-26/NewApi lint passed. APK signature and manifest are verified, and the artifact contains no HOUSE package or native Snapclient. Full lint also passes with **0 errors and 73 warnings** (predominantly existing style/resource warnings); the pre-existing SMB data-source opt-in annotation was added, without changing transport behavior. Phone Bluetooth/VPN acceptance was pending at release. On **2026-10-09**, the user confirmed Bluetooth and Tailscale connectivity are fixed and reported acceptable data/battery use. Cellular-outage automatic resume remains an open field failure ([Issue #7](https://github.com/oolah10293/smb-music-player/issues/7)); see [VALIDATION_STATE.md](VALIDATION_STATE.md) for the dated evidence and exact usage-counter windows, and [TESTING.md](TESTING.md) for regression procedures. These changes address concrete source gaps; they do not prove which gap caused every field failure.

## Installation

The delivered local APK has certificate SHA-256 `7935fde3a71e87e5964399facd15f01d620ad0a6f684442be8e9ce353d10475e`. It differs from v0.5.0 (`a98b7693115aa96e5aad226cc960f308e1f480977bb05d685f235996e127693d`), so save the SMB address/username/password, uninstall old SMB Music, then install this APK and enter those details. Allow Nearby devices and notifications. House Music is a separate installed package.

The exact key used for this delivered APK is retained privately in `SMBMusic-v0.5.1-signing-backup.zip` with alias/build instructions. Reuse it for subsequent delivered APKs; never put it in the repository. Default GitHub Actions builds generate their own debug key and are verification artifacts, not guaranteed in-place updates to this delivered APK.

## Implementation references

- [Android Bluetooth implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- [Android foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android audio-focus requirements](https://developer.android.com/media/optimize/audio-focus)
- [Tailscale external connect receiver](https://github.com/tailscale/tailscale-android/blob/main/android/src/main/java/com/tailscale/ipn/IPNReceiver.java)

## Delivered APK

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `SMBMusic-v0.5.1.apk` | 11296303 | `be6d76786e1a15cab0da3d334674f90f3817d26966c19c020245c5e249fb227a` |

## Source identity

Published implementation: [`0e8888544b459be869f22806917e9d22cf916245`](https://github.com/oolah10293/smb-music-player/commit/0e8888544b459be869f22806917e9d22cf916245), tree `49d81cff2539d0dcaa21826a46ae7f15090cea69`. The delivered source ZIP identifies the equivalent local build commit `e856197953f9f3cfc3e55d3a5b29ce934b3ab6d7`; its tree is byte-for-byte identical to the published implementation. This release-record addition follows the archived snapshot.

`SMBMusic-v0.5.1-source.zip`: 120,187 bytes, SHA-256 `c9067305613295a33cab16b62fbdb843d1bb0bbf4071dca8b7fcf4c26c42c806`.
