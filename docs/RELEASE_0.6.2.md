# SMB Music v0.6.2 — controller permissions

Version code 20; package `com.smbmusic.player`; Android 8+.
The user authorized building and testing this correction on 2026-10-20.

## Correction

Media3 1.11.0's deprecated `super.onConnect` returns an internal empty-command
sentinel. Rebuilding it as an accepted result lost the sentinel and granted no
player commands to ordinary new controllers. This disabled file controls and
prevented queue submission even though browsing could work and existing audio
could continue. Radio source switching explicitly refreshed commands, masking
the defect. The user confirmed that playing radio once restored SMB playback.

The service now obtains the normal trusted/untrusted defaults from
`AcceptedResultBuilder(session, controller)`, retaining the existing Garmin
exception and radio command filtering. The bounded connection log records Play
and queue-command grants for app controllers without addresses or filenames.

See [the source diagnosis and earlier emulator evidence](SMB_CONTROLLER_COMMANDS_2026-10-20.md).
No controller-reconnect redesign is included. The exact lifecycle event on the
user's drive has not been observed. The v0.6.1 automatic House bookmark refresh
behavior is retained.

## Verification

Source `f86612d99e901761a1c1f160e186f3d7f4ee4cc2` passed
[Actions run 39497204085](https://github.com/oolah10293/smb-music-player/actions/runs/39497204085)
on 2026-10-20.

- All 33 JVM tests passed with no failures, errors or skips.
- API-26 `NewApi` lint reported no issues; standalone APK exclusion checks passed.
- The API-35 emulator passed the targeted controller, direct HTTP playback and
  automatic station-refresh regression described below.
- Before-radio and after-restart UI dumps both show the retained track and
  enabled Play/Next/Shuffle controls. Service logs show `play=true queue=true`
  for app-controller connections.
- Downloaded APK and check archives matched the Actions artifact SHA-256 digests.

The targeted regression checked a retained paused file queue and enabled
Play/Next/Shuffle before any radio command, toggled Shuffle and verified the
service persisted the changes, then repeated after force-stop/restart. Direct
HTTP playback and House initial, periodic and network-return refresh also passed,
including reconciliation, malformed-response retention, unchanged playback,
GET-only station access and offline cache persistence.

The fixture contains SMB URIs but does not exercise real SMB transfer. Physical
phone acceptance, the extended-drive lifecycle, locked-screen cellular recovery,
Doze and Bluetooth are not established by this run. This is not a passing claim
for the broader navigation/CRUD/Resume Playlist suite.

## APK

`SMBMusic-v0.6.2.apk` is signed with the preserved SMB signing key. Signature
schemes v2 and v3 verify. The APK manifest confirms version 0.6.2, version code 20,
package `com.smbmusic.player`, minimum SDK 26 and target SDK 36. HTTP streams are
permitted. APK contents contain no HOUSE engine package or snapclient payload.

- Size: 11,545,671 bytes.
- APK SHA-256: `b18d64011e4b7eb0e9ee8ccb27f69426f20cf7909a47b8d01e59295266e9a649`.
- Signing certificate SHA-256:
  `7935fde3a71e87e5964399facd15f01d620ad0a6f684442be8e9ce353d10475e`.

The CI artifact uses the runner's temporary debug signature. Use the delivered
APK signed with the preserved key to upgrade the existing app.
