# Roadmap / ideas

This file contains **open work and future features only**. Completed implementation history belongs in release/validation documents, and normative behavior belongs in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).

## Current Android priorities

1. **Split into completely independent SMB Music and HOUSE Android apps**
   - **User decision, 2026-10-01:** no connection between the apps. This supersedes automatic LAN/cellular/LAN handoff, the proposed coordinator redesign, and the briefly discussed manual transfer. The normative boundary is [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).
   - SMB Music owns only its standalone SMB/ExoPlayer session. The HOUSE app controls/renders only the Pi's shared MPD/Snapcast session. No shared queue/song/position, SMB-triggered HOUSE reset, automatic or manual transfer, app-specific launch/stop coordination, or background synchronization.
   - Produce separate installable apps and remove the combined mode-switching machinery from their playback paths. Keep the Country Buffer, SMB retry/recovery and vehicle behavior in SMB Music; keep S8 compatibility, synchronized HOUSE rendering, timing adjustment and HOUSE controls. Retain Bluetooth automation within each app.
   - Current shipping code remains combined Android v0.4.3 with server v0.9.0. No new APK or runtime change follows from this plan update. Preserve existing deployment and historical failed-handoff evidence; do not require successful handoffs as a gate for the independent apps.
   - Validate that playing/pausing/quitting SMB Music or changing Wi-Fi/cellular never touches HOUSE. HOUSE launch/reconnection adopts current server state without importing SMB music or controlling that app. Each app must recover its own connection independently.
   - Address reader shutdown and measure SMB data use within the standalone app. The screenshot's 72.19 MB app counter does not establish excess use; Mobile Services' 2.23 GB is separately attributed.
   - **Approved diagnostic space retained:** the Now Playing album-art area may show a bounded, readable, timestamped mini-log during diagnosis. Show each app's own connection, audio-reader lifecycle, actual errors and retries; label measured SMB payload bytes accurately. Preserve surrounding controls and omit credentials. Transfer/mode-switch events are obsolete with the split; the log must not create a connection between apps.

2. **Fix the manual mute button; preserve Bluetooth automation**
   - **Queued on 2026-10-01; implementation deferred at the user's request.** v0.4.3 blocks manual Unmute when Bluetooth is absent, preventing use of a third phone's headphone jack. Make the icon a normal manual Mute/Unmute control for the phone's current Android audio output, including wired headphones, without requiring Bluetooth.
   - Keep the existing Bluetooth automatic mute/unmute and connection behavior unchanged. This request adds a working manual override; it does not remove Bluetooth functionality.
   - Recheck the reported two-node Pause/Resume failure: shared transport must preserve local mute and must never independently unmute the phone. An explicit press of Unmute is a separate user action.
   - Check pre-connected Bluetooth on HOUSE attachment, manual mute across transport commands, and retained standalone connect/disconnect/reconnect behavior.
   - Complete HOUSE Quit/reopen, screen-off heartbeat/expiry, muted-only pause/resume, and active-queue-sort acceptance.
   - Preserve standalone/vehicle behavior in the independent SMB Music service.

3. **Release reliability**
   - Stable APK signing so upgrades do not require uninstall/reconfiguration.
   - Retest each app's own retained state and recovery after process death; determine installation/settings migration without retaining a live connection between apps.

4. **Reliability experiments**
   - Field-test the prepared multi-second HOUSE Country Buffer without assuming stale-buffer flush behavior.
   - Continue correlating intermittent S3 dropouts with diagnostics.
   - Complete the dedicated standalone prolonged-outage hardening test.

## Next major HOUSE features

- **Internet radio through MPD:** tracked in [house-audio-server Issue #5](https://github.com/oolah10293/house-audio-server/issues/5). MPD remains the source authority.
- **Dedicated synchronized subwoofer node:** tracked in [house-audio-esp32 Issue #4](https://github.com/oolah10293/house-audio-esp32/issues/4).

## Later candidates

- `.m3u` / `.m3u8` playlist-file support.
- Smart Shuffle / listening-history weighting while preserving ordinary Shuffle.
- Metadata-assisted filename cleanup as a separate library-maintenance tool.
- Recursive or metadata-indexed search if ever needed.
- Album-art fallback/cache improvements.
- Richer Android Auto browse tree.

## References

- Current physical results: [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md)
- Historical validation: [VALIDATION_STATE.md](VALIDATION_STATE.md)
- Normative HOUSE architecture: [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md)
- Current release record: [RELEASE_0.4.3.md](RELEASE_0.4.3.md)
