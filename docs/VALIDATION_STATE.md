# Validation state

This file separates behavior exercised in real use from source changes that still need targeted phone testing.

## v0.4.2 revision checkpoint

v0.4.2 is physically **partially accepted**. Bluetooth connect/disconnect output automation works and **+400 ms** is the current route-specific sync value. Live home/away mode transitions, pre-connected-Bluetooth HOUSE attachment, Galaxy S8 launch, and HOUSE Quit cleanup remain open.

For the detailed current pass/fail list and reproduction sequence, use [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md). For normative behavior, use [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md). This file keeps only the version-by-version validation history.

## v0.4.1 correction checkpoint

The three first-phone-pass corrections are implemented: physical-route home qualification with normal Android routing, pre-command conditional playlist auto-unmute, and an output icon inside the Media3 bottom strip. Local APK build and all 15 tests pass (5 network-policy, 7 output-policy, 3 state), along with both native ABI/license checks. GitHub CI passed for source `6d4305a`, and the delivered APK was extracted from that exact run with the archive digest verified. See [release record](RELEASE_0.4.1.md) for artifacts, checksums and the debug-signing/fresh-install requirement.

v0.4.1 now has a **partial physical pass**. Confirmed on the real phone: HOUSE works with Tailscale connected; the output icon is correct in appearance/location; changing song/PLAY LIST from a muted phone while an S3 is already audible keeps the phone muted while the shared S3 playback changes; and leaving the qualifying home Wi-Fi correctly moves the app to `HOUSE — home network unavailable; reconnecting` rather than treating cellular/Tailscale reachability as still home. **Phone/S3 synchronization is currently a failed/open check:** the phone was observed about one second behind the S3. **Automatic same-song home→away SMB/Tailscale continuation is not implemented yet and therefore failed as expected in the real drive-away test.** Two additional failures were observed on the return/cleanup path: explicit HOUSE Quit can leave the prior HOUSE song cached/displayed locally, and return-home HOUSE reacquisition can remain at `Socket closed / Retrying in 15s` before eventually recovering even though the qualifying home Wi-Fi is already present. Quit must clear phone-local HOUSE presentation without stopping MPD, and qualifying physical route gain must trigger an immediate real HOUSE probe rather than wait for retry backoff. Remaining conditional-output cases, background controller lifecycle, and standalone regression are still pending. Server v0.8.2 is already installed and healthy.

## v0.4.0 HOUSE source checkpoint

Final release artifact: Android `9c89b24` (includes heartbeat recovery), paired with server v0.8.2 `9c98973`. Android CI passed the APK build, 3 state tests and both native receiver/license checks; server CI passed with 90 tests. The delivered APK was recovered from that exact run and its archive digest matched GitHub's digest. This is build/package verification only. [Release record](RELEASE_0.4.0.md).

The first Android HOUSE backend/receiver and Browser polish are implemented; phone/S3 audio synchronization and background/standalone regression acceptance remain **pending**. See [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md). The v0.3.8 phone baseline below is preserved as historical field evidence, not automatically promoted to v0.4.0 validation. Server v0.8.2 is now installed and healthy. After MPD's LAN listener was enabled, Android v0.4.0 successfully entered HOUSE with Tailscale off and Now Playing adopted the current MPD track.

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

## General rule

When changing playback behavior, preserve known-good transport behavior first. A change that looks like cleanup can regress screen-off playback, SMB recovery, Country Buffer behavior, Media3 controls, vehicle routing, or large-queue performance.
