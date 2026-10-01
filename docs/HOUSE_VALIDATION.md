# v0.4.3 HOUSE physical validation

This is the **single current Android HOUSE field-status/checklist document**. Normative behavior lives in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md), version history in [VALIDATION_STATE.md](VALIDATION_STATE.md), and build/artifact verification in [RELEASE_0.4.3.md](RELEASE_0.4.3.md).

**2026-10-01: v0.4.3 has a partial physical pass, but both live handoff directions failed.** Server **v0.9.0 is confirmed installed** by the user's health output: service OK, startup ready, MPD and Snapserver reachable. Build success does not establish handoff acceptance.

**Later decision, 2026-10-01:** the user chose completely independent SMB Music and HOUSE apps with no handoff or shared session. The observations below remain valid evidence for the shipped combined v0.4.3 app. Its transfer checklist is historical and is no longer a gate for the split; no new build has been delivered. Current product direction is in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).

## Remaining useful checks on the installed v0.4.3 build

The user will report these later; do not mark them passed without field evidence:

- Already-connected Bluetooth: reopen HOUSE during active playback and join without reconnecting Bluetooth.
- Muted-phone Pause/Resume: S3s resume while the phone stays muted.
- Standalone Bluetooth disconnect/reconnect: pause and retain its own session, then resume it.
- HOUSE Quit/reopen: phone audio and notification clear, other nodes continue, and reopening fetches current server state.

## Current physical results — v0.4.3, 2026-10-01

- **PASS:** the user reports three S3 nodes and two phones running with good synchronization; Galaxy S8 is working. The two phone timing settings are **410 ms** and **385 ms**; the report does not map them to particular devices or output routes.
- **FAIL, leaving home:** the user reports failed continuation. At 14:28 on cellular with VPN active, Now Playing shows **Waiting for SMB — retry in 12s**, `02.02.99`, and 00:01 / 00:00. The app reached standalone SMB recovery; the screenshot does not establish why the file read failed or whether it selected the correct departure track.
- **FAIL, returning home:** at 14:56 with Wi-Fi and VPN active, Now Playing shows **Return home paused — transfer incomplete**, `Get That Money`, and 2:18 / 4:44. A transfer was attempted but did not complete automatically. The screenshot cannot distinguish a reserved, expired, or cancelled server receipt.
- **FAIL, manual Unmute:** a third phone using its headphone jack cannot unmute without Bluetooth. The manual override is queued in [ROADMAP.md](ROADMAP.md); implementation remains explicitly deferred. Keep Bluetooth automation.
- **UNCONFIRMED, excess mobile data:** the 14:57 Settings screenshot for Oct 1–Nov 1 attributes **72.19 MB to SMB Music**, **2.23 GB to Mobile Services**, and **14.33 MB to Tailscale**. It does not establish listening duration, a trip-specific delta, or unique bytes across app/VPN counters. Do not attribute Mobile Services' gigabytes to the player.
- The user reports that the release feels regressed overall despite the compatibility and synchronization gains. Review transition ownership before adding more recovery branches or issuing another build.

## Read-only code review after those failures

- `PlaybackService.finishHandoff()` maps `reserved`, `expired`, and `cancelled` to the same incomplete message and marks them terminal, stopping automatic reconciliation. An exception during prepare/attach/commit can lead to this state while hiding its original cause. This explains the persistent paused state, not the initiating failure.
- `SmbClient.probeFile()` reduces authentication, path, connection, and read failures to one boolean; the departure screenshot cannot identify which occurred. A stale SMB connection across network changes is a hypothesis, not a finding.
- Return transfer pauses the standalone player but stops its loader only after successful HOUSE adoption. A prepared paused player can continue buffering; failed transfers therefore risk unnecessary reads. Repeated recovery can also discard and reread buffered data. Neither establishes measured waste in this trip.
- v0.4.3 did not enlarge the standalone Country Buffer: 120–600 seconds, a 32 MiB allocation target with time priority, and 64 KiB SMB read-ahead. The allocation target is not a hard download cap. Retry probes request one file byte plus protocol overhead, not an entire song.
- Superseded proposal (before the independent-app decision): one service-owned transition coordinator, explicit operation phases, actual closure of the outgoing reader, and a small trace retaining original errors and byte counts. Keep the proven audio engines, Bluetooth automation, sync adjustment, and server authority. Do not resume SMB while a delayed HOUSE commit could still take effect; resolve or confirm cancellation first. No playback code changed during this review.

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
- **Observed competing sessions:** while the phone remained on SMB after returning, a subsequently powered S3 started another HOUSE queue. The correction required at that time was to transfer the playing phone session into idle HOUSE first, so the S3 joined it. That handoff requirement is now superseded by the independent-app decision.
- **FAIL:** Galaxy S8 v0.4.2 crashed on launch. No crash trace establishes the cause; Issue #3 tracked it. v0.4.3 guards newer public Android route APIs and now has the reported operation pass above.
- **PENDING:** dedicated HOUSE Quit/reopen cleanup validation.

## Historical combined v0.4.3 checklist — superseded as a split-app release gate

1. **Prepare the paired system and mapping**
   - Server v0.9.0 installation is confirmed; v0.4.3 is the reported phone build. Preserve the exact APK/build identity from the release document for any targeted reproduction.
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
   - Basic operation is now a reported physical pass. This does not establish every home/away case on the S8 or prove the earlier crash's cause.
   - Attribute future transition results to the actual tested device; the current report does not identify which phone performed the failed handoffs.

## Installed combined v0.4.3 configuration notes

The current CI debug-signing scheme can require uninstall/reinstall between builds; stable upgrade signing remains open. Preserve local SMB credentials, HOUSE address, music-root mapping and timing correction before uninstalling.

MPD must listen on localhost **and** the configured LAN interface. HOUSE qualification uses a physical non-VPN route plus Pi identity; ordinary Android routing carries HOUSE control/audio so Tailscale may remain connected. VPN-only reachability never qualifies HOUSE.

The optional multi-second HOUSE buffer experiment remains separately prepared in the server repository; this app iteration does not deploy it.
