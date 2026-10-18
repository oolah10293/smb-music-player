# SMB Music v0.6.1 — automatic House station refresh

The user authorized a corrected build after v0.6.0 shipped without House stations,
then explicitly corrected the proposed one-time import: **refresh automatically
when on the home network**. This supersedes the optional/excluded import language
in the original plan. Version code 19; package `com.smbmusic.player`; Android 8+.

## Behavior

- Read `GET /radio/stations` on app/service start, entering Radio, returning to a
  qualifying physical home LAN, pulling down Radio, and every minute while the UI
  or playback service is alive. The timer is best effort; no background wake job is
  added to keep a quit/stopped app running merely for bookmarks.
- Require physical non-VPN Wi-Fi/Ethernet and a directly connected route covering
  the configured Pi address. Verify the response's `house-audio-server` identity.
  Use normal Android routing after qualification, preserving House's established
  Tailscale-on behavior. VPN-only reachability does not qualify as home.
- Use any retained old House connection setting, otherwise try the saved SMB host
  as the initial candidate (the project Pi serves Samba). Tap the House status on
  Radio to set/change the Pi LAN address. A Tailscale-only SMB address needs its LAN
  address entered here. Blank explicitly disables refresh. No Import button.
- Reconcile House additions, names, URL updates and deletions. Keep phone-created
  stations, explicit phone aliases, stable IDs and phone listening totals. URLs
  deduplicate. Deleting a cached House row hides it locally across refreshes; Undo
  unhides it. Do not import historical listening totals from House.
- Save a complete validated snapshot atomically with local entries. Timeout,
  wrong-service, malformed and oversized responses retain the last good list.
  Leaving the LAN or changing the server discards stale in-flight results.
- Cached streams play directly from the phone away from home. Refresh does not
  switch the current station, register a HOUSE controller, write station changes
  back, alter HOUSE playback or require a Pi/server/firmware update.

The v0.6.0 combined recovery/radio/swipe/Resume Playlist implementation is retained.
Cellular-outage recovery still requires physical locked-phone acceptance; issue #7
remains open.

## Verification

Built from `07b570aa7e445d0ef9e9ebec8b74e59ddb3b55bf` in
[Actions run 39160808286](https://github.com/oolah10293/smb-music-player/actions/runs/39160808286),
which completed successfully on 2026-10-18.

- All 33 JVM tests passed, including snapshot reconciliation, invalid-response
  retention, local overrides/deletion and physical-LAN qualification.
- API-26 `NewApi` lint passed with no issues; standalone APK checks found no HOUSE
  engine package or snapclient payload.
- The targeted API-35 emulator test passed: direct HTTP playback; initial refresh;
  automatic one-minute additions, renames, URL changes and removals without a
  refresh gesture; retained playback selection and intent; malformed-response
  retention; no new rows while Wi-Fi was disabled; refresh after Wi-Fi returned;
  GET-only `/radio/stations` access; and cache persistence after app restart with
  the fixture server unavailable. The test uses local fixtures, not the user's Pi.
- Earlier full UI runs did not complete reliably. Failures included emulator
  dialog-input/launcher interference and station selection after Resume Playlist.
  The final run isolates the requested refresh feature; it is **not** a passing
  full navigation/CRUD/Resume Playlist regression run. Physical-device
  reproduction of the selection failure has not been established.
- Physical home-LAN/Tailscale operation, locked-screen cellular-outage recovery,
  Doze and Bluetooth remain unverified for this build.

## Delivered APK

`SMBMusic-v0.6.1.apk` was signed with the preserved SMB signing key and verified
with APK signature schemes v2 and v3. It keeps package `com.smbmusic.player`,
version code 19, minimum SDK 26 and target SDK 36.

- Size: 11,545,671 bytes.
- APK SHA-256: `6e0b1e154bec892022d2b7f6570ed41e1ff3fb4bcca099d59476d041443cbf29`.
- Signing certificate SHA-256:
  `7935fde3a71e87e5964399facd15f01d620ad0a6f684442be8e9ce353d10475e`.

The CI artifact carries the runner's temporary debug signature. Install the
delivered APK signed with the preserved key when upgrading the existing app.
