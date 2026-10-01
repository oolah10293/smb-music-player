# Roadmap / ideas

This file contains **open work and future features only**. Completed implementation history belongs in release/validation documents, and normative behavior belongs in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).

## Current Android priorities

1. **Reconsider handoff orchestration after both v0.4.3 field failures**
   - Server v0.9.0 is installed. Departure reached SMB recovery without successful continuation; return reached an incomplete transfer and remained paused. See [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md) for evidence and limits. Do not treat these as merely untested paths or fix them by accumulating more independent retry flags.
   - Proposed design: one service-owned coordinator decides playback mode and transfer progress; network observers, HOUSE runtime, Bluetooth, and SMB recovery report facts. Preserve S8 compatibility, synchronization, the proven standalone buffer, and Bluetooth automation.
   - Separate reservation, commit attempted, uncertain outcome, success, and confirmed cancellation. Resolve an outstanding commit before resuming private playback. Preserve the original failure and transition phase instead of hiding them behind “transfer incomplete.”
   - Stop the outgoing audio reader after preserving the transition snapshot. Measure actual SMB reads, discarded read-ahead, retry causes, and source closure; the screenshot's 72.19 MB app counter does not establish excessive use. Mobile Services' 2.23 GB is separately attributed.
   - Validate event sequences: route changes during prepare/attach/commit, S3 arrival, delayed acknowledgement, Stop/Quit, restart, and Bluetooth changes. Then establish one real same-song trip in both directions before claiming handoff acceptance.
   - Retain the idle-HOUSE queue transfer and active-HOUSE adoption requirements. Departure currently carries only the estimated heard track; whole-queue HOUSE -> away copying remains undecided.

2. **Fix the manual mute button; preserve Bluetooth automation**
   - **Queued on 2026-10-01; implementation deferred at the user's request.** v0.4.3 blocks manual Unmute when Bluetooth is absent, preventing use of a third phone's headphone jack. Make the icon a normal manual Mute/Unmute control for the phone's current Android audio output, including wired headphones, without requiring Bluetooth.
   - Keep the existing Bluetooth automatic mute/unmute and connection behavior unchanged. This request adds a working manual override; it does not remove Bluetooth functionality.
   - Recheck the reported two-node Pause/Resume failure: shared transport must preserve local mute and must never independently unmute the phone. An explicit press of Unmute is a separate user action.
   - Check pre-connected Bluetooth on HOUSE attachment, manual mute across transport commands, and retained standalone connect/disconnect/reconnect behavior.
   - Complete HOUSE Quit/reopen, screen-off heartbeat/expiry, muted-only pause/resume, and active-queue-sort acceptance.
   - Preserve standalone/vehicle behavior through the new service mode boundary.

3. **Release reliability**
   - Stable APK signing so upgrades do not require uninstall/reconfiguration.
   - Retest retained-session recovery after process death and interrupted handoff persistence.

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
