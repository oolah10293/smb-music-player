# Android v0.4.3 revision

Version name **0.4.3**, version code **15**. This iteration implements live HOUSE/STANDALONE transitions, coordinated return-home transfer with **house-audio-server v0.9.0**, and Bluetooth output-intent corrections. Physical acceptance remains pending in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md).

## Behavior and boundaries

- One service-owned Media3 session switches between the Pi-backed player and the retained standalone ExoPlayer. Activities, notification and external controllers use the selected authority without restarting the app.
- Returning while SMB is playing pauses private playback, reserves idle HOUSE before a temporary muted/not-ready controller attachment, and commits the mapped queue/order/index/position/Shuffle with standalone Repeat All. An S3 joining during the reserved transfer cannot independently start the default. A previously active HOUSE session is adopted without queue replacement.
- Transfer identities are retained locally. Lost acknowledgements are resolved through server status, without blindly replaying queue replacement or resuming private audio. Failed/unknown/incomplete transfers stay paused with a visible status; The foreground notification’s **Stop transfer** action cancels pending transfer. Quit cancels/detaches local participation without stopping an already-committed shared session.
- Physical home departure uses the current estimated heard track and position, accounting for recent track boundaries, Snapclient buffer/server latency and the effective timing correction. It carries the current track only. Muted/paused/stopped departure stays silent. This is approximate continuation, not sample-accurate or gapless switching.
- Connections adds **HOUSE music root on SMB**, the folder corresponding to MPD's music root. Hold the Browser's **SMB / MP3s / Rap** button to open Connections. Transfers require valid relative-path mapping; the app does not guess roots, match by title, or discard unmappable queue entries.
- Every HOUSE output path requires Bluetooth audio. Play/Resume and queue changes preserve local mute; pre-connected Bluetooth is recognized on attachment. Route changes report local output state while the server retains ownership of automatic pause/resume policy.
- Standalone queue/index/position/Shuffle and Stop intent are retained. Bluetooth audio connect/reconnect resumes an available retained session; disconnect pauses it; pre-connected routes are checked on entry. Stop/Quit and muted/paused HOUSE departure prevent unintended resume.
- Physical-route checks avoid unguarded newer Android `RouteInfo` APIs on older supported devices. CI adds a targeted `NewApi` lint gate. This fixes a concrete API compatibility mismatch; no Galaxy S8 trace proves it caused the reported v0.4.2 launch failure.
- Country Buffer, SMB read-ahead, recovery thresholds and the bundled native receiver versions remain unchanged.

## Required server and deployment boundary

Coordinated return requires server **v0.9.0** and its `prepare` / `commit` / `status` / `cancel` handoff contract. Install the paired server update before testing this return path. The previously confirmed deployed server is v0.8.2; committing source does not establish a Pi upgrade.

An explicit library-root mapping is required in both directions. A server/control outage while the qualifying physical home route still exists remains HOUSE recovery. Entire HOUSE queue copying on departure and a configurable network grace period remain outside this iteration.

## Build verification and exact artifacts

Paired server source: [`d19e269`](https://github.com/oolah10293/house-audio-server/commit/d19e2694e9c1222e5eadf88607511130a4a0cb7a). [Server CI run 36814563574](https://github.com/oolah10293/house-audio-server/actions/runs/36814563574) passes all **119 tests**. This is source/build verification, not Pi deployment.

**Pending:** the implementation has not yet received a completed Android CI build/artifact verification record. Fill this section with the passing source commit and run, unit-test and targeted API-lint results, packaged native/license checks, artifact links, exact APK size and SHA-256, archive digests, and signing-certificate SHA-256 before treating the build as verified.

Physical testing is separate from build verification. Do not promote v0.4.2 observations or source-level test results into v0.4.3 hardware PASS results.

## Installation and signing

CI currently uses a newly generated debug certificate per runner. Stable upgrade signing is not configured. Preserve SMB credentials, HOUSE address, music-root mapping and route timing settings before any required uninstall/reinstall. Exact compatibility with the prior delivered APK must be established from this build's signing certificate, not assumed.

Keep the matching corresponding-source archive available alongside the APK when redistributing the bundled native receiver.
