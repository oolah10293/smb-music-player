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

Implementation/build commit: [`d56881f8`](https://github.com/oolah10293/smb-music-player/commit/d56881f8a87b3aa88d6e679b89b5d0f17ff5502f). [CI run 36814746962](https://github.com/oolah10293/smb-music-player/actions/runs/36814746962) passed on 2026-10-01: APK assembly, all **42 unit tests**, targeted **NewApi/API-26 compatibility lint**, both native receiver ABIs, license checks and matching source packaging. Later release-record commits do not change the APK implementation.

The extracted APK is **12,943,938 bytes**. Its manifest identifies `com.smbmusic.player`, version **0.4.3 (15)**. Local verification checked its APK v2 cryptographic signature and content digest, `arm64-v8a` and `armeabi-v7a` receivers, and Snapcast/FLAC/Boost notices. Both downloaded artifact ZIPs match GitHub's digests. The corresponding-source ZIP includes Snapclient, FLAC and Boost sources and their licenses.

| Artifact | Exact CI download |
| --- | --- |
| APK artifact ZIP — contains `app-debug.apk` | [SMBMusicPlayer-debug](https://github.com/oolah10293/smb-music-player/actions/runs/36814746962/artifacts/11141495477) |
| Source artifact ZIP — contains the corresponding-source ZIP | [SMBMusicPlayer-v0.4.3-source](https://github.com/oolah10293/smb-music-player/actions/runs/36814746962/artifacts/11141455754) |

| Bytes identified | SHA-256 |
| --- | --- |
| Delivered `SMBMusicPlayer-v0.4.3.apk` | `ebee7798e59973664af17aa87c930f64c39111331c3d98fb16ac213ca491614a` |
| APK artifact ZIP | `236052313295d998ab34caf8841cd7d2afd03e6909a3a23733c16252c0992db7` |
| Delivered `SMBMusicPlayer-v0.4.3-source.zip` | `db33df02cadbb963ea22f666fb5e39bb94fe73710462be7a15e7853adf3c2ffe` |
| Source artifact ZIP | `38de343885858744b4843c85eef3a2f5a0cba497dc5c7f997872f1d9e0f7e6ad` |

Signing certificate SHA-256: `61671a45a88bdb17ac203edf26309c605169bb28db20e6039c2766f1101062d2`.

Physical testing is separate from build verification. Do not promote v0.4.2 observations or source-level test results into v0.4.3 hardware PASS results.

## Installation and signing

CI currently uses a newly generated debug certificate per runner. Stable upgrade signing is not configured. Preserve SMB credentials, HOUSE address, music-root mapping and route timing settings before any required uninstall/reinstall. **This APK certificate differs from delivered v0.4.2 (`256f969b…`), so an in-place update from that build will fail. Save settings, uninstall the older debug APK, install v0.4.3, then re-enter settings.**

Keep the matching corresponding-source archive available alongside the APK when redistributing the bundled native receiver.
