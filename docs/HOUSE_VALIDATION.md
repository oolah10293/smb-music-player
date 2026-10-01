# v0.4.2 HOUSE physical validation

This is the **single current Android HOUSE field-status/checklist document**. Historical version results live in [VALIDATION_STATE.md](VALIDATION_STATE.md); normative behavior lives in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md); exact v0.4.2 build/signing details live in [RELEASE_0.4.2.md](RELEASE_0.4.2.md).

Server v0.8.2 remains the deployed server baseline.

## Current field results — 2026-09-30

- **PASS:** HOUSE works with Tailscale connected while the physical non-VPN home LAN remains present.
- **PASS:** the lower Media3 output icon appearance/location is accepted.
- **PASS:** with an S3 already audible, changing song/PLAY LIST from a muted phone changes shared playback while the phone stays muted.
- **PASS:** Bluetooth audio connect automatically unmutes the HOUSE phone output.
- **PASS:** Bluetooth audio disconnect automatically mutes the HOUSE phone output.
- **PASS, reported sequence:** while two nodes played and the phone was muted with no Bluetooth audio, Pause on the phone paused playback and Resume restarted both nodes.
- **FAIL, same sequence:** Resume also incorrectly unmuted the phone despite no Bluetooth audio being connected. Shared transport propagation worked; local phone renderer eligibility was violated. Root cause is not yet verified.
- **PASS, route-specific:** **+400 ms** timing correction audibly aligns the currently tested phone/output route with the S3. Keep the adjustment available until other devices/routes are measured.
- **PASS:** physical home-LAN departure is detected even when cellular/Tailscale can still reach the Pi.
- **FAIL:** HOUSE -> STANDALONE same-song continuation still does not occur after departure; the app remains HOUSE-reconnecting instead of continuing through SMB/Tailscale.
- **FAIL:** STANDALONE -> HOUSE live return transition does not reliably occur when the qualifying home LAN returns; the app can remain on its private SMB player.
- **PASS only after close/reopen:** after that failed live return, reopening can qualify HOUSE and adopt the shared Now Playing state.
- **FAIL:** if Bluetooth is already connected during HOUSE reopen/reattach while the HOUSE/S3 session is already playing, the phone can attach muted. Current route state must be evaluated on attachment.
- **Observed split-brain consequence:** while stranded in STANDALONE after returning home, the phone played its SMB session and a subsequently powered S3 started a second HOUSE queue. **Required correction:** the playing phone session should have transferred to the Pi and become HOUSE on return; the S3 should have joined it. Stopping the phone later and adopting the second/default queue is not a successful handoff.
- **FAIL:** Galaxy S8 v0.4.2 crashes on launch. Root cause is not yet assigned; tracked separately in Issue #3.
- **PENDING:** dedicated HOUSE Quit/reopen stale-state cleanup validation.

## Remaining v0.4.2 checks

1. **HOUSE Quit cleanup**
   - With an S3 playing, Quit the app.
   - Phone audio, notification, cached track/queue/position and stale HOUSE presentation must disappear.
   - MPD/S3 playback must not be stopped or cleared by the phone.
   - Reopen must freshly adopt current server state.

2. **Live home/away transition after the next fix**
   - Depart while the phone is audibly playing HOUSE: continue the same track through standalone SMB/Tailscale from the last-heard position.
   - Muted/paused/stopped departure stays silent.
   - Return while SMB is actively playing and HOUSE is idle: immediately qualify the physical LAN/Pi, transfer the phone's current queue/order, track, position and Shuffle/Repeat to the authoritative HOUSE session, and end private SMB playback as the handoff takes effect.
   - Then power on an S3: it must join that same session at its current track/position, with no separate passive-default start, queue replacement, or song restart.
   - Also power on the S3 during handoff to check that passive auto-start cannot race the transfer and create a second session.
   - Separately return to a HOUSE session already active before arrival: adopt it without overwriting its queue. Mere controller attachment and paused/stopped return must not create Playing intent.
   - Confirm the failed sequence is prevented, not merely repaired by adopting an independently started S3/default queue afterward. These are required checks, not new PASS results.

3. **Bluetooth attachment and sole-phone policy**
   - If HOUSE is already playing and Bluetooth is already connected on attach/reopen, join unmuted immediately.
   - Existing Bluetooth alone must not start fresh idle or deliberate Pause/Stop.
   - With the phone as the only audible node, Bluetooth disconnect should cause the existing server muted-only policy to pause/retain; reconnect should unmute and resume that automatic pause.
   - Watches/input-only Bluetooth devices must not count as media output.

4. **Controller lifecycle**
   - Background/screen-off heartbeat remains alive beyond the 15-second lease expiry window.
   - Force-stop/network loss exercises expiry.
   - Passive S3 return or phone unmute resumes a server-owned automatic pause.
   - Explicit Pause/Stop remains authoritative.

5. **Transport and phone renderer eligibility**
   - Reproduce the reported regression: two nodes playing, phone muted, no Bluetooth audio; Pause then Resume on the phone.
   - Both nodes must resume; the phone must remain muted throughout.
   - Repeat via on-screen and media-session/notification controls, selected-track/PLAY LIST, queue changes, and HOUSE reattachment. No path may bypass Bluetooth eligibility or fall back to the phone speaker.
   - An explicit local mute survives transport commands while Bluetooth remains connected; route connect/attachment follows the separate approved output rules.

6. **Queue/control preservation**
   - Now Playing sort preserves current song, exact position, transport, Shuffle/Repeat and session-policy state.
   - Seek/Next/Previous/PLAY LIST and selected-track behavior change the shared session without independently unmuting the phone. Use the Bluetooth eligibility rule in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md), which supersedes pre-command auto-unmute.

7. **Standalone regression**
   - Preserve v0.3.8 Country Buffer/recovery, Android Auto/Garmin, metadata, fade, search X, portrait layout, scroll restoration, Repeat All and SMB tuning.
   - Add the approved standalone Bluetooth-output behavior: connect starts/resumes an available retained SMB session; disconnect pauses/silences and retains exact local queue/song/position; reconnect resumes; already-connected Bluetooth is evaluated on app/mode entry; explicit Stop/Quit wins; no retained session means no Bluetooth-triggered start.

8. **Device compatibility**
   - Capture the Galaxy S8 crash/stack trace before changing compatibility code.

## Install/config notes

Install the exact v0.4.2 artifact identified in [RELEASE_0.4.2.md](RELEASE_0.4.2.md). The current CI debug-signing scheme can require uninstall/reinstall between builds; stable upgrade signing remains open work.

MPD must listen on localhost **and** the configured LAN interface. HOUSE qualification uses the physical non-VPN Wi-Fi/Ethernet route plus a real Pi identity check; normal Android routing carries HOUSE control/audio so Tailscale may remain connected. VPN-only reachability never qualifies HOUSE.

The optional multi-second HOUSE buffer experiment is prepared in the server repository but is not automatically deployed.
