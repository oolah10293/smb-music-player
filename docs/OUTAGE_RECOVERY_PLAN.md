# Prolonged-outage recovery — approved next-revision plan

Status: approved for implementation and field testing. This document does not mean the recovery changes have been implemented or validated.

## Reported failure and goal

After a network outage lasts long enough to exhaust the Country Buffer, playback becomes silent and reports that it is building a recovery buffer. Recovery appears to give up after a short time. Pressing Play manually starts another attempt, which can also appear to give up after roughly 30–40 seconds. The behavior has been reported with the screen both on and off.

The desired behavior is to keep trying to resume the same music without requiring another Play press. The observed 30–40 seconds is not a verified hard-coded session timeout. The exact cause remains unproven; screen-off lifecycle handling alone cannot explain all reported cases.

## Triggers: network availability plus time, not signal bars

- Use network-change/availability events to bring a retry forward when connectivity returns or changes.
- Keep timed retries as a fallback; a network callback is not proof that the SMB server is reachable.
- Judge success by actual SMB file access and subsequent audio-buffer progress, not a Connected label, GPS, or a particular number of signal bars.
- The user's reference to two bars meant trying again when usable service returns. It was not a literal signal-strength threshold requirement.

## Persistence and starting timing policy

- Preserve the existing short initial retry progression (1, 2, 5, 10, then approximately 15 seconds between failed attempts).
- Continue active retries for at least the first ten minutes after playback stalls.
- After ten minutes on battery, back off to approximately one attempt per minute rather than abandoning the pending playback request.
- On charging power, continue active retries without a fixed ten-minute abandonment cutoff.
- A relevant network return/change can shorten the scheduled wait. Debounce event bursts and permit only one active connection/recovery attempt at a time.
- An individual attempt timing out must schedule another attempt; it must not cancel the overall recovery session.
- These timing values are starting targets for testing, not measured performance guarantees.

## Preserve intent and playback state

Keep the user's desire to play distinct from ExoPlayer's temporary paused, buffering, or error state. Preserve the current item, position, full queue, sort, shuffle, and Repeat All policy while recovering.

An explicit user Pause, Stop, or Quit cancels automatic resume. A changed track or replacement queue invalidates obsolete recovery work so late callbacks cannot restore the old song. Respect audio-focus and output-disconnection behavior: recovery must not unexpectedly start the phone speaker after an output is disconnected.

## Detect a stuck attempt without discarding useful progress

Add a no-progress watchdog. If a file open/refill is genuinely stuck, close or cancel that attempt and retry at the retained position. Cancellation must reach the actual I/O, not merely enqueue more work behind a blocked worker.

Measure progress through received bytes and/or increasing playable buffer. Do not impose a short overall refill deadline while data is still arriving: a slow but productive transfer must be allowed to finish. Retain usable buffered media where supported rather than repeatedly throwing away progress.

A second outage during refill returns to the waiting/retry state automatically. No additional Play press is required.

## Resume only after a useful buffer

Retain the approximately 20-second recovery resume threshold, or the remaining track duration when shorter. Do not resume after each tiny burst of arriving audio and recreate the rapid play/stall behavior.

Do not reduce the Country Buffer or change the proven 64 KiB SMB read-ahead, tcpNoDelay, or normal SMB timeout values as a substitute for repairing recovery.

## Screen-off operation and lifecycle

Recovery must remain operational without keeping an Activity visible. Implement and test service-lifecycle handling for prolonged recovery under Android's platform rules, including periods longer than ten minutes. Do not rely solely on normal actively-playing wake behavior or the fact that a charger is connected.

Any extra recovery wake locks must be bounded, scoped, and released when no longer needed. Battery-backoff waiting must not become a tight CPU loop. Validate the selected scheduling/lifecycle approach on the actual phones rather than claiming that a Handler timer alone guarantees screen-off recovery.

## Truthful status and diagnostics

Use recovery state as the source of the displayed status. Distinguish waiting, retrying, rebuilding, user-paused, and stopped states instead of deriving everything from ExoPlayer's READY/BUFFERING flags.

Example UI messages:

- Waiting for SMB — retry in 15s
- Rebuilding buffer — 8 / 20 seconds

Record attempt starts/results, failure categories, last progress, buffer duration, power state, and service lifecycle events for diagnosis. Do not log credentials or publish private server addresses/paths.

## Acceptance tests

1. Play normally, then keep the server unreachable until the existing buffer is exhausted. Test outages of several minutes and at least ten minutes with the screen off. Restore connectivity and verify same-track/same-position automatic recovery without touching the phone.
2. Repeat with the screen on; screen state must not determine whether retries survive.
3. Repeat both on battery and on charging power, including an outage longer than ten minutes. Verify continued active retries on power and slower continued retries on battery.
4. Restore service briefly, interrupt it during refill, and restore it again. Verify retry persistence and no rapid play/stall output.
5. Use a slow but productive connection. Verify the watchdog does not discard progressing downloads.
6. Send Pause, Stop, Quit, track changes, and queue replacements during recovery. Verify canceled/stale work cannot resume the old request.
7. Verify output-disconnection/audio-focus behavior, normal Android Auto controls, Repeat All, shared sort, and normal playback remain intact.
