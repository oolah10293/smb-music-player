# Android v0.4.1 correction release

This release implements the three corrections recorded after the v0.4.0 phone test. Server v0.8.2 is already installed and healthy; no server code or ESP32 firmware change is required.

- Physical non-VPN Wi-Fi/Ethernet with a direct route to the configured Pi address qualifies home. MPD/service identity checks, HTTP control and Snapcast audio use normal Android routing, allowing Tailscale to remain connected. Physical route loss closes phone output and suspends HOUSE requests/heartbeats.
- Song/PLAY LIST reads transport and audible-output state before the write. A muted phone stays muted if another output was already audible, and auto-unmutes when starting from pause/stop or no audible output. Unknown audibility preserves mute; failed reads abort the write. Explicit Play from pause/stop auto-unmutes. A newer manual mute and uncertain command failures remain authoritative.
- Mute/Unmute is a speaker icon inside the lower Media3 controller strip, hidden in STANDALONE.

## Build and artifacts

Version name **0.4.1**, version code **13**. Source/build commit: [`6d4305adc63a459eda8e153fbf277adc3659819f`](https://github.com/oolah10293/smb-music-player/commit/6d4305adc63a459eda8e153fbf277adc3659819f). [GitHub CI run 36653501314 passed](https://github.com/oolah10293/smb-music-player/actions/runs/36653501314) on 2026-09-30. Later documentation commits do not change the app implementation.

Local verification and CI both passed the debug APK build and unit-test task. **15 tests:** 5 direct-route/network-policy, 7 pre-command output-policy and 3 HOUSE state tests. Both `arm64-v8a` and `armeabi-v7a` Snapclient receivers and GPL/FLAC/Boost notices are packaged. The recovered APK's manifest, signature and native/license contents were checked; the downloaded artifact ZIP matches GitHub's digest.

| Artifact | Exact download |
| --- | --- |
| APK ZIP — extract `app-debug.apk` | [SMBMusicPlayer-debug](https://github.com/oolah10293/smb-music-player/actions/runs/36653501314/artifacts/11071334551) |
| Matching corresponding source | [SMBMusicPlayer-v0.4.1-source](https://github.com/oolah10293/smb-music-player/actions/runs/36653501314/artifacts/11071642359) |

The delivered `SMBMusicPlayer-v0.4.1.apk` is the exact extracted CI APK, **12,899,954 bytes**. Keep the matching source available alongside it when redistributing; that archive includes the pinned Snapclient/FLAC/Boost sources and build scripts.

| Bytes identified | SHA-256 |
| --- | --- |
| Delivered APK | `5f0ad06f76c6965c0d514b69f2bb87dd82374005f0ce6e1af0f19e4e74c609cb` |
| APK artifact ZIP | `ae733c7268903d3071e24397b0ea898851dffdc7252a79311c9d11d7166f1149` |
| Source artifact ZIP (GitHub digest) | `c0a3ce7f0bb4d7489bae8204f879eda1948be7a67684c7e434ffa6b55a91e4d7` |

## Installation and signing

This CI debug build uses a different signing key from the delivered v0.4.0 APK, so Android **cannot install it in place over that APK**. Record the SMB connection details and configured House address first, then uninstall v0.4.0 and install v0.4.1; uninstalling clears locally saved app settings. No Pi or S3 update is needed.

Verified certificate SHA-256:

- v0.4.0: `c7b08cc1f275f474f35bfa72fec09399a9633b045e4ba91532ac48a06a8262da`
- v0.4.1: `bcd9d0b72e296d90a0cc1a9b1f72a4eeb018f25050f7b12f63b80a649296d285`

The existing CI workflow generates a debug keystore on each runner; it does not currently provide stable upgrade signing.

## Next physical checkpoint

Follow [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md): first Tailscale-on launch/toggle and silent state adoption, then conditional mute/UI behavior, phone/S3 audio synchronization and controller lifecycle. Test physical route loss with VPN reachability still available, then recovery. No v0.4.1 hardware pass is claimed.

Live home/away same-song handoff remains the following slice, with the existing open grace/queue/idle-return decisions preserved. The confirmed standalone field baseline remains v0.3.8. Historical v0.4.0 evidence and exact artifacts remain in [RELEASE_0.4.0.md](RELEASE_0.4.0.md).
