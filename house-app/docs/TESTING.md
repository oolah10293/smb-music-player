# House Music v0.1.1 physical acceptance

All checks below are **pending on the new independent APK**. Record phone model, Android version, output route, server version and the APK version with results. Prior combined-app observations are preserved in [HOUSE_VALIDATION.md](../../docs/HOUSE_VALIDATION.md), not promoted to new-build passes.

## First phone pass

1. Install beside SMB Music, Quit the old combined app, and open House Music on home Wi-Fi. Confirm the name is **House Music**, the Pi address is correct and folders load without SMB credentials. Repeat with Tailscale enabled.
2. With music already playing on an S3, open Now Playing. Confirm it adopts the same song/queue without a restart. Select a song and use Play/Pause, Next and Seek; verify the S3 follows the shared session.
3. Connect Bluetooth. Confirm the phone joins the same stream, then tap the gear, enter that phone's prior timing correction and compare phone/S3 synchronization. Confirm the correction remains after Quit/reopen and that changing it does not restart the server's song.
4. Mute the phone, Pause and Resume the house while S3 nodes are audible. Confirm the phone stays muted. Disconnect Bluetooth; the phone must remain silent and other audible nodes must continue under server policy.
5. Quit House Music while an S3 is playing. Confirm the phone stops, its notification disappears and the S3 continues. Reopen: current server state must be fetched before a track is presented.

Manual Unmute must now work without Bluetooth, including the headphone jack. Verify Bluetooth connect/unmute and disconnect/mute still work. Check unplugging wired headphones mutes without falling back audibly to the phone speaker.

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

Build/test/lint and artifact evidence belongs in [RELEASE_0.1.1.md](RELEASE_0.1.1.md). Do not revive cross-app handoff tests: transfer was removed from the product.

## Reported field issues — 2026-10-02

These are observations, not implemented fixes. SMB Music v0.5.1 does not change the House app.

- New files added to Shared Music do not appear in the browser; refresh/indexing cause uninvestigated.
- No visible mute/output icon in the supplied Now Playing screenshot. The earlier instruction to hold that icon is unusable on the delivered screen. Keep Bluetooth automation when restoring a working manual control, including wired output.
- Tapping the gear appears to do nothing; cause uninvestigated.
- Now Playing displayed `ACK [55@0] {next} Not playing; refresh before ...`, “Boulevard of Broken Dreams”, `00:00 / 04:22`, and a dimmed Play button. The user confirmed that it did not recover and required Quit/reopen. Restore usable controls from fresh server state after a rejected command and provide a readable status; root cause remains unproven.

## v0.1.1 regression acceptance — pending on phones

- Confirm mute sits on the bottom bar beside Shuffle and Repeat; the gear on that bar directly opens the current route’s −1000 to +1000 ms sync dialog. Check small screens and larger text settings. Apply 410/385 ms only to the phones that previously used those values; reopen and confirm retention.
- Add a file to Shared Music with server v0.9.1 installed. Pull down on the file list: verify scan status and eventual new entry. Repeat with the browser left open for automatic refresh, with two phones, and while music is playing. The queue/transport should remain unchanged. Retain search/sort/scroll; returning from Now Playing should refresh. No new buttons were added.
- With a retained stopped queue, Play must be enabled and Next/Previous disabled. Trigger a rejected command or interrupt control access; restore it and confirm the same screen recovers without Quit, guessing a new track, or sending Next twice. Hold status text if logs are needed.
- Verify mute/unmute via headphone jack, Bluetooth automation, S8 compatibility, lock-screen controls, and audible synchronization on real hardware. JVM/UI and server tests are not these hardware passes.
