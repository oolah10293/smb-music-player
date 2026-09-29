# Validation state

This file separates behavior exercised in real use from source changes that still need targeted phone testing.

## v0.4.0 HOUSE source checkpoint

Final release artifact: Android `9c89b24` (includes heartbeat recovery), paired with server v0.8.2 `9c98973`. Android CI passed the APK build, 3 state tests and both native receiver/license checks; server CI passed with 90 tests. The delivered APK was recovered from that exact run and its archive digest matched GitHub's digest. This is build/package verification only. [Release record](RELEASE_0.4.0.md).

The first Android HOUSE backend/receiver and Browser polish are implemented; phone/S3 audio synchronization and background/standalone regression acceptance remain **pending**. See [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md). The v0.3.8 phone baseline below is preserved as historical field evidence, not automatically promoted to v0.4.0 validation. Server v0.8.1 is installed, and restart with an already-present S3 starting a fresh randomized Rap session is proven. Server v0.8.2's queue reorder helper is source/unit-test work awaiting Pi update.

## Proven in regular use before v0.3.7

- Direct SMB browsing and playback.
- Foreground playback with the screen off.
- Lock-screen / system media controls.
- Exact-track, same-position recovery after an SMB/network outage.
- Large **Country Buffer™** behavior on variable network service.
- 64 KiB SMB read-ahead and `tcpNoDelay` substantially improving high-latency/tunneled playback.
- Stock Media3 Previous / Play-Pause / Next controls and buffered seek indication.
- Embedded metadata title taking precedence over filename fallback.
- Large-folder queue sorting without the old repeated-move ANR.
- Instant current-folder filename/subfolder search.
- Search acting as an on-the-fly temporary playlist filter.
- v0.3.6 vehicle audio routing without another media app establishing the path first.
- Steering-wheel track skip through the Media3 session.

## Confirmed v0.3.7 behavior

- Browse does not autofocus Search or open the keyboard.
- Browser and Now Playing are locked to portrait.
- Explicit Play/Resume uses the short ExoPlayer-only fade-in.
- Garmin/Bluetooth media commands work.
- Tailscale connection requests at startup/recovery work in practice while SMB remains the actual reachability test.
- The v0.3.6 Android Auto/audio-focus behavior remains working.

These are the baseline for v0.3.8 and should not be reopened without a direct regression.

## Corrected recovery observation

A prolonged outage can look inactive while the app first waits for SMB and then silently builds the approximately 20-second recovery buffer. After that sequence was understood and allowed to finish, the player resumed successfully at least twice. The core probe/rebuild/resume design is therefore treated as functional evidence, not as a proven abandonment bug.

## Confirmed v0.3.8 phone behavior

Real-phone testing confirmed:

- v0.3.8 installs/runs normally with no obvious regressions.
- Shared Browser/Now Playing sort state works in both directions.
- Selected/current track remains queue item zero after deliberate sorting, using the agreed rotated order.
- Repeat All wraps correctly from the final queued song back to the first.
- The 40dp search field is the desired height.
- The clear-search X behaves as intended: it clears/restores the list and does not raise a hidden keyboard.
- No regressions were observed in the preserved v0.3.7/v0.3.6 behavior: Android Auto/audio focus, Garmin/Bluetooth controls, explicit Play/Resume fade, Tailscale startup behavior, metadata handling, Country Buffer, or SMB transport tuning.

Treat these as the current standalone baseline unless a direct regression is reproduced.

## v0.3.8 behavior still requiring targeted validation

The prolonged-outage recovery hardening has **not yet been field-tested in v0.3.8**. Still verify:

- recovery phase/status reporting;
- network-change-triggered early SMB probes;
- persistence after an individual failed/timed-out probe;
- stale-result rejection;
- no-progress buffer-rebuild watchdog behavior;
- correct explicit Pause/Stop/Quit/queue-replacement behavior during recovery;
- screen-off prolonged recovery.

The older recovery architecture already has functional evidence: after understanding that the app first restores SMB access and then silently rebuilds about 20 seconds of buffer, prolonged recovery was observed to resume successfully at least twice. The v0.3.8 hardening should therefore be tested as targeted robustness work, not assumed to be replacing a fundamentally broken design.

## Current external-network note

Intermittent Android networking failures were previously observed while Tailscale was connected, including normal internet/notifications/Phone Link recovering immediately when Tailscale was disconnected. The same SMB Music build did not need to be open for the failure to occur, so this remains an external Tailscale/Android networking concern unless a direct SMB Music reproduction proves otherwise. Do not weaken the Country Buffer, SMB read-ahead, or audio-focus behavior in response without such evidence.

## General rule

When changing playback behavior, preserve known-good transport behavior first. A change that looks like cleanup can regress screen-off playback, SMB recovery, Country Buffer behavior, Media3 controls, vehicle routing, or large-queue performance.
