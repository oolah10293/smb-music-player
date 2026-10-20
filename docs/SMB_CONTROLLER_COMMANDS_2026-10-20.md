# File controls and queue commands unavailable after connecting

## User report

On 2026-10-20 the user reported that, after hours of driving with SMB audio playing,
Now Playing still showed Playing and track metadata but its transport controls were
disabled and its position/duration were 00:00 / 00:00. After closing/reopening,
selecting music produced Nothing queued. Force-stop, phone restart and attempted
reinstallation did not resolve it. The user reported SMB access working in other
apps/devices and browsing working. The installed version was uncertain between
v0.6.0 and v0.6.1; both include radio.

The supplied connection-log screenshot contains service creation, an earlier Quit,
another service creation and a browser-start Tailscale request. It contains no
playback error and no controller/queue diagnostics. Absence of those diagnostics
does not establish that a queue request reached the service or that a controller
disconnected.

## Verified source defect

`PlaybackService.SessionCallback.onConnect` calls `super.onConnect`, copies its
player commands into a new accepted result and discards its session extras.
The pinned dependency is Media3 **1.11.0**. In that exact tag:

- [`MediaSession.java`](https://github.com/androidx/media/blob/1.11.0/libraries/session/src/main/java/androidx/media3/session/MediaSession.java)
  implements deprecated `Callback.onConnect` by returning a sentinel with empty
  player/session commands and a `BUNDLE_KEY_NOT_IMPLEMENTED` extra.
- [`MediaSessionImpl.java`](https://github.com/androidx/media/blob/1.11.0/libraries/session/src/main/java/androidx/media3/session/MediaSessionImpl.java)
  recognizes that extra and dispatches to `onConnectAsync`. Without the extra, it
  accepts the returned result as final.
- SMB's rebuilt result therefore grants no player commands to ordinary new
  controllers. Its own radio custom commands are still added. The Garmin branch
  explicitly grants standard commands and does not copy the sentinel.
- [`MediaControllerImplBase.java`](https://github.com/androidx/media/blob/1.11.0/libraries/session/src/main/java/androidx/media3/session/MediaControllerImplBase.java)
  returns from `setMediaItems` without sending the queue when
  `COMMAND_CHANGE_MEDIA_ITEMS` is unavailable.

The existing `refreshControllerCommands()` explicitly grants standard commands to
the app's controllers during radio/file source switches. A controller created after
that switch instead receives the defective initial grant. This explains a mechanism
for functioning playback followed by disabled controls when screens reconnect,
and for persistent failed file selections after fresh starts. The exact lifecycle
event on the user's drive has not been observed.

## Existing emulator evidence and test gap

The retained artifacts from Actions run
[39156997852](https://github.com/oolah10293/smb-music-player/actions/runs/39156997852)
show `exo_play_pause`, Next and Shuffle disabled in `files-now-playing.xml` before
the first radio command. That test only checked navigation at this stage, then
started radio before testing playback and Resume Playlist. The source-switch grant
masked the initial connection defect. These artifacts are evidence from the old
build, not a test of the proposed fix.

## Prepared source correction

- Construct the trusted/untrusted baseline with
  `ConnectionResult.AcceptedResultBuilder(session, controller).build()` instead of
  copying the deprecated superclass sentinel. Keep the Garmin exception and
  radio-specific command filtering.
- Record the app controller's Play and queue-command grants in the existing
  bounded connection log, without filenames, addresses or credentials.
- Extend the emulator smoke script to require a visible retained file queue and
  an enabled Play control on first connection and after force-stop/restart,
  **before any radio command**. The fixture does not prove real SMB playback.

The user authorized building and testing the corrected APK on 2026-10-20 after
confirming that the temporary radio workaround restored SMB playback. Version
0.6.2 (code 20) contains this correction. Android build and emulator results will
be recorded in `RELEASE_0.6.2.md` after validation.

The earlier missing-disconnect-reconnect finding remains a separate resilience
gap. It is not evidence that a disconnection caused this particular report, and
reconnect changes are not included in this focused permissions correction.

## User-confirmed temporary recovery

Play a saved radio station once, then return to Browse and select the SMB track.
The source-switch path explicitly restores controller commands. The user had no
saved stations, was given a direct Radio Paradise stream URL, and confirmed
"that did fix smb playback." This supports the source diagnosis without proving
the exact lifecycle event during the drive. Merely opening the Radio page does
not run that path.
