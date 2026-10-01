# Regression testing

SMB Music v0.5.0 is standalone. Historical combined-app device acceptance is in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md). This file is the reusable standalone/regression suite; older field results do not automatically validate a new build.

The project accumulated several fixes where a seemingly harmless UI or performance change could regress proven playback behavior. Run these checks after meaningful playback, SMB, queue, Media3, or Browser changes.

## Core playback

- Play/Pause/Previous/Next work.
- Stock Media3 buffered-ahead seek indication is visible.
- Shuffle remains available.
- Repeat All remains active for every nonempty queue.
- A one-track queue repeats that track.
- Screen-off playback continues for an extended period on battery.
- Lock-screen, Garmin/Bluetooth, and steering-wheel controls work.
- Explicit Play/Resume fades in over roughly 1.5 seconds without changing Android's system media volume.
- Automatic track-to-track transitions do not fade.

## Retained standalone Bluetooth session

- With an available SMB queue, Bluetooth audio disconnect pauses and retains the exact queue/index/position; reconnect resumes it.
- Already-connected Bluetooth is evaluated when the app restores its standalone session.
- Explicit Stop and Quit prevent route callbacks or process restart from resurrecting playback.
- With no retained queue, Bluetooth connect creates no playlist.
- A watch/input-only Bluetooth connection is not an audio output.
- Repeat during SMB recovery and screen-off playback; reconnect must not bypass recovery buffering or explicit Stop/Quit.
- Save/reload a queue containing spaces, punctuation and Unicode in its paths; credentials remain in the encrypted credential store.

## Standalone separation

- Launch on home Wi-Fi with the Pi/S3s playing: SMB Music opens its own SMB browser/session and does not change the house queue.
- Switch Wi-Fi/cellular with Tailscale available: the same SMB engine continues or recovers, with no HOUSE transfer status.
- There are no HOUSE address/root settings, mute/sync controls or bundled Snapcast receiver.
- Browser opens immediately without waiting for a HOUSE probe.
- API-26 compatibility lint and standalone APK exclusion checks pass in CI.

## SMB outage recovery

1. Start a track and note its position.
2. Make the SMB route unavailable long enough to exhaust the local playback buffer.
3. Confirm the app stays on the same media item rather than skipping.
4. Confirm Now Playing reports waiting and probe phases rather than appearing silently abandoned.
5. Leave the server unavailable through several retries; one failed/timed-out attempt must not end recovery.
6. Restore the route.
7. Confirm the status changes to SMB restored/buffer rebuilding.
8. Confirm playback resumes near the saved position only after a useful buffer is rebuilt.

Expected retry schedule: `1 s → 2 s → 5 s → 10 s → every 15 s`.

Additional recovery tests:

- Restore network during a scheduled wait and verify the next real SMB probe is advanced.
- Do not treat Android's Connected status as SMB success.
- Interrupt service again during refill and verify automatic return to retry.
- Use a slow but productive link and verify progressing buffer is not discarded.
- Test a truly stalled rebuild and verify the no-progress watchdog retries it.
- Send Pause, Stop, Quit, Next/Previous, and a replacement queue during recovery; stale work must not resume the old song.
- Disconnect the output or trigger audio-focus loss during recovery; the phone speaker must not start unexpectedly.
- Repeat with the screen off.

## Country Buffer / poor-network test

- Start with a healthy network and confirm buffered-ahead progress grows substantially.
- Move to a variable/high-latency connection.
- Confirm already buffered playback continues through short interruptions.
- Do not judge the Country Buffer only on stable Wi-Fi; its purpose is resilience under changing coverage.

## Shared sort

- Browser and Now Playing initially show the same active mode.
- Change Browser sort, open Now Playing, and verify the same label appears.
- Change Now Playing sort, return to Browser, and verify the same label and browser order appear.
- Merely changing pages must not rebuild or reorder the active queue.
- `A–Z` and `Z–A` keep folders grouped for browsing.
- `New–Old` and `Old–New` sort folders and tracks together by modified time.
- A newly modified standalone track in a directory containing many folders can appear at the top under `New–Old`.

## Current/selected-track-first queue rotation

Given the sorted list `A B C D E F`:

- Tap `D`; verify the active queue is `D E F A B C` and starts at item zero.
- While `D` is playing, explicitly select another sort mode; verify the newly sorted sequence is rotated from `D`, with `D` still at item zero.
- Verify playback position and playing/paused state survive active-queue sorting.
- Verify Shuffle state survives active-queue sorting.
- Verify no item is dropped or duplicated.
- Press `PLAY LIST` without choosing a track; verify the first sorted/filtered track is item zero.
- Choose a new folder or filter that excludes the old song; verify the new playlist replaces the old queue outright.

## Search / PLAY LIST

- Blank Search shows the complete current-folder listing.
- Filtering updates immediately while typing.
- Results include matching subfolders and supported audio filenames.
- `blink 182` matches names containing both `blink` and `182`, including `Blink-182`, `Blink_182`, `Blink$182`, and `Blink182`.
- The white search field has a 40dp minimum height and remains readable under font scaling.
- Browse opens without Search focus and without the keyboard.
- Tapping the typing area opens the keyboard.
- The X is absent while Search is blank and visible while text is present.
- With the keyboard hidden, tapping X clears Search and leaves the keyboard hidden.
- With the keyboard already open, tapping X clears Search without forcing the keyboard open or closed.
- Clearing immediately restores the complete sorted listing.
- Clearing does not reload the folder and does not alter the active playback queue.
- With active Search, `PLAY LIST` queues only matching playable tracks.
- With blank Search, `PLAY LIST` queues all playable tracks in the folder.

## Repeat All

- A normal folder queue wraps from its last queued item to its first.
- A search-filtered queue repeats only the tracks actually queued, not the entire source folder.
- A one-track queue repeats the same track.
- Sorting, page navigation, SMB recovery, and queue replacement leave Repeat All enabled.
- External media controls cannot leave the local player in Repeat Off or Repeat One.
- Explicit Pause, Stop, and Quit do not cause Repeat All to restart intentionally stopped playback.

## Metadata

- Embedded title is shown when present.
- Filename-without-extension is used only if embedded title is absent.
- Album artist is preferred over artist.
- Missing artist/album fields are hidden rather than replaced with placeholder text.

## Media3 control-layout regression

- Previous / Play-Pause / Next remain visible.
- Stock seek bar and buffered-ahead progress remain visible.
- The dedicated controls region remains 200dp unless a deliberate redesign is being tested; shrinking it can trigger Media3 minimal mode.

## Vehicle / Android Auto

- Start SMB Music directly without first starting another media app.
- Confirm the vehicle routes SMB Music to the speakers.
- Confirm steering-wheel Next/Previous work.
- Confirm Play/Pause from vehicle controls works when available.
- Confirm v0.3.8 queue/recovery changes do not alter audio-focus behavior.

## Tailscale

- Launch while Tailscale is disconnected and verify the connection request is made.
- Confirm SMB browsing is still the source of truth; a VPN Connected label alone must not report the folder as loaded.
- After repeated SMB browse failures, verify another connect request can occur without blindly disconnecting an otherwise healthy VPN.

## Quit

Standalone regression:
- Quit stops standalone playback.
- The live player/queue is released. A retained queue stays stopped until an explicit Play.
- Foreground playback notification disappears.
- Quit releases service resources immediately and closes the app screens.

HOUSE Quit behavior/acceptance is maintained in [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md), not duplicated here.
