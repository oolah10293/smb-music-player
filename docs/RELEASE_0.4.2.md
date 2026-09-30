# Android v0.4.2 revision

v0.4.2 implements immediate service-owned HOUSE Quit cleanup, event-triggered identity/control reacquisition with browser retry reset, and the approved Bluetooth local-output policy. Holding the existing output icon opens separate phone/wired and Bluetooth sync adjustments with reported buffer/latency diagnostics. Subsequent physical testing found **+400 ms** audibly correct for the currently tested phone/output route; keep the adjustment available until other devices/routes are measured.

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

v0.4.2 now has **partial** real-device acceptance. Follow [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md) for the live checkpoint. v0.3.8 remains the confirmed standalone field baseline.

Confirmed on the current primary test path:

- Bluetooth connect -> HOUSE phone output auto-unmutes: **PASS**.
- Bluetooth disconnect -> HOUSE phone output auto-mutes: **PASS**.
- Phone/S3 audible synchronization at **+400 ms** correction: **PASS for the currently tested phone/output route**. Keep route timing adjustable until other phones/Bluetooth devices are tested; do not make +400 ms a universal hard-coded value.

Open/failing:

- HOUSE -> STANDALONE same-song continuation after physical home-LAN loss: **FAIL / still absent**. The app detects departure but remains HOUSE-reconnecting rather than continuing over SMB/Tailscale.
- STANDALONE -> HOUSE on physical return home: **FAIL**. v0.4.2 can remain in local SMB playback after the qualifying home LAN is restored. Closing/reopening the app can then detect HOUSE, which proves cold/reopen qualification can work while live mode transition is still broken.
- The failed return transition can create **two independent playback worlds**: the phone continues its standalone SMB queue while powering an S3 starts/joins the separate authoritative HOUSE queue.
- HOUSE attachment with Bluetooth **already connected**: **FAIL** when HOUSE is already playing. Reopen can detect HOUSE yet leave the phone muted. Existing route state must be evaluated at attachment; a pre-existing Bluetooth output should join an already-playing HOUSE session just as a new Bluetooth-connect event does. Existing Bluetooth alone must not start a deliberately idle/stopped HOUSE session.
- Galaxy S8 launch compatibility: **FAIL**; v0.4.2 crashes when opened. Root cause is not yet established.
- HOUSE Quit cleanup still needs a dedicated v0.4.2 physical verdict.

New desired behavior recorded after this release: STANDALONE/SMB should receive the same Bluetooth output-intent ergonomics. Disconnect should pause/silence local playback while retaining exact queue/song/position; reconnect should resume the retained standalone session; already-connected Bluetooth must be recognized on app/mode entry; explicit Stop/Quit wins; with no retained session, Bluetooth connect alone starts nothing.


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
