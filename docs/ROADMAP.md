# Roadmap / ideas

This file contains **open work and future features only**. Completed implementation history belongs in release/validation documents, and normative behavior belongs in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).

## Current Android priorities

1. **Finish live HOUSE <-> STANDALONE handoff**
   - HOUSE -> STANDALONE: continue the same audibly playing track through SMB/Tailscale after physical home-LAN departure.
   - STANDALONE -> HOUSE: automatically adopt the authoritative HOUSE session when the qualifying home LAN returns.
   - Prevent the observed split-brain state where private SMB playback and a separately started S3 HOUSE queue run at the same time.
   - Preserve muted/paused/stopped intent and avoid overwriting the HOUSE queue on return.

2. **Finish Bluetooth output-intent handling**
   - On HOUSE attach/reopen, honor Bluetooth that is already connected when HOUSE is already playing.
   - Keep route timing adjustable; current measured values belong in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md), not the roadmap.
   - Add STANDALONE parity: disconnect retains/pauses the exact local session; reconnect resumes; existing Bluetooth is recognized on app/mode entry; explicit Stop/Quit wins.

3. **Galaxy S8 compatibility**
   - Capture the actual launch crash/stack trace and fix the v0.4.2 startup failure.
   - Tracked separately in Issue #3.

4. **Finish remaining v0.4.2 acceptance**
   - Dedicated HOUSE Quit/reopen stale-state check.
   - Background controller heartbeat/expiry and muted-only pause/resume cases.
   - Physical Android queue-sort preservation check.
   - Standalone/vehicle regression pass.
   - Stable APK signing so updates do not require uninstall/reconfiguration.

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
- Current release artifact: [RELEASE_0.4.2.md](RELEASE_0.4.2.md)
