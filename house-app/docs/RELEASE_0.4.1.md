# House Music v0.4.1 — Bluetooth controls and playback errors

House Music remains `com.housemusic.player`, version code 8, Android API 26+.
This update builds on the complete v0.4.0 radio release and retains the accepted
swipe behavior. It does not modify SMB Music or receiver firmware.

## Changes

- Bluetooth Pause and shutdown Stop mute this phone instead of sending a house
  Pause/Stop. Bluetooth Play unmutes only while a Bluetooth audio output is
  currently present. Toggle keys use the phone's mute choice, with key-up and
  repeat events consumed without a second toggle.
- Media-key callbacks and Bluetooth transport-controller requests use the same
  local behavior. Android framework/anonymous legacy media requests follow this
  rule too. Identified third-party controllers retain their transport behavior;
  the app's onscreen and own notification Play/Pause remain house controls.
- The media session reports the muted phone as paused so a headset can issue
  Play next, while the app's button continues to show the server's house state.
  Metadata, server queue and capabilities remain available.
- The existing server policy handles the listener condition: other audible
  nodes continue; muting the only audible phone automatically pauses and retains
  the session. Returning an audible output resumes only an automatically paused
  session. Bluetooth Play cannot override a deliberate house Pause/Stop.
- Resume Playlist uncertainty includes the server's `lastError`. Command failures
  identify the action and retain useful detail across routine status reads until
  the next deliberate house command. Connection problems take priority. Ambiguous
  writes still receive read-only refreshes and are never automatically replayed.
- The bounded local connection log records media event origin and local action,
  without device addresses or names. Its title no longer displays an obsolete
  version. Hold the status text to inspect it.

Keep the deployed house-audio-server v0.13.1: its seek-readiness fix is required for
the user's confirmed Resume Playlist result. No server package is part of this
Android update. Creation-date sorting was dropped after inspection of the NAS
timestamps; file sorting is unchanged.

## Verification

The Android CI gate assembles both native ABIs, runs unit/UI/session regressions,
checks API 26 compatibility, and verifies bundled licenses and separation from
the SMB engine. New tests cover raw media keys, route loss, repeated keys,
transport-controller attribution, muted media-session state, the actual app
Pause button, and persistent/readable command errors. Existing startup recovery,
radio intent/capabilities, Resume Playlist, station order and layout tests remain.

The unchanged server v0.13.1 suite passed all 129 tests during this build's
preparation, including muted-only pause, audible-output recovery and deliberate
Pause ownership. Build result, delivered APK/source checksums and retained-key
signature evidence are recorded in the release pull request when available.
CI APKs require signing with the retained House Music key before delivery.

## Physical acceptance still required

Repeat with **onn. Rugged Speaker, Samsung Buds 3 Pro, VHM-314 board and JBL
earbuds**, for both a file playlist and live radio:

1. With house nodes audible, press Bluetooth Pause and power off/disconnect the
   Bluetooth device. Only the phone should mute; room audio should continue.
2. Reconnect, then press Bluetooth Pause/Play. Play should unmute the phone and
   rejoin the current house audio. Test screen off and app in the background.
3. With the phone as the only audible output, Bluetooth Pause should let the
   server pause; Play with Bluetooth connected should resume the retained
   session. For radio this rejoins live audio, not a saved broadcast position.
4. While the phone is muted, press the app's onscreen Pause/Play and its own
   notification controls. They must affect the whole house and preserve mute.
   After deliberate house Pause, Bluetooth Play must not start the house.
5. Check no speaker leakage after disconnect, manual wired/speaker Unmute,
   phone synchronization, startup from a retained stopped queue, file → radio →
   Resume Playlist, and readable errors. Automated tests are not hardware passes.

If an OEM attributes a Bluetooth event differently, capture the connection log
after reproducing it. No claim of a physical pass on these four devices is made.
