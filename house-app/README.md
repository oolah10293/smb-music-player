# House Music

House Music is the independent Android controller and synchronized phone receiver for the Pi's MPD/Snapcast session. The Pi owns the music files, queue and playback. Folders remain playlists.

Current source: **v0.3.0**, application ID `com.housemusic.player`, Android **8.0 / API 26** or newer. It installs alongside **SMB Music** (`com.smbmusic.player`). Each app has its own settings, service and media session. There is no handoff, shared queue, shared playback position, SMB fallback or app-to-app control.

Build verification and exact artifacts belong in [RELEASE_0.3.0.md](docs/RELEASE_0.3.0.md). The user confirmed swiping and station add/save/play on v0.2.1. This revision's richer details, Last played, and spacing changes still need physical phone acceptance. Earlier focus/startup recovery fixes remain covered by regression tests, with physical acceptance still pending.

## Install and connect

1. Install `HouseMusic-v0.3.0.apk` over the delivered House Music v0.2.1, v0.2.0 or v0.1.1 APK. The retained signing key permits an in-place update that keeps the server address and sync corrections. If still using v0.1.0, first record those values, uninstall only House Music, then install v0.3.0 and re-enter them; v0.1.0 used a different, unavailable CI key. Leave SMB Music installed.
2. Connect the phone to the home network and open **House Music**. Allow its notification permission when requested so the foreground controller is visible.
3. On first launch, enter the Pi's LAN hostname or IPv4 address in **Server**. Enter the host alone, without a port, protocol or folder. No deployment address is bundled. The app uses the house service on port **8787**, checks MPD identity on **6600**, and receives Snapcast audio on **1704**.
4. Browse a folder and select a song or **PLAY LIST**, or open Now Playing to control music already playing in the house. Open **Radio** to add or play an internet station.

No SMB username, password, share path or HOUSE-to-SMB root mapping is required. The Pi uses its existing music library. Radio requires **house-audio-server v0.10.0 or newer**; **Last played requires v0.11.0**. Automatic and pull-down library indexing require v0.9.1 or newer. No S3 firmware update is needed.

Before replacing the combined app, record each phone's synchronization correction. House Music uses a separate settings store and does not import the old app's values. Tap the gear in Now Playing to enter the correction for the current audio route, from −1000 to +1000 ms. The reported **410 ms** and **385 ms** values belong to the user's two tested phones; the record does not identify which phone uses which value. Re-enter each phone's own known value, then recheck sync.

If an older combined app is still installed on this phone, **Quit it before using House Music** so it does not remain a second controller/receiver. House Music cannot close it automatically; the apps are independent.

## Controls and behavior

- Swipe left/right between **Library → Now Playing → Radio**. Page changes preserve the browser position and do not issue playback commands. Vertical scrolling, pull-down refresh, URL/search editing and seeking retain their own gestures.
- A short folder name is centered between MP3s/Rap and Parent Folder. A longer name gets a row beneath them, aligned with the visible left edge of MP3s/Rap; truncation keeps the beginning visible.
- Play/Pause uses confirmed server readiness and controller registration even while the standard media-controller timeline catches up after startup. Explicit Play retries interrupted audio focus on an unmuted phone; manual mute and other apps’ focus ownership are respected.

- Radio accepts a direct HTTP or HTTPS audio stream URL. Tap **Add & Play** to save it on the Pi and start it for the house. The Pi generates a name from station metadata, with a URL-based fallback. A station's website address is not necessarily a playable stream URL.
- Saved stations are shared across House Music controllers. Tap a station to play it; the current station is highlighted. Hold a station to rename it. The row's **×** deletes its saved bookmark without stopping a broadcast that is already playing; the brief **Undo** restores the bookmark.
- Now Playing shows separate station, song, artist and album lines, the supplied broadcast name and bitrate/audio format, and a **LIVE** indication for radio. Details scroll above fixed playback/mute/sync controls. **Last played** shows the previous identified song that accumulated more than ten seconds of playback, with its station and time. The Pi keeps this record even while the app is closed and across server restarts. Missing station metadata is left unavailable. Seeking, Previous/Next, Shuffle, Repeat and queue sorting are unavailable for live radio. Pause stops the upstream stream; Play rejoins the live broadcast. Radio notification controls provide Play/Pause and Quit.
- To return to local music, open the library browser and select a song or **PLAY LIST**. This replaces the shared station with that folder's music; it does not need to restore the old song or queue. Radio selection does not change the MP3s/Rap default for a future fresh passive-node session.
- Pull down on the file list to scan for new music. The visible browser also checks automatically every 30 seconds; the server coalesces automatic scans to at most once per minute and reuses an active scan. Scanning polls stop when the browser leaves the foreground. Search, sort and scroll position survive refresh.
- Browser retains the familiar folder navigation, search/clear, sorting, scroll memory and PLAY LIST behavior. Selecting a track or PLAY LIST changes the shared house queue through the server.
- For local music, Now Playing shows the server's current track and transport. Play/Pause, Previous/Next, Seek, Shuffle, Repeat and explicit queue sort operate on that shared session.
- The Browser **MP3s / Rap** button selects the server's persisted default for a future fresh passive-node session. It does not replace the music currently playing.
- The mute icon remains beside Shuffle and Repeat. It controls only this phone; explicit Unmute works with wired headphones and the phone speaker as well as Bluetooth. Bluetooth connection still unmutes automatically; Bluetooth disconnection or an unplug/noisy event mutes the phone. Play/Pause and unrelated device callbacks preserve the user’s mute choice.
- The gear directly opens the current route’s −1000 to +1000 ms sync adjustment. Holding the mute icon is also supported. It changes only this phone’s timing correction.
- A rejected playback command triggers a fresh queue/state read without replaying the command. Next/Previous/Seek are unavailable while MPD is stopped; Play remains available for a retained queue or selected station. Station writes are not replayed after a connection loss or ambiguous response. Hold the status text for a bounded local diagnostic log.
- While the app is connected, its foreground service maintains controller heartbeats even when the screen is off. The Pi decides pause/resume and final-node behavior from the remaining controllers and renderers.
- Quit stops this phone's receiver, detaches its controller and clears its local presentation. It does not send global Stop or clear the house queue.
- Connection loss leaves House Music reconnecting. Recovery adopts the Pi's current session. It never opens an SMB file, transfers a song or starts SMB Music.

HOUSE availability requires a directly connected physical Wi-Fi/Ethernet route to the configured Pi plus a successful identity check. Actual traffic uses normal Android routing, so Tailscale may remain enabled at home; VPN-only access while away does not qualify.

## Build and source

`house-app` is a separate Gradle root; building it does not build or package SMB Music. See [BUILD_AND_INSTALL.txt](BUILD_AND_INSTALL.txt) and [native/README.md](native/README.md).

The APK bundles Snapclient for `arm64-v8a` and `armeabi-v7a`, built from pinned Snapcast v0.31.0, FLAC 1.4.3 and Boost 1.85.0 sources. Distribute the matching **`HouseMusic-v0.3.0-source.zip`** with the APK: it includes the native sources, build scripts and notices required for the bundled components. License notices also ship in the APK. No license has been selected for the original app code; third-party components retain their own licenses.

The shared product contract is [CENTRAL_PLAYBACK.md](https://github.com/oolah10293/smb-music-player/blob/main/docs/CENTRAL_PLAYBACK.md). App-specific physical checks are in [TESTING.md](docs/TESTING.md), and network/storage details are in [PRIVACY_AND_SECURITY.md](docs/PRIVACY_AND_SECURITY.md). DSP, per-node crossover/volume controls and an always-visible diagnostic mini-log remain separate work.
