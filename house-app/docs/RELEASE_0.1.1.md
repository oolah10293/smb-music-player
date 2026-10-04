# House Music v0.1.1

Version name **0.1.1**, code **2**, package `com.housemusic.player`, Android **8.0 / API 26+**, target SDK **36**. House Music remains completely independent of SMB Music; the SMB app is unchanged.

## Changes

- Mute is visible on the existing bottom bar beside Shuffle and Repeat. It toggles this phone's output, including wired headphones or speaker without Bluetooth. Existing Bluetooth connect/unmute, disconnect/mute, and noisy/unplug behavior remain. Transport and unrelated audio-route callbacks preserve the manual mute choice.
- The gear on that same bar directly opens route-specific **−1000 to +1000 ms** playback sync. Current Bluetooth and phone/wired corrections are stored separately. The dialog reports requested/applied correction and buffer limits; Apply dismisses only after the service acknowledges the setting. No separate settings menu or new button row was added.
- Pull down on the file list to request an MPD index update and reload. No refresh button was added. The browser also refreshes on return and every 30 seconds while visible; indexing polls every two seconds until finished. Server v0.9.1 coalesces automatic scans across phones for 60 seconds and reuses an active root scan. Search, sort and scroll are retained. An older server can still browse/play and shows a clear update requirement for indexing.
- Rejected commands fetch authoritative queue/state and clear stale errors after successful reads. Pending Media3 operations show confirmed state; failures cannot leave a guessed next track. Writes are not replayed after ambiguous responses. Play remains available for a retained stopped queue; Next/Previous/Seek are disabled while stopped. Quit resolves the outstanding command and releases the phone.
- Hold status text for a bounded local connection log. It records state transitions and error classes/codes without credentials, music paths, or track names. Status can wrap instead of truncating a recovery message to one line.

## Verification

Local `assembleDebug testDebugUnitTest lintDebug --max-workers=2` passes. **31 tests, zero failures**; full Android lint has **zero errors and 106 warnings**. API-26 NewApi checks are included in that full lint run. Both native receiver ABIs (`arm64-v8a`, `armeabi-v7a`) and all Snapcast/FLAC/Boost notices are present. APK v2 signature verification passes. Manifest identity/version and exclusion of SMB transport, the SMB package, and handoff API paths are verified.

Robolectric tests exercise a rejected Next followed by usable Play without recreating the player, a lost response that adopts the actual next track without a duplicate command, and connection loss/recovery. Layout tests keep mute and sync alongside Shuffle and Repeat without overlap at 320, 360 and 411 dp. Policy tests cover wired manual unmute, manual mute retention, Bluetooth connect/disconnect automation, and unchanged device callbacks.

Companion server **v0.9.1**, commit `c57116e1a05bcc42f8e956b99055acbc452dad63`, passes **125 Python tests**. Install it to enable indexing; no S3 firmware update is needed. It adds no cross-app behavior and issues no playback/queue commands for library refresh. MPD may remove queued entries whose underlying files were deleted during an index update.

No phone, headphone jack, Bluetooth receiver, Pi or real SMB filesystem was available for physical acceptance. The user's earlier regression reports are not claimed as new-build passes. Use [TESTING.md](TESTING.md) for the device checks.

## Installation and signing

The v0.1.0 CI debug signing key was not retained. Before installing, record the House server address and each route's correction, then uninstall **only House Music v0.1.0** and install v0.1.1. Re-enter the server address and corrections using the gear. Leave SMB Music installed. The known 410 ms and 385 ms corrections are not mapped to individual phones in the conversation; use each phone's own value.

This delivered APK uses the retained private debug key with certificate SHA-256 `7935fde3a71e87e5964399facd15f01d620ad0a6f684442be8e9ce353d10475e`. Reuse it for later delivered House Music APKs to preserve in-place upgrades. CI generates its own debug key; CI APKs are not interchangeable upgrades unless signed with the retained key. Never commit a private keystore.

Distribute the matching `HouseMusic-v0.1.1-source.zip` with the APK. It contains the independent Gradle project, pinned Snapcast/FLAC/Boost corresponding sources, licenses and native build scripts. This archive is not the SMB app.


## Delivered artifacts

Published implementation commit: `ad5beeb1ed2cbf30ec23ece987dcb1dc24d3d770`. The source archive's `SOURCE_COMMIT.txt` records local build commit `4997bf25286deb23de4d8c79648c0a49566f1c6a`; its tree equals the published implementation tree `1c969ad2a3c07ca5f7d7ec99a26c9d04e10a25d2`. This artifact record is a later documentation-only commit.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| HouseMusic-v0.1.1.apk | 9,414,294 | `5e1d2c773207a3e5324255d2939d8231cbb92ca956cd8b3351d7a989375e766a` |
| HouseMusic-v0.1.1-source.zip | 28,401,796 | `50690986b9e9aa751e45b1c3a8ba325c98b591f9998bab8a5f3b815985894e22` |
