# House Music

House Music is the independent Android controller and synchronized phone receiver for the Pi's MPD/Snapcast session. The Pi owns the music files, queue and playback. Folders remain playlists.

Current source: **v0.4.1**, application ID `com.housemusic.player`, Android **8.0 / API 26** or newer. It installs alongside **SMB Music** (`com.smbmusic.player`). Each app has its own settings, service and media session. There is no handoff, shared queue, shared playback position, SMB fallback or app-to-app control.

The full radio update is documented in [RELEASE_0.4.0.md](docs/RELEASE_0.4.0.md): Resume Playlist, stable station ordering during each Radio visit, and radio Play/Pause aligned with Mute and the sync gear. It retains the native pager from v0.3.1; the user reported the swipe feel is good. The user confirmed Resume Playlist worked after the server v0.13.1 seek-readiness fix; detailed remaining radio checks stay in the test record.

The [v0.4.1 update](docs/RELEASE_0.4.1.md) makes Bluetooth media controls local to the phone and improves playback errors. Its hardware acceptance is pending.

## Install and connect

1. Install `HouseMusic-v0.4.1.apk` over the delivered House Music v0.4.0, v0.3.1, v0.3.0, v0.2.1, v0.2.0 or v0.1.1 APK. The retained signing key permits an in-place update that keeps the server address and sync corrections. If still using v0.1.0, first record those values, uninstall only House Music, then install v0.4.1 and re-enter them; v0.1.0 used a different, unavailable CI key. Leave SMB Music installed.
2. Connect the phone to the home network and open **House Music**. Allow its notification permission when requested so the foreground controller is visible.
3. On first launch, enter the Pi's LAN hostname or IPv4 address in **Server**. Enter the host alone, without a port, protocol or folder. No deployment address is bundled. The app uses the house service on port **8787**, checks MPD identity on **6600**, and receives Snapcast audio on **1704**.
4. Browse a folder and select a song or **PLAY LIST**, or open Now Playing to control music already playing in the house. Open **Radio** to add or play an internet station.

No SMB username, password, share path or HOUSE-to-SMB root mapping is required. The Pi uses its existing music library. Radio requires **house-audio-server v0.10.0 or newer**; **Last played requires v0.11.0**, and **Resume Playlist requires v0.13.0; keep v0.13.1 for the confirmed seek-readiness fix**. Automatic and pull-down library indexing require v0.9.1 or newer. No S3 firmware update is needed.

Before replacing the combined app, record each phone's synchronization correction. House Music uses a separate settings store and does not import the old app's values. Tap the gear in Now Playing to enter the correction for the current audio route, from −1000 to +1000 ms. The reported **410 ms** and **385 ms** values belong to the user's two tested phones; the record does not identify which phone uses which value. Re-enter each phone's own known value, then recheck sync.

If an older combined app is still installed on this phone, **Quit it before using House Music** so it does not remain a second controller/receiver. House Music cannot close it automatically; the apps are independent.

## Controls and behavior

- Swipe left/right between **Library → Now Playing → Radio**. Page changes preserve the browser position and do not issue playback commands. Vertical scrolling, pull-down refresh, URL/search editing and seeking retain their own gestures.
- A short folder name is centered between MP3s/Rap and Parent Folder. A longer name gets a row beneath them, aligned with the visible left edge of MP3s/Rap; truncation keeps the beginning visible.
- Play/Pause uses confirmed server readiness and controller registration even while the standard media-controller timeline catches up after startup. Explicit Play retries interrupted audio focus on an unmuted phone; manual mute and other apps’ focus ownership are respected.

- Radio accepts a direct HTTP or HTTPS audio stream URL. Tap **Add & Play** to save it on the Pi and start it for the house. The Pi generates a name from station metadata, with a URL-based fallback. A station's website address is not necessarily a playable stream URL.
- On entering Radio, stations use the Pi’s total listening-time ranking (highest first, alphabetical ties). Rows stay in place while the page remains open, including pull-down refresh. A newly added station appears at the top until you leave and return. Rename and delete/Undo preserve row placement during that visit.
- Saved stations are shared across House Music controllers. Tap a station to play it; the current station is highlighted. Hold a station to rename it. The row's **×** deletes its saved bookmark without stopping a broadcast that is already playing; the brief **Undo** restores the bookmark.
- Now Playing shows separate station, song, artist and album lines, the supplied broadcast name and bitrate/audio format, and a **LIVE** indication for radio. Details scroll above fixed playback/mute/sync controls. **Last played** shows the previous identified song that accumulated more than ten seconds of playback, with its station and time. The Pi keeps this record even while the app is closed and across server restarts. Missing station metadata is left unavailable. Seeking, Previous/Next, Shuffle, Repeat and queue sorting are unavailable for live radio. Pause stops the upstream stream; Play rejoins the live broadcast. Radio notification controls provide Play/Pause and Quit.
- During radio playback, **Resume Playlist** appears beside Quit when the Pi has a saved playlist. It restores the Pi’s previous queue, track, position and playback modes. The button hides when no saved playlist exists and is disabled while a request is running. The app sends one request and refreshes state after failure; it never reconstructs the queue or automatically retries. Phone mute stays as chosen. Selecting a song or **PLAY LIST** in the library instead replaces the shared station with that folder’s music. Radio selection does not change the MP3s/Rap default for a future fresh passive-node session.
- Pull down on the file list to scan for new music. The visible browser also checks automatically every 30 seconds; the server coalesces automatic scans to at most once per minute and reuses an active scan. Scanning polls stop when the browser leaves the foreground. Search, sort and scroll position survive refresh.
- Browser retains the familiar folder navigation, search/clear, sorting, scroll memory and PLAY LIST behavior. Selecting a track or PLAY LIST changes the shared house queue through the server.
- For local music, Now Playing shows the server's current track and transport. Play/Pause, Previous/Next, Seek, Shuffle, Repeat and explicit queue sort operate on that shared session.
- The Browser **MP3s / Rap** button selects the server's persisted default for a future fresh passive-node session. It does not replace the music currently playing.
- Radio Play/Pause is centered on the same row as Mute and the sync gear; file playback keeps its existing layout. The mute icon remains beside Shuffle and Repeat for files. It controls only this phone; explicit Unmute works with wired headphones and the phone speaker as well as Bluetooth. Bluetooth connection still unmutes automatically; Bluetooth disconnection or an unplug/noisy event mutes the phone. Play/Pause and unrelated device callbacks preserve the user’s mute choice.
- Bluetooth Pause/Stop mutes this phone; Bluetooth Play unmutes only with a current Bluetooth output. Other audible house nodes continue. If this was the only audible output, the server automatically pauses and later resumes its retained session when an output returns. A deliberate house Pause is not overridden by Bluetooth Play. The app’s onscreen and own notification Play/Pause still control the whole house; the headset sees local muted/paused state.
- The gear directly opens the current route’s −1000 to +1000 ms sync adjustment. Holding the mute icon is also supported. It changes only this phone’s timing correction.
- A rejected playback command displays its action and error detail, retaining it across routine reads until the next house command. An uncertain playlist restore includes the server’s reason. Connection problems take priority. A fresh queue/state read follows a failed command without replaying it. Next/Previous/Seek are unavailable while MPD is stopped; Play remains available for a retained queue or selected station. Station writes are not replayed after a connection loss or ambiguous response. Hold the status text for a bounded local diagnostic log.
- While the app is connected, its foreground service maintains controller heartbeats even when the screen is off. The Pi decides pause/resume and final-node behavior from the remaining controllers and renderers.
- Quit stops this phone's receiver, detaches its controller and clears its local presentation. It does not send global Stop or clear the house queue.
- Connection loss leaves House Music reconnecting. Recovery adopts the Pi's current session. It never opens an SMB file, transfers a song or starts SMB Music.

HOUSE availability requires a directly connected physical Wi-Fi/Ethernet route to the configured Pi plus a successful identity check. Actual traffic uses normal Android routing, so Tailscale may remain enabled at home; VPN-only access while away does not qualify.

## Build and source

`house-app` is a separate Gradle root; building it does not build or package SMB Music. See [BUILD_AND_INSTALL.txt](BUILD_AND_INSTALL.txt) and [native/README.md](native/README.md).

The APK bundles Snapclient for `arm64-v8a` and `armeabi-v7a`, built from pinned Snapcast v0.31.0, FLAC 1.4.3 and Boost 1.85.0 sources. Distribute the matching **`HouseMusic-v0.4.1-source.zip`** with the APK: it includes the native sources, build scripts and notices required for the bundled components. License notices also ship in the APK. No license has been selected for the original app code; third-party components retain their own licenses.

The shared product contract is [CENTRAL_PLAYBACK.md](https://github.com/oolah10293/smb-music-player/blob/main/docs/CENTRAL_PLAYBACK.md). App-specific physical checks are in [TESTING.md](docs/TESTING.md), and network/storage details are in [PRIVACY_AND_SECURITY.md](docs/PRIVACY_AND_SECURITY.md). DSP, per-node crossover/volume controls and an always-visible diagnostic mini-log remain separate work.

