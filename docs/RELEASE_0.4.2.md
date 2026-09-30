# Android v0.4.2 revision

v0.4.2 implements immediate service-owned HOUSE Quit cleanup, event-triggered identity/control reacquisition with browser retry reset, and the approved Bluetooth local-output policy. Holding the existing output icon opens separate phone/wired and Bluetooth sync adjustments with reported buffer/latency diagnostics. The offset defaults to zero; the roughly one-second phone/S3 delay remains undiagnosed and needs physical measurement.

## Behavior and boundaries

- Quit explicitly tears down the service's local HOUSE receiver, state and media session while Activities may still be bound. Selection epochs reject late startup probes and old-service cleanup. The only HOUSE lifecycle write is controller detach; no MPD Stop/Clear is added.
- Physical network events coalesce for 200 ms, then request identity/control recovery without waiting for the five-second heartbeat or browser's 15-second backoff. A successful probe/reconnection resets a pending browser retry. Timers remain fallback; neither route gain nor a failed probe falsely marks the server available.
- Media-capable Bluetooth output additions unmute, final removal/noisy output events mute. Duplicate callbacks preserve subsequent manual mute; silent initial attachment remains muted. A deliberate start with Bluetooth connected unmutes regardless of other audible outputs. All route events remain phone-local; existing server session policy controls automatic muted-only pause/resume.
- Hold the existing output icon for route-specific timing correction. Default 0 ms, range ±2000 ms; positive is earlier. Actual Snapclient ServerSettings provide buffer and server latency; advance retains at least 200 ms headroom. The receiver rejoins when correction changes. The cause of the field sync error has not been established.
- Optional [shared 3000 ms buffer trial](https://github.com/oolah10293/house-audio-server/blob/main/docs/HOUSE_BUFFER_TRIAL.md) is prepared separately. No Pi configuration or ESP32 firmware was changed. Ordinary FIFO transport changes do not guarantee a shared buffer flush; audible control latency must be measured.

Version name **0.4.2**, version code **14**. Local debug APK assembly and all **27 unit tests** pass: 5 network, 8 output-intent, 3 state, 3 selection/quit epoch, 4 Bluetooth-transition and 4 timing tests. Both native receiver ABIs and all three license notices are packaged. Exact CI/artifact verification follows below. The matching source archive retains pinned native sources and license notices.

## Installation and signing

The CI workflow currently generates a new debug certificate for each runner. Stable upgrade signing is not configured. Expect to save the SMB credentials/House address, uninstall the older debug build and re-enter settings after installation if the signing certificates differ. Exact certificate verification accompanies the delivered APK below.

## Physical acceptance

No v0.4.2 phone/S3 pass is claimed. Follow [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md), starting with Quit/reopen and route regain, then Bluetooth and measured sync. Automatic HOUSE→standalone same-song continuation is still unimplemented; the repository's open transition/queue/idle-return/heard-position choices are preserved. v0.3.8 remains the confirmed standalone field baseline.
