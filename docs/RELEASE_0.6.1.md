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

Pending the exact v0.6.1 build: existing JVM regression suite plus station snapshot
reconciliation/validation and physical-LAN eligibility tests; API-26 lint; emulator
playback/navigation and automatic-refresh fixture checks. This section will be
updated with the actual build outcome and signing checks before delivery.
