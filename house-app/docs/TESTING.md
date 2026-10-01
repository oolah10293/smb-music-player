# House Music v0.1.0 physical acceptance

All checks below are **pending on the new independent APK**. Record phone model, Android version, output route, server version and the APK version with results. Prior combined-app observations are preserved in [HOUSE_VALIDATION.md](../../docs/HOUSE_VALIDATION.md), not promoted to new-build passes.

## First phone pass

1. Install beside SMB Music, Quit the old combined app, and open House Music on home Wi-Fi. Confirm the name is **House Music**, the Pi address is correct and folders load without SMB credentials. Repeat with Tailscale enabled.
2. With music already playing on an S3, open Now Playing. Confirm it adopts the same song/queue without a restart. Select a song and use Play/Pause, Next and Seek; verify the S3 follows the shared session.
3. Connect Bluetooth. Confirm the phone joins the same stream, then hold the output icon, enter that phone's prior timing correction and compare phone/S3 synchronization. Confirm the correction remains after Quit/reopen and that changing it does not restart the server's song.
4. Mute the phone, Pause and Resume the house while S3 nodes are audible. Confirm the phone stays muted. Disconnect Bluetooth; the phone must remain silent and other audible nodes must continue under server policy.
5. Quit House Music while an S3 is playing. Confirm the phone stops, its notification disappears and the S3 continues. Reopen: current server state must be fetched before a track is presented.

The existing Bluetooth requirement for manual Unmute is expected in v0.1.0. The requested wired/headphone override remains deferred; do not mark it fixed by the split.

## Independence and reconnect

- Leave House Music connected and muted. Start, pause and Quit SMB Music; the house queue and transport must not change through an app-specific command or transfer. Android's ordinary audio-focus behavior is separate from the app boundary.
- Change or remove the home Wi-Fi route. House Music should show unavailable/reconnecting, never open SMB Music or play a private copy. On returning home, it must adopt the Pi's current song/position without replaying an old queue.
- Cellular/Tailscale access alone must not qualify HOUSE. Tailscale enabled on the correct home LAN must continue to work.
- Briefly interrupt HTTP/control access and restore it; retry should refresh state and renew/reacquire the controller lease without duplicating Next or a queue write. Test an audio-only interruption separately if available; compare against the current live stream after recovery.
- Change the configured host and save. Old callbacks and folders must not appear as results from the new server.

## Browser and shared queue

- Search remains 40dp, opens the keyboard only when the text area is tapped, and X clears without opening a hidden keyboard. Test multiple search terms and the filtered PLAY LIST.
- Confirm Browser and Now Playing sort labels/settings agree, folder navigation preserves scroll, metadata falls back to filename, and both screens stay portrait.
- Browser sorting changes the view. Deliberately sorting the active queue preserves its current song, position and transport state.
- Tap MP3s/Rap. Confirm the setting survives reconnect and does not replace current playback. Its effect belongs to the next fresh passive-node session.
- Opening an idle house controller must not start playback or replace its queue. Starting a passive S3 follows the server's normal default rules.

## Background and node lifecycle

- Keep the screen off long enough to cover several five-second heartbeats and the fifteen-second server expiry window. The phone must remain a controller while its service is active.
- With the phone muted, remove the last audible S3. Confirm the server automatically pauses and retains the queue/position. Return an eligible audible node and confirm retained-session resume.
- With the phone as the last controller in an automatically paused session, Quit and confirm server cleanup follows the existing session contract.
- Reconnect with Bluetooth already attached, both during active playback and during deliberate Pause/Stop. It must recognize the current route without inventing Play intent.
- Exercise lock-screen/media controls and incoming audio-focus interruptions. Local renderer interruption must not masquerade as a global Pause/Stop button press.
- Run the same launch/basic output checks on the S8 and, if used, the Android 8 J3. API compatibility lint alone is insufficient hardware evidence.

Build/test/lint and artifact evidence belongs in [RELEASE_0.1.0.md](RELEASE_0.1.0.md). Do not revive cross-app handoff tests: transfer was removed from the product.
