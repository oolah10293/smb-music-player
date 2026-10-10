# House Music v0.2.0

Version name **0.2.0**, code **3**, package `com.housemusic.player`, Android **8.0 / API 26+**, target SDK **36**. This release adds the House Music Radio page. SMB Music is unchanged and remains an independent app.

## Radio

- Open **Radio** from the library browser. Paste a direct HTTP or HTTPS audio stream URL and tap **Add & Play**. The Pi saves the station, generates its name from stream metadata or a URL-based fallback, and starts the shared house stream. A station's web page is not necessarily a stream URL.
- Saved stations live on the Pi and appear for all House Music controllers. Tap a station to play it. The current station is highlighted. Hold a station to rename it.
- Each row has an **×** to remove its bookmark. Deleting the current station leaves its broadcast playing. A brief **Undo** restores the bookmark without switching playback.
- Now Playing identifies radio as **LIVE**, shows the station and available broadcast metadata, and disables seeking, Previous/Next, Shuffle, Repeat and queue sorting. Radio notification controls offer Play/Pause and Quit. The existing phone mute and sync gear remain available.
- Pause disconnects the upstream radio stream. Play rejoins the current live broadcast. Reconnection adopts the server's current state instead of replaying an old station-selection command.
- To return to local music, open the library browser and choose a song or **PLAY LIST**. That replaces the shared station with the selected local music. The previous song or queue does not need to be restored. The MP3s/Rap passive startup default remains independent of station selection.

Radio uses **house-audio-server v0.10.0 or newer**. The Pi remains the sole source and queue owner: MPD feeds Snapcast, including the phone receiver. The app does not decode internet stations independently. No S3 firmware update is required.

## Verification

Build, automated test, lint, signature and artifact checks are pending the final build. Record the completed results here before distributing the APK. The expected native receiver ABIs remain `arm64-v8a` and `armeabi-v7a`, with Snapcast, FLAC and Boost notices and matching sources.

The user installed server **v0.10.0** and confirmed hearing Radio Paradise, hearing WXDX, and returning to local music through the existing House Music app. These are server integration results; they do not establish acceptance of the v0.2.0 Radio page, station editing/deletion, notification changes, pause/retry behavior or hardware synchronization. The server-side field record is [house-audio-server PR #8](https://github.com/oolah10293/house-audio-server/pull/8#issuecomment-6098027152).

No physical phone is available for this app build. All v0.2.0 phone checks in [TESTING.md](TESTING.md) remain pending until results are reported. An automated pass does not establish S8/J3 compatibility or audible phone/S3 synchronization.

## Installation and signing

Install the delivered `HouseMusic-v0.2.0.apk` over the delivered **v0.1.1** APK. Both use the retained private debug key, so the update preserves the House server address, route-specific sync corrections and other app settings. Leave SMB Music installed.

The signing certificate SHA-256 is `7935fde3a71e87e5964399facd15f01d620ad0a6f684442be8e9ce353d10475e`. CI uses its own debug key; a CI APK must be signed with the retained key before it can serve as an in-place delivered update. Never commit or include the private keystore in a source archive.

House Music **v0.1.0** used a different CI key that was not retained. If upgrading from that version, record the server address and each route's sync correction, uninstall only House Music, install v0.2.0, and re-enter those values. The historical 410 ms and 385 ms corrections are not mapped to individual phones in the conversation; use each phone's own known correction.

## Artifacts and source

Distribute `HouseMusic-v0.2.0-source.zip` with `HouseMusic-v0.2.0.apk`. The source archive contains this independent Gradle project, pinned Snapcast/FLAC/Boost corresponding sources, license notices and native build scripts. It is not the SMB Music app.

Artifact sizes, SHA-256 checksums and the implementation commit are pending the final build and publication. Record them here once those artifacts are fixed.
