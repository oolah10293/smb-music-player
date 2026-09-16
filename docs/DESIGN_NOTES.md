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

The player never treats an SMB outage as evidence that the current media file is corrupt. On playback error it records:

- current media item/index
- current playback position
- whether playback was supposed to continue

It pauses and probes the same SMB file after 1 s, 2 s, 5 s, 10 s, then every 15 s. Once reachable, it reparses the same item at the saved position and waits for a useful recovery buffer before resuming.

This behavior is a core product requirement.

## 4. Foreground MediaLibraryService owns playback

Early playback lived in the Activity and could stop after the screen slept. Since v0.2, ExoPlayer lives in `PlaybackService : MediaLibraryService` and uses `C.WAKE_MODE_NETWORK` while actively playing/buffering.

This also enables system/lock-screen/Bluetooth media controls and gives Android a proper media session independent of the Browser Activity lifecycle.

## 5. Metadata title must not be pre-seeded from filename

A previous build set `MediaMetadata.title` from the filename before playback. Because the title was already nonblank, real extractor metadata could fail to replace it.

Current rule:

1. let Media3/extractor metadata provide the embedded title;
2. only use filename-without-extension as a UI fallback when embedded title is absent.

Album artist is preferred over artist; absent secondary fields are hidden.

## 6. Large queue sorting must be one replacement

Repeated `MediaController.moveMediaItem()` calls can issue hundreds of session commands on the UI thread and caused ANR/freezes in a large folder.

Current rule:

1. snapshot the queue once;
2. sort it in memory;
3. remember current item, position, and play/pause state;
4. call one `setMediaItems(...)`;
5. prepare the same item at the saved position and restore intent.

Do not reintroduce per-item move loops for bulk sorting.

## 7. Media3 controller needs enough vertical space

A separate controller region at 132dp caused Media3 to switch into minimal mode, which intentionally hides Previous, Next, and the normal bottom bar. The control region was raised to **200dp** and should remain safely above the minimal-mode threshold unless the controller layout is redesigned and retested.

## 8. Search is a live queue filter

Search intentionally operates on the already loaded current-folder entries; it is not a recursive index.

Current behavior:

- case-insensitive
- current folder only
- matches filenames and subfolder names
- whitespace-separated query terms are ANDed
- punctuation/separators in the actual filename do not matter because each term only needs to occur somewhere in the name
- blank search restores the full listing
- active sort still applies
- `PLAY LIST` queues only visible matching audio files while search is active

This makes search useful as an instant temporary playlist builder without introducing playlist-management state.

## 9. Name-sort and date-sort semantics differ on purpose

Name sorting keeps folders grouped ahead of tracks for normal browsing. Date sorting is a true modified-time sort across both folders and tracks. This fixed the confusing case where a newly downloaded track was counted but appeared hundreds of rows below older folders.

## 10. Vehicle audio focus

v0.3.6 explicitly configures Media3 audio attributes as media/music and enables ExoPlayer audio-focus handling. The intent is for SMB Music to request the vehicle's media audio path directly instead of relying on some other media app to establish it first.

This is an audio-focus/routing change only; a full Android Auto browse tree is still future work.
