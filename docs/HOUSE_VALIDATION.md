# v0.4.3 HOUSE physical validation

This is the **single current Android HOUSE field-status/checklist document**. Normative behavior lives in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md), version history in [VALIDATION_STATE.md](VALIDATION_STATE.md), and build/artifact verification in [RELEASE_0.4.3.md](RELEASE_0.4.3.md).

**2026-10-01: v0.4.3 source changes are implemented; physical acceptance is pending.** No old PASS or FAIL below is a v0.4.3 result. Server **v0.8.2 remains the confirmed deployed baseline**; coordinated return-home testing requires installing **server v0.9.0** first. Source commits/builds do not establish Pi deployment.

## Last physical baseline — v0.4.2, 2026-09-30

- **PASS:** HOUSE works with Tailscale connected while the physical non-VPN home LAN remains present.
- **PASS:** the lower Media3 output icon appearance/location is accepted.
- **PASS:** with an S3 already audible, changing song/PLAY LIST from a muted phone changes shared playback while the phone stays muted.
- **PASS:** Bluetooth audio connect unmutes; disconnect mutes the HOUSE phone output.
- **PASS, shared transport:** with two nodes playing and the phone muted without Bluetooth, Pause paused playback and Resume restarted both nodes.
- **FAIL, same sequence:** Resume also unmuted the phone without Bluetooth. v0.4.3 separates transport from output eligibility; retest required.
- **PASS, route-specific:** **+400 ms** audibly aligned the tested phone/output route with the S3. Keep adjustment available; this does not establish timing on another route/device.
- **PASS:** physical home-LAN departure is detected despite cellular/Tailscale reachability to the Pi.
- **FAIL:** departure did not continue the same song over SMB; the app remained HOUSE-reconnecting.
- **FAIL:** live return could leave the phone playing privately through SMB. Closing/reopening could then qualify HOUSE and adopt its state.
- **FAIL:** already-connected Bluetooth could attach muted on HOUSE reopen.
- **Observed competing sessions:** while the phone remained on SMB after returning, a subsequently powered S3 started another HOUSE queue. The required correction is to transfer the playing phone session into idle HOUSE first, so the S3 joins it. Stopping the phone later to adopt that second default is not a successful handoff.
- **FAIL:** Galaxy S8 v0.4.2 crashed on launch. No crash trace establishes the cause. v0.4.3 guards newer public Android route APIs, but S8 acceptance remains pending; Issue #3 tracks it.
- **PENDING:** dedicated HOUSE Quit/reopen cleanup validation.

## v0.4.3 acceptance checklist — all pending

1. **Prepare the paired system and mapping**
   - Install server v0.9.0 and the exact v0.4.3 APK recorded in the release document.
   - In Browser, hold **SMB / MP3s / Rap** to open Connections. Set **HOUSE music root on SMB** to the folder corresponding to MPD's music root. If HOUSE lists `MP3s/song.mp3`, that SMB folder must contain `MP3s`.
   - Check spaces, punctuation, Unicode, nested folders, and duplicate filenames in separate folders. A missing root, different share/host, or out-of-root queue entry must leave transfer paused with a visible explanation, without silently dropping tracks.

2. **Returning to idle HOUSE**
   - Play a known SMB queue away from home; note order, selected track, position and Shuffle.
   - Return while playing. The phone pauses private audio, reserves idle HOUSE, attaches muted/not-ready, commits that queue/index/position/Shuffle and Repeat All, then adopts HOUSE and joins according to Bluetooth eligibility.
   - Power on an S3 after the transition: it must join that same session at its current track/position, without starting a separate default or restarting/reshuffling the transferred queue.
   - Also power on the S3 during preparation/commit. Reservation must prevent a competing passive-default start. Record the actual order of phone qualification, reservation and S3 arrival if this fails.
   - Separately return when HOUSE was already active before arrival: adopt it without overwriting its queue. A paused/stopped phone or mere controller attachment must not invent Playing intent.

3. **Uncertain or interrupted return**
   - Interrupt the commit acknowledgement, then restore networking. Resolve the same transfer's server status; do not replay queue replacement or briefly resume private SMB.
   - Restart the app or server with a pending transfer. Unknown/failed/incomplete outcomes must stay paused and explain the next action.
   - Use the foreground notification’s **Stop transfer** action during transfer: stale completion must not resume playback. Quit must cancel/detach local participation without stopping an already-committed shared session or unrelated listeners.
   - Confirm the temporary controller lease is replaced or detached correctly, including cancelled/failed transfers.

4. **Leaving HOUSE**
   - Depart while the phone is audibly playing HOUSE over Bluetooth: continue the same estimated heard track/position through SMB/Tailscale after buffering.
   - Test near a track boundary with Shuffle, after a seek, and on a repeating single-track queue. The phone must not guess an unobserved successor or use the raw MPD lead as the heard position.
   - Muted/paused/stopped departure stays silent. Already-connected Bluetooth must not resume an unrelated old SMB queue.
   - Other HOUSE nodes keep their session. No departure Stop, Seek or queue replacement reaches MPD.
   - A control/audio failure while the physical home route remains present stays HOUSE recovery; VPN-only reachability after physical departure does not count as home.
   - A gap during switching is expected to require measurement; gapless or sample-accurate switching is not claimed.

5. **HOUSE Bluetooth eligibility and transport**
   - Reproduce the reported two-node Pause/Resume sequence without Bluetooth: both nodes resume, phone stays muted.
   - Repeat through on-screen, notification, lock-screen/media-session controls, selected-track/PLAY LIST, queue changes and reattachment. No path may bypass Bluetooth eligibility or fall back to the phone speaker.
   - Manual local mute survives transport while the route is unchanged. A new Bluetooth audio connection follows the approved automatic unmute rule.
   - With HOUSE already playing, attach/reopen while Bluetooth is already connected: join immediately without requiring a new callback.
   - Existing Bluetooth alone does not start fresh idle or deliberate Pause/Stop. Watches/input-only devices do not qualify.
   - Sole audible phone: disconnect invokes server-owned muted-only pause/retention; reconnect resumes that automatic pause. Other audible nodes continue when present.

6. **Controller lifecycle, Quit and queue controls**
   - Background/screen-off heartbeats remain alive beyond the 15-second lease window; force-stop/network loss exercises expiry.
   - Quit clears phone audio, notification, cached track/queue/position and stale HOUSE presentation. It must not Stop/Clear MPD. Reopen fetches fresh authoritative state.
   - Check final-node drain, return before track end, and ending the session when the last controller leaves an automatic pause.
   - Now Playing sort preserves song, position, transport, Shuffle/Repeat and session-policy state. Seek/Next/Previous/PLAY LIST changes shared playback without independently unmuting the phone.
   - Browser, Now Playing and external media controls follow the new player after each live mode change.

7. **Retained standalone session and regression**
   - Bluetooth connect/reconnect resumes an available retained SMB queue; disconnect pauses and retains queue/song/position. Evaluate pre-connected Bluetooth on app/mode entry.
   - Stop/Quit wins over route callbacks and restored state. With no retained session, Bluetooth starts nothing.
   - Exercise process restart, screen-off behavior and SMB recovery during connect/disconnect.
   - Preserve the v0.3.8 Country Buffer/recovery, Android Auto/Garmin, metadata, fade, search X, portrait layout, scroll restoration, Repeat All and SMB tuning. Use [TESTING.md](TESTING.md).

8. **Galaxy S8**
   - Install and launch v0.4.3; test home detection with Tailscale on/off and live mode changes.
   - If it crashes, capture the actual stack trace. API lint/source guards are not a hardware pass or proof of the earlier crash's root cause.

## Install/config notes

The current CI debug-signing scheme can require uninstall/reinstall between builds; stable upgrade signing remains open. Preserve local SMB credentials, HOUSE address, music-root mapping and timing correction before uninstalling.

MPD must listen on localhost **and** the configured LAN interface. HOUSE qualification uses a physical non-VPN route plus Pi identity; ordinary Android routing carries HOUSE control/audio so Tailscale may remain connected. VPN-only reachability never qualifies HOUSE.

The optional multi-second HOUSE buffer experiment remains separately prepared in the server repository; this app iteration does not deploy it.
