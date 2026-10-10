# House Music v0.2.1 physical acceptance

The user confirmed URL entry, saved-station selection and playback in v0.2.0 (issue #9). The checks below are **pending on v0.2.1**; automated evidence is recorded separately in [RELEASE_0.2.1.md](RELEASE_0.2.1.md). Record phone, Android version, output route and server version.

## Four required fixes

1. **#4 Folder alignment:** open short and long folder names. Short names center in the MP3s/Rap–Parent Folder gap; long names move below the buttons with their beginning aligned to the visible MP3s/Rap left edge. Check 320dp and larger-text layouts.
2. **#5 Output interrupted:** with Bluetooth attached and a stopped session, let another audio app take focus. Open House Music and press Play. An unmuted phone requests focus again without Quit/reopen. A denied request remains interrupted until Android grants it or the user retries. No background polling may repeatedly steal focus. Repeat after manually muting; Play must leave that phone muted.
3. **#6 Startup Play:** finish passive-node listening so MPD retains a stopped queue, then launch House Music. Once the server is ready and this controller is attached, Play must enable on the same screen. Tap once to start the retained session. Repeat after temporary network/lease loss, without Quit/reopen, and confirm controls stay disabled while authority is unavailable.
4. **#9 Swipes:** swipe left Library → Now Playing → Radio and right back. Confirm browser folder/search/scroll are retained. Swipe across a file/station row without playing it. Vertical list scrolling and pull-down refresh must work; URL/search editing, control taps and seek dragging must not change pages. At the first/last page an outward swipe does nothing. Return from Radio during an Add & Play lookup; the delayed lookup must not start playback after leaving.

## Radio page

Use house-audio-server v0.10.0 or newer. Update the delivered v0.2.0 or v0.1.1 APK in place and confirm the server address, route sync corrections and existing browser settings survive. No S3 firmware update is required.

1. Swipe left through Now Playing to **Radio**. Confirm station loading, an empty-list explanation when appropriate, and usable navigation back to the library and Now Playing. The keyboard should open when the URL field is tapped, not merely when the page opens.
2. Paste `https://stream.radioparadise.com/rock-192` and tap **Add & Play**. Confirm a generated name, one saved row, the current-station indication, and audible playback on the house receivers. Station lookup may take several seconds; repeated taps must not create duplicate writes.
3. Add `https://stream.revma.ihrhls.com/zc2033` (WXDX / 105.9 The X). Confirm playback switches to it. Tap the Radio Paradise row and confirm it switches back. Open another House Music controller and confirm it sees the shared stations and current selection.
4. Submit an already-saved URL. Confirm it reuses the saved station instead of adding a duplicate. Hold a station, rename it, then reload the page on both phones and confirm the shared name. Empty or whitespace-only names must not be saved.
5. Delete an inactive station with its **×** and confirm it disappears. Use **Undo** and confirm the bookmark returns without changing playback. Delete the currently playing station and confirm its broadcast continues; return to Now Playing and confirm it still identifies the live source even though the bookmark is gone. Undo should restore the saved row without restarting the stream.
6. Quit/reopen the app and confirm saved stations remain. Restart the house server and reload: bookmarks must survive, but the server need not resume the pre-restart radio session. Tap a saved station to start it again.
7. Submit an empty value, whitespace, malformed URL, unsupported scheme, station web page and unreachable stream. Confirm useful errors and usable controls. Invalid or failed probes must not replace the active house music. A pasted URL must remain editable after failure.
8. Try Add & Play while disconnected; reconnect and confirm it does not later replay the add/play operation. Separately interrupt the response to a station add, rename, delete or play request. After recovery, refresh to establish the server's actual result; the app must not silently repeat the write or switch back to an older requested station. Retry deliberately only after checking the current list/state.

The two URLs above were verified against the deployed server before this APK was built. They are test examples, not a guarantee of future station availability or metadata.

## Live playback and return to local music

- Open Now Playing during radio playback. Confirm **LIVE**, the saved station name and available song/broadcast metadata. Metadata should update as the stream changes tracks without requiring a queue replacement or reopening the screen. Missing metadata must leave a readable station identity, not a raw blank placeholder.
- Confirm seeking, Previous/Next, Shuffle, Repeat and queue sorting are unavailable for live radio. The phone mute and sync gear must remain usable in their existing positions. Check small screens and larger text settings.
- Pause radio from Now Playing, then resume. Confirm the app presents the server's logical paused state even though MPD stops its upstream connection. Resume must rejoin the live broadcast, not promise the paused song position. Exercise the radio notification's Play/Pause and Quit actions; there must be no radio skip action.
- Interrupt the upstream station connection while keeping the Pi reachable. Confirm connecting/retrying/error status remains readable and manual Pause/Stop cancels continued play intent according to the server state. Switching to a different station or local music must prevent the old station from returning after recovery.
- Return to the library browser and select an MP3s song or **PLAY LIST**. Confirm local music replaces radio on the shared house receivers, **LIVE** disappears, normal local transport/seek/queue-sort controls return, and the stations remain saved. Repeat with Rap. The old local queue and song need not be restored.
- Change the MP3s/Rap default while radio is playing. Confirm this changes the future passive startup choice without interrupting the current station. Test the server's fresh passive-node startup separately; it must still follow the saved local folder default.
- During radio, mute the phone and remove the last audible renderer. Confirm server automatic pause is reflected in the app; return an eligible audible node and check live resume. Remove all nodes/controllers and verify the server ends the radio session according to its lifecycle policy. These checks do not replace the local-music lifecycle checks below.

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

Build/test/lint and artifact evidence belongs in [RELEASE_0.2.1.md](RELEASE_0.2.1.md). Do not revive cross-app handoff tests: transfer was removed from the product.

## Reported field issues — 2026-10-02

These are historical observations that motivated v0.1.1. The fixes and automated evidence are recorded in [RELEASE_0.1.1.md](RELEASE_0.1.1.md); they are not new v0.2.1 physical passes. SMB Music changes do not change the House app.

- New files added to Shared Music do not appear in the browser; refresh/indexing cause uninvestigated.
- No visible mute/output icon in the supplied Now Playing screenshot. The earlier instruction to hold that icon is unusable on the delivered screen. Keep Bluetooth automation when restoring a working manual control, including wired output.
- Tapping the gear appears to do nothing; cause uninvestigated.
- Now Playing displayed `ACK [55@0] {next} Not playing; refresh before ...`, “Boulevard of Broken Dreams”, `00:00 / 04:22`, and a dimmed Play button. The user confirmed that it did not recover and required Quit/reopen. Restore usable controls from fresh server state after a rejected command and provide a readable status; root cause remains unproven.

## Retained v0.1.1 regression checks

- Confirm mute sits on the bottom bar beside Shuffle and Repeat; the gear on that bar directly opens the current route’s −1000 to +1000 ms sync dialog. Check small screens and larger text settings. Apply 410/385 ms only to the phones that previously used those values; reopen and confirm retention.
- Add a file to Shared Music with server v0.9.1 or newer installed. Pull down on the file list: verify scan status and eventual new entry. Repeat with the browser left open for automatic refresh, with two phones, and while music is playing. The queue/transport should remain unchanged. Retain search/sort/scroll; returning from Now Playing should refresh.
- With a retained stopped queue, Play must be enabled and Next/Previous disabled. Trigger a rejected command or interrupt control access; restore it and confirm the same screen recovers without Quit, guessing a new track, or sending Next twice. Hold status text if logs are needed.
- Verify mute/unmute via headphone jack, Bluetooth automation, S8 compatibility, lock-screen controls, and audible synchronization on real hardware. JVM/UI and server tests are not these hardware passes.


## v0.3.0 radio details and spacing acceptance

Install server v0.11.2 and app v0.3.0. Retain the existing swipe, focus-recovery,
stopped-startup, phone mute/sync and local-queue checks above.

1. Play a station that supplies song metadata. Now Playing should show station,
   song, artist/album when supplied, broadcast name and stream quality. Wait for
   a song change without changing stations; details must refresh. Longer details
   scroll while playback/mute/sync controls stay in place. Missing metadata must
   not look like an invented song. Switch to a local folder and check normal
   artwork/metadata and seek/skip/shuffle/repeat controls return.
2. Let an identified song play more than ten seconds, then wait for the next song.
   Last played should show the outgoing title/artist, station and local date/time.
   The current song passing ten seconds must not replace it prematurely. A brief
   station snippet must not replace the previous qualifying song. With an audible
   S3 left on, close the phone app, let a song change, and reopen: history should
   be there. Restart the server and check history survives but old radio intent
   does not resume. No earlier pre-update songs can be recovered.
3. On Library, the gray folder panel should be shorter; the visible black gap
   below it should match the gap above Search. Short folder names stay centered;
   long ones retain their left-aligned second row. Folder buttons remain tappable.
4. Compare Radio's URL entry to Library's filename search: both are 40dp high.
   Test text entry, paste, horizontal page swipes and vertical scrolling/refresh.

The user confirmed v0.2.1 swiping and station add/save/play. On 2026-10-10, after
server v0.11.2 fixed both observed WXDX metadata formats, the user reported:
“ok, that seems to work pretty well. The UI looks really good.” This is positive
field feedback for the metadata fix and UI. Do not treat that general confirmation
as individual execution of every timing, restart, app-closed or focus/startup case
above; retain those detailed checks for targeted verification if needed.
