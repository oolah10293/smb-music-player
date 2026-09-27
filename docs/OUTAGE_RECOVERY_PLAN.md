# Prolonged-outage recovery — v0.3.8 hardening and field validation

## Corrected understanding

The original concern was that recovery appeared to give up after roughly 30–40 seconds. After the recovery sequence was explained—first regain real SMB access, then silently rebuild about 20 seconds of playable buffer—the app was left alone longer and successfully resumed at least twice.

That evidence changes the engineering conclusion: the existing recovery architecture is fundamentally useful. v0.3.8 does **not** replace it with a new downloader or reduce the Country Buffer. It makes the stages visible and removes several ways an individual attempt could appear to strand the overall session.

## Retained recovery sequence

1. A playback failure records the current media item, queue index, playback position, and play intent.
2. The player is held silent rather than advancing to another song.
3. The exact SMB file is probed on the existing `1 s → 2 s → 5 s → 10 s → every 15 s` schedule.
4. When the probe succeeds, ExoPlayer reopens the same item at the saved position.
5. Playback remains paused while approximately 20 seconds of audio is buffered, or the remainder of the song when shorter.
6. Automatic playback resumes only after that useful buffer is ready.

## v0.3.8 hardening

### A failed attempt is not a failed recovery session

Each SMB probe is one attempt inside a persistent recovery session. A false result, exception, or 30-second attempt watchdog schedules another attempt; it does not cancel the retained song or require another Play press.

The probe executor permits a later attempt to run even if a previous jcifs call is slow to terminate. Late results are rejected with generation and attempt identifiers so obsolete work cannot restore the wrong song.

### Network changes bring a real probe forward

Android's default-network callback is used only as a trigger. A network availability or capability change debounces briefly, then advances the next **actual SMB file probe**. The callback itself is not treated as proof that Tailscale, the SMB server, credentials, or the specific file work.

### Buffer progress is observed

During rebuild, the service tracks increasing buffered position and publishes the buffered-ahead duration to the media session. A 45-second no-progress interval is treated as a genuinely stuck refill: the current source is closed and recovery returns to the SMB-probe loop at the retained position.

A slow transfer that keeps increasing the playable buffer is allowed to continue. There is no short overall refill deadline while useful progress is being made.

### A second outage remains recoverable

An error during buffer rebuilding returns to the recovery path. The user does not need to press Play again merely because service returned briefly and disappeared during refill.

### Explicit user and output intent remain authoritative

- Pause stops automatic resume and retains a paused recovery state.
- Stop, Quit, a track change, or queue replacement invalidates obsolete recovery callbacks.
- Audio-focus loss, audio-becoming-noisy, or output-disconnection handling must not unexpectedly resume through the phone speaker.
- A late loader error after an explicit Pause/Stop cannot create a brand-new automatic-resume request.

## Truthful status

Now Playing receives recovery state through Media3 session extras rather than guessing entirely from READY/BUFFERING flags.

Expected messages include:

- `Waiting for SMB — retry in 15s`
- `Checking SMB…`
- `SMB restored — rebuilding buffer…`
- `Rebuilding buffer — 8 / 20s`
- `Paused`

## Preserved transport behavior

Do not use recovery work as a reason to change:

- 120–600-second Country Buffer;
- 32 MiB target buffer;
- 64 KiB SMB read-ahead;
- `tcpNoDelay`;
- normal SMB connection/socket/response timeouts;
- approximately 20-second recovery resume threshold;
- v0.3.6 media audio-focus behavior;
- v0.3.7 fade, Garmin/Bluetooth, portrait, no-autofocus, or Tailscale behavior.

## Acceptance tests

1. Play normally, then keep SMB unreachable until the Country Buffer is exhausted. Observe waiting/probing status, restore service, and verify same-track/same-position automatic recovery without touching the phone.
2. Repeat with the screen off for several minutes. Recovery must remain owned by the service rather than the Activity.
3. Leave the server unavailable through several individual probe attempts. Verify each timeout/failure schedules another attempt.
4. Restore network service during a long scheduled wait. Verify the network callback advances a real SMB probe and does not itself falsely report success.
5. Restore SMB briefly, interrupt it during the 20-second refill, then restore it again. Verify the app returns to waiting/retry and eventually resumes.
6. Test a slow but steadily progressing connection. Verify the no-progress watchdog does not discard it.
7. Simulate a truly stuck refill. Verify it returns to retry rather than remaining forever in `Rebuilding buffer`.
8. During recovery, send Pause, Stop, Quit, Next/Previous, and a replacement playlist. Verify stale callbacks cannot restore the old request.
9. Disconnect the active output or trigger audio-focus loss during recovery. Verify the phone speaker does not begin playing unexpectedly.
10. Verify the queue, shared sort, Shuffle state, Repeat All, metadata, Android Auto, Garmin/Bluetooth controls, and normal playback remain intact afterward.

## Validation status

The source implements these protections, but the new v0.3.8 behavior must still be field-tested on the actual phones and network path. Successful recovery in older builds supports the architecture; it does not by itself validate every new watchdog and cancellation edge.
