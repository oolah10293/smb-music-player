# House Music

House Music is the independent Android controller and synchronized phone receiver for the Pi's MPD/Snapcast session. The Pi owns the music files, queue and playback. Folders remain playlists.

Current source: **v0.1.1**, application ID `com.housemusic.player`, Android **8.0 / API 26** or newer. It installs alongside **SMB Music v0.5.1** (`com.smbmusic.player`). Each app has its own settings, service and media session. There is no handoff, shared queue, shared playback position, SMB fallback or app-to-app control.

Build verification and exact artifacts belong in [RELEASE_0.1.1.md](docs/RELEASE_0.1.1.md). This update has **not yet passed physical phone testing**; the older combined app's S8 and synchronization results are historical evidence only.

## Install and connect

1. Record the House server address and each route’s sync correction, uninstall House Music v0.1.0, then install `HouseMusic-v0.1.1.apk`. The previous CI signing key is unavailable, so this release cannot upgrade v0.1.0 in place. SMB Music remains installed.
2. Connect the phone to the home network and open **House Music**. Allow its notification permission when requested so the foreground controller is visible.
3. On first launch, enter the Pi's LAN hostname or IPv4 address in **Server**. Enter the host alone, without a port, protocol or folder. No deployment address is bundled. The app uses the house service on port **8787**, checks MPD identity on **6600**, and receives Snapcast audio on **1704**.
4. Browse a folder and select a song or **PLAY LIST**, or open Now Playing to control music already playing in the house.

No SMB username, password, share path or HOUSE-to-SMB root mapping is required. The Pi uses its existing music library. Install server **v0.9.1** for automatic and pull-down library indexing. Playback controls still work with v0.9.0, but its browser cannot request an index update. No S3 firmware update is needed.

Before replacing the combined app, record each phone's synchronization correction. House Music uses a separate settings store and does not import the old app's values. Tap the gear in Now Playing to enter the correction for the current audio route, from −1000 to +1000 ms. The reported **410 ms** and **385 ms** values belong to the user's two tested phones; the record does not identify which phone uses which value. Re-enter each phone's own known value, then recheck sync.

If an older combined app is still installed on this phone, **Quit it before using House Music** so it does not remain a second controller/receiver. House Music cannot close it automatically; the apps are independent.

## Controls and behavior

- Pull down on the file list to scan for new music. The visible browser also checks automatically every 30 seconds; the server coalesces automatic scans to at most once per minute and reuses an active scan. Scanning polls stop when the browser leaves the foreground. Search, sort and scroll position survive refresh.
- Browser retains the familiar folder navigation, search/clear, sorting, scroll memory and PLAY LIST behavior. Selecting a track or PLAY LIST changes the shared house queue through the server.
- Now Playing shows the server's current track and transport. Play/Pause, Previous/Next, Seek, Shuffle, Repeat and explicit queue sort operate on that shared session.
- The Browser **MP3s / Rap** button selects the server's persisted default for a future fresh passive-radio session. It does not replace the music currently playing.
- The mute icon remains beside Shuffle and Repeat. It controls only this phone; explicit Unmute works with wired headphones and the phone speaker as well as Bluetooth. Bluetooth connection still unmutes automatically; Bluetooth disconnection or an unplug/noisy event mutes the phone. Play/Pause and unrelated device callbacks preserve the user’s mute choice.
- The gear directly opens the current route’s −1000 to +1000 ms sync adjustment. Holding the mute icon is also supported. It changes only this phone’s timing correction.
- A rejected playback command triggers a fresh queue/state read without replaying the command. Next/Previous/Seek are unavailable while MPD is stopped; Play remains available for a retained queue. Hold the status text for a bounded local diagnostic log.
- While the app is connected, its foreground service maintains controller heartbeats even when the screen is off. The Pi decides pause/resume and final-node behavior from the remaining controllers and renderers.
- Quit stops this phone's receiver, detaches its controller and clears its local presentation. It does not send global Stop or clear the house queue.
- Connection loss leaves House Music reconnecting. Recovery adopts the Pi's current session. It never opens an SMB file, transfers a song or starts SMB Music.

HOUSE availability requires a directly connected physical Wi-Fi/Ethernet route to the configured Pi plus a successful identity check. Actual traffic uses normal Android routing, so Tailscale may remain enabled at home; VPN-only access while away does not qualify.

## Build and source

`house-app` is a separate Gradle root; building it does not build or package SMB Music. See [BUILD_AND_INSTALL.txt](BUILD_AND_INSTALL.txt) and [native/README.md](native/README.md).

The APK bundles Snapclient for `arm64-v8a` and `armeabi-v7a`, built from pinned Snapcast v0.31.0, FLAC 1.4.3 and Boost 1.85.0 sources. Distribute the matching **`HouseMusic-v0.1.1-source.zip`** with the APK: it includes the native sources, build scripts and notices required for the bundled components. License notices also ship in the APK. No license has been selected for the original app code; third-party components retain their own licenses.

The shared product contract is [CENTRAL_PLAYBACK.md](https://github.com/oolah10293/smb-music-player/blob/main/docs/CENTRAL_PLAYBACK.md). App-specific physical checks are in [TESTING.md](docs/TESTING.md), and network/storage details are in [PRIVACY_AND_SECURITY.md](docs/PRIVACY_AND_SECURITY.md). DSP, per-node crossover/volume controls, internet radio and an always-visible diagnostic mini-log remain separate work.
