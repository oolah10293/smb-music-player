# Regression testing

The project accumulated several fixes where a seemingly harmless UI or performance change could regress proven playback behavior. The following checks should be run after meaningful playback, SMB, queue, or Media3 changes.

## Core playback

- Play/pause/previous/next work.
- Stock Media3 buffered-ahead seek indication is visible.
- Regular Shuffle remains available.
- Screen-off playback continues for an extended period on battery.
- Lock-screen and Bluetooth controls work.

## SMB outage recovery

1. Start a track and note its position.
2. Make the SMB route unavailable long enough to exhaust the local playback buffer.
3. Confirm the app stays on the same media item rather than skipping.
4. Restore the route.
5. Confirm the app reconnects, rebuilds a useful buffer, and resumes near the saved position.

Expected retry schedule: `1 s → 2 s → 5 s → 10 s → every 15 s`.

## Country Buffer / poor-network test

- Start with a healthy network and confirm buffered-ahead progress grows substantially.
- Move to a variable/high-latency connection.
- Confirm already buffered playback continues through short interruptions.
- Do not judge the Country Buffer only on stable Wi-Fi; its purpose is resilience under changing coverage.

## Browser / sort

- `A–Z` and `Z–A` keep folders grouped for browsing.
- `New–Old` and `Old–New` sort folders and tracks together by modified time.
- A newly modified standalone track in a directory containing many folders can appear at the top under `New–Old`.
- Sorting a queue containing hundreds of tracks does not freeze the UI.
- Queue sorting preserves the same track and approximately the same playback position.

## Search / PLAY LIST

- Blank search shows the complete current-folder listing.
- Filtering updates immediately while typing.
- Results include matching subfolders and supported audio filenames.
- `blink 182` matches names containing both `blink` and `182`, including `Blink-182`, `Blink_182`, `Blink$182`, and `Blink182`.
- Clearing the field restores the full listing.
- With an active search, `PLAY LIST` queues only matching playable tracks.
- With a blank search, `PLAY LIST` queues all playable tracks in the folder.

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
- Confirm pausing/resuming from vehicle controls works when available.

## Quit

- Quit stops playback.
- Queue clears.
- Foreground playback notification disappears.
- Service stops when the UI disconnects.
