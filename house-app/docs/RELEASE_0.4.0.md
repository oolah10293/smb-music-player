# House Music v0.4.0 — complete radio update

This release continues from the delivered v0.3.1 pager branch. It contains all
three planned radio changes and retains the swipe behavior the user accepted.
The Android application remains `com.housemusic.player`, version code 7, API 26+.

- **Resume Playlist:** replaces the inactive LIVE button beside Quit while radio
  is selected and the server advertises a ready saved playlist. The app sends
  only the current opaque snapshot ID to `/radio/resume-playlist`. The Pi owns
  queue order, duplicate track identity, position and playback modes. No snapshot
  means no button. An in-flight request disables the button. Stale/unavailable
  requests are rejected; errors refresh state without automatically replaying a
  write. An uncertain restore asks the user to select a station or folder.
  Requires house-audio-server v0.13.0. Manual phone mute is preserved.
- **Station ranking and placement:** each Radio visit adopts the Pi's listening
  ranking, then keeps existing rows stable during updates and refreshes. A newly
  added station is pinned at the top for that visit. Rename, delete and Undo keep
  row positions. Leaving and returning adopts the fresh ranking. Delayed reads
  from an earlier visit cannot replace the current list; Add & Play retains its
  guard against playing after navigation away.
- **Radio control alignment:** Play/Pause sits centered on the Mute/gear row in
  radio mode. File playback returns to its previous layout. Hidden seek, previous,
  next, shuffle and repeat controls do not imply radio time shifting.

## Verification and delivery

The CI build runs the existing pager/audio/radio regressions plus new tests for
resume availability, exact single-request payloads, stale/error paths, station
visit order, busy state and radio layout alignment. It builds both native audio
ABIs, checks API 26 compatibility, and packages matching native source/licenses.
Build results, delivered APK checksum and signature verification are recorded in
the release pull request after the build completes. CI APKs must be re-signed
with the retained House Music key before delivery as in-place updates.

Physical acceptance is pending for this release: install over the current app;
play a folder, switch among stations, then Resume Playlist; confirm queue, track,
position, modes and mute behavior; check no-snapshot and second-phone behavior;
confirm ranking on reentry, stable rows, new-station placement and delete/Undo;
check radio alignment and file layout on the user's phones. Server startup and
audible playback acceptance do not establish the new end-to-end restore path.
