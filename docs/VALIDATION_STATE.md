# Validation state

This file separates behavior that has been exercised in real use from changes that still need a specific regression test.

## Proven in regular use before v0.3.6

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

## v0.3.6 changes

v0.3.6 intentionally made only narrow changes on top of the v0.3.5 baseline:

- swapped the Now Playing sort/Browse button functions while preserving their physical layout;
- renamed `PLAY FOLDER` to `PLAY LIST`;
- changed multi-term search to AND semantics;
- made the search field white;
- explicitly configured media/music audio attributes with ExoPlayer audio-focus handling.

## Confirmed in v0.3.6

The v0.3.6 audio-focus change fixed the vehicle-routing problem in real use:

- SMB Music routes audio to the vehicle directly without requiring another media app to establish the path first.
- Steering-wheel track skip works through the Media3 session.

Treat this audio-focus behavior as proven and preserve it unless a future regression test directly implicates it.

## Current external-network note

Intermittent Android networking failures have been observed while Tailscale is connected, including normal internet/notifications/Phone Link recovering immediately when Tailscale is disconnected. The same SMB Music build is not required to be open for the failure to occur, so this is currently being treated as an external Tailscale/Android networking issue rather than an SMB Music playback regression. Do not change the proven Country Buffer, SMB read-ahead, or audio-focus behavior in response without a direct reproduction tying the failure to SMB Music.

## General rule

When changing playback behavior, preserve known-good transport behavior first. A change that looks like a cleanup or optimization can still regress screen-off playback, SMB recovery, Country Buffer behavior, Media3 control layout, or large-queue performance.
