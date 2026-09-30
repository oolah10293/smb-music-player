# Android v0.4.0 / server v0.8.2 release — 2026-09-29

The first Android HOUSE build and approved Browser polish are available for the combined phone/S3 checkpoint. The APK was retrieved from the successful final CI run and delivered as `SMBMusicPlayer-v0.4.0.apk`. Installation has now been exercised on the real phone; the first field pass found a HOUSE/Tailscale transport defect plus mute/UI corrections before the synchronized-audio checkpoint.

## Exact release builds

| Component | Source commit | Build evidence | Field status |
| --- | --- | --- | --- |
| Android v0.4.0 | [9c89b24](https://github.com/oolah10293/smb-music-player/commit/9c89b246d98f316eef3b402ab7e09f0d9343d809) | [CI passed](https://github.com/oolah10293/smb-music-player/actions/runs/36614059665): debug APK, 3 state tests, both native receiver ABIs and license assets | Installed; HOUSE state adoption works with Tailscale off after MPD LAN bind fix. Tailscale-on transport defect and mute/UI corrections found; synchronized audio pending |
| Server v0.8.2 | [9c98973](https://github.com/oolah10293/house-audio-server/commit/9c989737b2a3d3ddb672fb8d34313932f47871c4) | [CI passed](https://github.com/oolah10293/house-audio-server/actions/runs/36613448675); 90 server tests | Installed on Pi; health/startup good and current Rap session/passive S3 observed |
| ESP32 | Existing firmware | Two S3/PCM5102A outputs previously heard playing in sync | No firmware or wiring change required; Android/S3 synchronization is a separate pending check |

Android commit `9c89b24` includes the initial implementation in `f0171da` plus the heartbeat recovery fix: successful renewal after a temporary failure restores HOUSE controls without Quit/reopen or waiting for lease expiry.

## Downloads and identity

- [APK ZIP: SMBMusicPlayer-debug](https://github.com/oolah10293/smb-music-player/actions/runs/36614059665/artifacts/11054647608). Extract `app-debug.apk`; the delivered `SMBMusicPlayer-v0.4.0.apk` is the same file renamed.
- [Matching source: SMBMusicPlayer-v0.4.0-source](https://github.com/oolah10293/smb-music-player/actions/runs/36614059665/artifacts/11053843947). This contains `SMBMusicPlayer_v0_4_0_source.zip`, including corresponding receiver source, pinned dependencies, build files and licenses. Keep it with the APK when distributing this build.

The downloaded ZIP hashes match GitHub's artifact digests. The APK package contains `libsnapclient.so` for `arm64-v8a` and `armeabi-v7a`, plus Snapcast, FLAC and Boost license assets.

| File | SHA-256 |
| --- | --- |
| Extracted APK (`app-debug.apk` / `SMBMusicPlayer-v0.4.0.apk`, 12,889,491 bytes) | `3f10bb35e827ac518b2df51a8c8ddee3f284079fb0a352f49f8e9a993867ea2d` |
| GitHub APK artifact ZIP | `00eda6f49b90f41014af549b2780144f94117763e327b9f509c9afc98b863c0e` |
| GitHub source artifact ZIP (outer download) | `160e5161588dd8131233c24f87a671869c4c54b9f3d39b5d4e40b890a68a6faf` |

## Included behavior

- Existing Browser/Now Playing screens and Media3 controls target the selected backend. STANDALONE retains the SMB/Media3 engine.
- Normal app startup probes the configured house address through a non-VPN Wi-Fi/Ethernet network before standalone startup. In this delivered build, HOUSE HTTP/audio are also pinned to that Android `Network`; field testing showed this transport choice fails when Tailscale is enabled and must be revised.
- HOUSE adopts the Pi queue/state and starts with phone output muted. The delivered build currently unmutes for song selection / PLAY LIST, but that behavior is superseded by field feedback: starting/replacing a playlist must preserve local mute. Explicit Play from pause/stop may still unmute; browsing, sorting and Next/Previous during playback do not.
- Bundled upstream Snapclient 0.31.0 receives the existing FLAC stream. Local mute/audio interruptions affect phone output; HOUSE Quit detaches the phone without MPD Stop/Clear.
- Service-owned controller presence uses five-second heartbeats and fifteen-second expiry, with reattachment after server restart and no replay of uncertain transport writes.
- Browser rows align with Search; the path shows only the current folder; SMB/Parent Folder and PLAY LIST/Sort are swapped. HOUSE adds the persisted MP3s/Rap selector. The delivered mute control placement is not accepted: Mute/Unmute must live in the lower Media3 control strip alongside transport, Shuffle/Repeat, and track time.
- Server `/queue/reorder` checks the queue revision and existing MPD IDs, then reorders without restarting/seeking the current track or changing transport, Shuffle/Repeat, automatic-pause ownership or pending drain.

## Installation and next checkpoint

1. Update the Pi control service to v0.8.2 using its existing checkout/install procedure. A service restart ends the old session under the settled restart rule; an already-powered passive radio may start a fresh configured default.
2. Install the APK. In the existing SMB connection panel, enter the Pi's LAN address in the optional House server field, save, Quit and reopen. Keep the existing SMB settings. Allow notifications for the foreground controller.
3. Follow [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md): silent opening; phone/S3 synchronization; local mute; Sort while playing/paused; background controller presence; muted-only pause/audible-return resume; heartbeat recovery; and Quit leaving other listeners playing.

No new hardware pass is recorded by this release handoff. v0.3.8 remains the confirmed standalone phone baseline; v0.8.1 remains the confirmed Pi baseline, including fresh Rap startup after restart with an S3 already powered. The separate all-radios-off restart check remains pending.

## Following work

Live home/away handoff, last-heard-position SMB continuation, return-to-idle-house behavior and cold external-media-browser mode selection remain later work. Departure grace and whole-queue continuation choices remain open. This build selects its backend on normal launch and stays HOUSE/reconnecting after a HOUSE outage. Server artwork is unavailable, so HOUSE uses text metadata and filename fallback.

Complete phone/S3 acceptance, then the agreed recovery slice and final standalone/vehicle regressions, including prolonged SMB outage recovery. Neither tracking issue is complete yet.


## Field findings after release

The first real-phone pass established:

- MPD on the Pi was initially listening only on loopback. HOUSE discovery could not work until MPD also listened on the configured LAN interface/address.
- After that server configuration fix, Android v0.4.0 entered HOUSE with Tailscale off and Now Playing displayed the track already playing on MPD.
- Turning Tailscale on caused HOUSE app updates to stop, while the phone browser could still read the Pi's HTTP health JSON with Tailscale on or off.
- The corrective design is to use a real non-VPN Wi-Fi/Ethernet network and its directly connected routes for **physical-home qualification/departure detection**, while normal Android routing carries MPD/HTTP/Snapcast traffic. Tailscale may remain connected; VPN reachability alone still does not qualify as home.
- Playlist selection must not change local phone mute. A muted phone stays muted when a song or PLAY LIST starts/replaces the house queue.
- Mute/Unmute belongs in the lower Media3 control strip, not as a separate standalone button.

These items define the follow-up correction to v0.4.0. The phone/S3 synchronization result remains unproven until that correction is built and tested.
