# Android v0.4.1 correction release

This release implements the three corrections recorded after the v0.4.0 phone test. Server v0.8.2 is already installed and healthy; no server code or ESP32 firmware change is required.

- Physical non-VPN Wi-Fi/Ethernet with a direct route to the configured Pi address qualifies home. MPD/service identity checks, HTTP control and Snapcast audio use normal Android routing, allowing Tailscale to remain connected. Physical route loss closes phone output and suspends HOUSE requests/heartbeats.
- Song/PLAY LIST reads transport and audible-output state before the write. A muted phone stays muted if another output was already audible, and auto-unmutes when starting from pause/stop or no audible output. Unknown audibility preserves mute; failed reads abort the write. Explicit Play from pause/stop auto-unmutes. A newer manual mute and uncertain command failures remain authoritative.
- Mute/Unmute is a speaker icon inside the lower Media3 controller strip, hidden in STANDALONE.

## Build and artifacts

Version name **0.4.1**, version code **13**. Exact commit, CI run, artifacts and checksums will be recorded after build completion. Unit coverage includes HOUSE state, physical-route qualification and pre-command mute policy. Both arm64-v8a and armeabi-v7a receivers and license assets must be packaged. The matching source archive includes checksum-pinned native dependencies and build scripts.

## Next physical checkpoint

Follow [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md): first Tailscale-on launch/toggle and silent state adoption, then conditional mute/UI behavior, phone/S3 audio synchronization and controller lifecycle. Test physical route loss with VPN reachability still available, then recovery. No v0.4.1 hardware pass is claimed.

Live home/away same-song handoff remains the following slice, with the existing open grace/queue/idle-return decisions preserved. The confirmed standalone field baseline remains v0.3.8. Historical v0.4.0 evidence and exact artifacts remain in [RELEASE_0.4.0.md](RELEASE_0.4.0.md).
