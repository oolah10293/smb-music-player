# Android v0.4.2 revision

v0.4.2 implements immediate service-owned HOUSE Quit cleanup, event-triggered identity/control reacquisition with browser retry reset, and the approved Bluetooth local-output policy. Holding the existing output icon opens separate phone/wired and Bluetooth sync adjustments with reported buffer/latency diagnostics. Live physical acceptance is intentionally maintained in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md), not in this release record.

## Behavior and boundaries

- Quit explicitly tears down the service's local HOUSE receiver, state and media session while Activities may still be bound. Selection epochs reject late startup probes and old-service cleanup. The only HOUSE lifecycle write is controller detach; no MPD Stop/Clear is added.
- Physical network events coalesce for 200 ms, then request identity/control recovery without waiting for the five-second heartbeat or browser's 15-second backoff. A successful probe/reconnection resets a pending browser retry. Timers remain fallback; neither route gain nor a failed probe falsely marks the server available.
- Media-capable Bluetooth output additions unmute, final removal/noisy output events mute. Duplicate callbacks preserve subsequent manual mute; silent initial attachment remains muted. A deliberate start with Bluetooth connected unmutes regardless of other audible outputs. All route events remain phone-local; existing server session policy controls automatic muted-only pause/resume.
- Hold the existing output icon for route-specific timing correction. Default 0 ms, range ±2000 ms; positive is earlier. Actual Snapclient ServerSettings provide buffer and server latency; advance retains at least 200 ms headroom. The receiver rejoins when correction changes. The cause of the field sync error has not been established.
- Optional [shared 3000 ms buffer trial](https://github.com/oolah10293/house-audio-server/blob/main/docs/HOUSE_BUFFER_TRIAL.md) is prepared separately. No Pi configuration or ESP32 firmware was changed. Ordinary FIFO transport changes do not guarantee a shared buffer flush; audible control latency must be measured.

Version name **0.4.2**, version code **14**. Local debug APK assembly and all **27 unit tests** pass: 5 network, 8 output-intent, 3 state, 3 selection/quit epoch, 4 Bluetooth-transition and 4 timing tests. Both native receiver ABIs and all three license notices are packaged. GitHub CI also passed for source `bfb785d94c84778487b898d132c95b79ffa6462b`; exact artifacts are below. The matching source archive retains pinned native sources and license notices.

## Installation and signing

The CI workflow currently generates a new debug certificate for each runner. Stable upgrade signing is not configured. **The delivered v0.4.2 certificate differs from v0.4.1, so an in-place update will fail. Save the SMB credentials/House address, uninstall the older debug build, install v0.4.2 and re-enter settings.** Exact certificate verification accompanies the delivered APK below.

## Physical acceptance

This file records what **v0.4.2 shipped**, its build verification, signing, and exact artifacts. It is not the live field-status document.

Current physical acceptance—including Bluetooth behavior, sync calibration, home/away transition failures, device compatibility, and pending checks—is maintained in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md). Historical milestone status is summarized in [VALIDATION_STATE.md](VALIDATION_STATE.md).

## Exact CI build and artifacts

Source/build commit: [`bfb785d94c84778487b898d132c95b79ffa6462b`](https://github.com/oolah10293/smb-music-player/commit/bfb785d94c84778487b898d132c95b79ffa6462b). [CI run 36756928917](https://github.com/oolah10293/smb-music-player/actions/runs/36756928917) passed on 2026-09-30, including APK assembly, unit tests, native/license checks and source packaging. Later documentation-only commits do not change the app implementation.

| Artifact | Exact download |
| --- | --- |
| APK ZIP — extract `app-debug.apk` | [SMBMusicPlayer-debug](https://github.com/oolah10293/smb-music-player/actions/runs/36756928917/artifacts/11117930488) |
| Matching corresponding source | [SMBMusicPlayer-v0.4.2-source](https://github.com/oolah10293/smb-music-player/actions/runs/36756928917/artifacts/11117995242) |

The delivered `SMBMusicPlayer-v0.4.2.apk` is the exact extracted CI APK, **12,912,426 bytes**. Manifest version name/code, signature, both native receivers and Snapcast/FLAC/Boost notices were verified. The artifact ZIP digest matches GitHub. Keep the corresponding source available alongside the APK when redistributing.

| Bytes identified | SHA-256 |
| --- | --- |
| Delivered APK | `cbaa46d41a739419e5ebee095392236030430333d648790a8e113373067b4f60` |
| APK artifact ZIP | `57ebaa8c1def1576d89d5fb75b6320782086fcaaad0e78b7cdfd0a9af4ee7003` |
| Source artifact ZIP (GitHub digest) | `d4b45645e11bb4ffc88ebeb6ee343e5d3b0caa3beaec3c54b9185a76e88393e6` |

Verified v0.4.2 signing certificate SHA-256: `256f969b2b6c1d9302c8f9f1686d8193d66a69b9b8ef10e0869491a4774484ac`. The delivered v0.4.1 used `bcd9d0b72e296d90a0cc1a9b1f72a4eeb018f25050f7b12f63b80a649296d285`.

The coordinated server documentation/helper commit is [`3032e08`](https://github.com/oolah10293/house-audio-server/commit/3032e0864c977d7610486ab178ccae6a40b3c308); [server CI](https://github.com/oolah10293/house-audio-server/actions/runs/36756569693) passes all 95 tests. ESP32 handoff notes are [`e46b859`](https://github.com/oolah10293/house-audio-esp32/commit/e46b8596cb61f1a640b10e52af3edb3aa58b7a29), without firmware changes.
