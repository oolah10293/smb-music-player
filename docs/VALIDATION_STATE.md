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

## Still needs targeted validation

The v0.3.6 audio-focus change was added because vehicle playback could require starting audio from another media app before SMB Music became audible through the vehicle speakers.

The next targeted vehicle test is:

1. connect normally;
2. do **not** start another media app;
3. start playback directly in SMB Music;
4. verify audio is routed to the vehicle immediately;
5. verify vehicle play/pause controls behave normally.

If that succeeds, the audio-focus change can be treated as a confirmed fix. If it does not, investigate Android Auto/media-session routing without changing the proven SMB or buffering stack.

## General rule

When changing playback behavior, preserve known-good transport behavior first. A change that looks like a cleanup or optimization can still regress screen-off playback, SMB recovery, Country Buffer behavior, Media3 control layout, or large-queue performance.
