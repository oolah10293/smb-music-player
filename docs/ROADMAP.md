# Roadmap / ideas

This file contains **open work and future features only**. Completed implementation history belongs in release/validation documents, and normative behavior belongs in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).

## Current Android priorities

1. **Finish live HOUSE <-> STANDALONE handoff**
   - HOUSE -> STANDALONE: continue the same audibly playing track through SMB/Tailscale after physical home-LAN departure.
   - STANDALONE -> idle HOUSE: transfer the phone's actively playing SMB session to the Pi, preserving queue/order, track, position and Shuffle/Repeat; stop private SMB playback as the handoff takes effect.
   - Coordinate transfer with passive auto-start. An S3 powered on after return or during handoff must join that session, never start a separate default queue. Adopting a wrongly started second queue afterward is not the fix.
   - If HOUSE was already active before arrival, adopt it without overwriting its queue. Preserve Bluetooth eligibility and paused/stopped intent; mere controller attachment does not start a session.

2. **Finish Bluetooth output-intent handling**
   - Enforce Bluetooth audio eligibility on every HOUSE output path. Play/Resume and queue changes must never independently unmute the phone; fix the reported two-node Pause/Resume case where the phone sounds without Bluetooth.
   - On HOUSE attach/reopen, honor Bluetooth that is already connected when HOUSE is already playing.
   - Keep route timing adjustable; current measured values belong in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md), not the roadmap.
   - Add STANDALONE lifecycle triggers: connect starts/resumes an available retained SMB session; disconnect retains/pauses the exact queue/track/position; reconnect resumes; existing Bluetooth is recognized on app/mode entry; explicit Stop/Quit wins; no retained session means no automatic playlist.

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
