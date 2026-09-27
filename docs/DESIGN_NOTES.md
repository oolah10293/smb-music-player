# Design notes

These notes preserve the reasoning behind values and structures that may otherwise look excessive or unusual during future cleanup.

## 1. Country Buffer™ is intentional

The player is meant to tolerate highly variable network service. A conventional small streaming buffer worked on stable Wi-Fi but was poor when cellular service changed rapidly or disappeared briefly.

The current `DefaultLoadControl` deliberately uses:

- `minBufferMs = 120000`
- `maxBufferMs = 600000`
- `bufferForPlaybackMs = 3000`
- `bufferForPlaybackAfterRebufferMs = 20000`
- `targetBufferBytes = 32 MiB`
- `prioritizeTimeOverSizeThresholds = true`

The goal is simple: **when bandwidth is available, bank enough compressed audio that a short network outage does not become an audible outage.**

Do not reduce these values merely because they are larger than typical streaming defaults. Change them only with an explicit regression test over unreliable networking.

## 2. SMB read-ahead solves a different problem

jcifs-ng `SmbFileInputStream` is unbuffered. Media extractors may request many small reads. On a local network the round-trip penalty is small; over a tunnel/cellular path it can become the throughput bottleneck.

`SmbDataSource` therefore wraps the SMB stream in a **64 KiB `BufferedInputStream`**. Media3 still sees its normal DataSource API, but many small extractor reads are served from the local read-ahead buffer.

This must not be confused with the Country Buffer:

- 64 KiB SMB read-ahead reduces protocol transaction overhead.
- 120–600 seconds of ExoPlayer buffering stores playable media ahead of the current position.

Both are useful and operate at different layers.

## 3. Network errors are not bad songs

The player never treats an SMB outage as evidence that the current media file is corrupt. On playback error it retains:

- current media item/index
- current playback position
- full queue
- sort and Shuffle state
- Repeat All policy
- whether playback was supposed to continue

It pauses and probes the same SMB file after 1 s, 2 s, 5 s, 10 s, then every 15 s. Once reachable, it reparses the same item at the saved position and waits for a useful recovery buffer before resuming.

Real-use observation showed that this design can recover; the delay was partly the unseen SMB-probe and 20-second refill phases. v0.3.8 therefore exposes and hardens the existing state machine instead of replacing it.

## 4. Individual attempts must not own the whole recovery session

A timed-out probe is one attempt, not permission to abandon the retained song. v0.3.8 uses attempt/generation identifiers so late callbacks are ignored, a per-probe watchdog so another attempt can be scheduled, and an executor that does not force every future probe to wait behind one blocked call.

Network callbacks only advance an SMB probe. They never declare recovery complete.

During refill, increasing playable buffer is progress. A slow but progressing connection must be allowed to continue. A genuinely unchanged buffered position for the watchdog interval returns the state machine to the SMB-probe phase.

## 5. Foreground MediaLibraryService owns playback

Early playback lived in the Activity and could stop after the screen slept. Since v0.2, ExoPlayer lives in `PlaybackService : MediaLibraryService` and uses `C.WAKE_MODE_NETWORK` while actively playing/buffering.

This also enables system/lock-screen/Bluetooth media controls and gives Android a proper media session independent of the Browser Activity lifecycle.

## 6. Metadata title must not be pre-seeded from filename

A previous build set `MediaMetadata.title` from the filename before playback. Because the title was already nonblank, real extractor metadata could fail to replace it.

Current rule:

1. let Media3/extractor metadata provide the embedded title;
2. only use filename-without-extension as a UI fallback when embedded title is absent.

Album artist is preferred over artist; absent secondary fields are hidden.

## 7. Large queue sorting must be one replacement

Repeated `MediaController.moveMediaItem()` calls can issue hundreds of session commands on the UI thread and caused ANR/freezes in a large folder.

Current rule:

1. snapshot the queue once;
2. sort it in memory;
3. remember current item, position, play/pause state, and Shuffle state;
4. rotate the current item to index zero;
5. call one `setMediaItems(...)`;
6. prepare item zero at the saved position and restore intent.

Do not reintroduce per-item move loops for bulk sorting.

## 8. Current/selected track first means rotation

The agreed order is a rotation of the sorted sequence, not “current item plus a separately sorted remainder.”

Example:

- sorted: `A B C D E F`
- current/selected: `D`
- queue: `D E F A B C`

When a newly selected folder or filtered list excludes the old current song, the new playlist replaces the old queue outright.

## 9. Sort state is shared; queue sorting is deliberate

Browser and Now Playing read and update one stored `SortMode`. Returning from one page to the other updates the visible label and Browser order.

Page navigation alone must not rebuild the active playback queue. Only a deliberate active-queue sort action on Now Playing reorders the queue.

## 10. Repeat All is a fixed standalone policy

Every nonempty local queue loops, including filtered and one-track queues. Repeat applies to the actual queued items; it does not reload the source folder.

Shuffle remains independently user-controlled. Repeat All does not override explicit Pause, Stop, or Quit, and external controllers are not allowed to leave the standalone player in Repeat Off or Repeat One.

Future HOUSE session-end rules remain server-owned and are not overridden by this local policy.

## 11. Media3 controller needs enough vertical space

A separate controller region at 132dp caused Media3 to switch into minimal mode, which intentionally hides Previous, Next, and the normal bottom bar. The control region was raised to **200dp** and should remain safely above the minimal-mode threshold unless the controller layout is redesigned and retested.

## 12. Search is a live queue filter

Search intentionally operates on the already loaded current-folder entries; it is not a recursive index.

Current behavior:

- case-insensitive
- current folder only
- matches filenames and subfolder names
- whitespace-separated query terms are ANDed
- punctuation/separators in the actual filename do not matter because each term only needs to occur somewhere in the name
- blank Search restores the full listing
- active sort still applies
- `PLAY LIST` queues only visible matching audio files while Search is active

This makes Search useful as an instant temporary playlist builder without introducing playlist-management state.

The clear X is deliberately not implemented as a normal focusable part of the `EditText`. It is a separate non-focusable control so clearing a hidden Search cannot raise the keyboard. If the user is already typing, clearing does not forcibly change the existing keyboard state.

## 13. Name-sort and date-sort semantics differ on purpose

Name sorting keeps folders grouped ahead of tracks for normal browsing. Date sorting is a true modified-time sort across both folders and tracks. This fixed the confusing case where a newly downloaded track was counted but appeared hundreds of rows below older folders.

## 14. Vehicle audio focus

v0.3.6 explicitly configures Media3 audio attributes as media/music and enables ExoPlayer audio-focus handling. This fixed real vehicle routing and must be preserved.

v0.3.7 added the Play/Resume fade and Garmin command authorization without removing the audio-focus fix. v0.3.8 carries all three behaviors forward.
