# Validation state

This file separates behavior exercised in real use from v0.3.8 changes that still need targeted phone testing.

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

## New v0.3.8 behavior requiring targeted validation

- Shared Browser/Now Playing sort state.
- Repeat All permanently enabled for every nonempty standalone queue.
- Selected/current track rotated to item zero with the remaining sorted order wrapping from it.
- A newly selected folder/filter replacing the old queue outright.
- 40dp search field.
- Clear-search X that does not request focus or raise a hidden keyboard.
- Recovery phase/status reporting.
- Network-change-triggered early SMB probes.
- Per-probe timeout persistence and stale-result rejection.
- No-progress buffer-rebuild watchdog.
- Correct cancellation/pause behavior during recovery.
- Preservation of all confirmed v0.3.7 and earlier behavior.

## Current external-network note

Intermittent Android networking failures were previously observed while Tailscale was connected, including normal internet/notifications/Phone Link recovering immediately when Tailscale was disconnected. The same SMB Music build did not need to be open for the failure to occur, so this remains an external Tailscale/Android networking concern unless a direct SMB Music reproduction proves otherwise. Do not weaken the Country Buffer, SMB read-ahead, or audio-focus behavior in response without such evidence.

## General rule

When changing playback behavior, preserve known-good transport behavior first. A change that looks like cleanup can regress screen-off playback, SMB recovery, Country Buffer behavior, Media3 controls, vehicle routing, or large-queue performance.
