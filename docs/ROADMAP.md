# Roadmap / ideas

This file contains **open work and future features only**. Completed implementation history belongs in release/validation documents, and normative behavior belongs in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).

## Current Android priorities

1. **Validate v0.4.3 live transitions with server v0.9.0**
   - Confirm idle-HOUSE return transfers the playing SMB queue/order/song/position/Shuffle and Repeat All before a later S3 joins.
   - Exercise S3 arrival during the reservation/commit window, active-HOUSE adoption, acknowledgement loss, explicit Stop/Quit, and server restart.
   - Measure departure continuity at track boundaries and after seek with the explicit SMB music-root mapping. The current implementation carries only the estimated heard track; whole-queue HOUSE -> away copying remains undecided.
   - Decide whether physical-network departure needs a grace period after field evidence.

2. **Validate Bluetooth and controller lifecycle**
   - Recheck the reported two-node Pause/Resume failure: no Bluetooth must always mean muted HOUSE phone output.
   - Check pre-connected Bluetooth on HOUSE attachment, manual mute across transport commands, and retained standalone connect/disconnect/reconnect behavior.
   - Complete HOUSE Quit/reopen, screen-off heartbeat/expiry, muted-only pause/resume, and active-queue-sort acceptance.
   - Preserve standalone/vehicle behavior through the new service mode boundary.

3. **Galaxy S8 compatibility**
   - Run the build with guarded public Android route APIs on the S8. The source API mismatch is fixed, but it is not a proven explanation for the reported launch crash.
   - Capture a crash trace if launch still fails; tracked separately in Issue #3.

4. **Release reliability**
   - Stable APK signing so upgrades do not require uninstall/reconfiguration.
   - Retest retained-session recovery after process death and interrupted handoff persistence.

5. **Reliability experiments**
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
