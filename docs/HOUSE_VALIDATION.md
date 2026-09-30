# v0.4.2 HOUSE physical validation

This is the **single current Android HOUSE field-status/checklist document**. Historical version results live in [VALIDATION_STATE.md](VALIDATION_STATE.md); normative behavior lives in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md); exact v0.4.2 build/signing details live in [RELEASE_0.4.2.md](RELEASE_0.4.2.md).

Server v0.8.2 remains the deployed server baseline.

## Current field results — 2026-09-30

- **PASS:** HOUSE works with Tailscale connected while the physical non-VPN home LAN remains present.
- **PASS:** the lower Media3 output icon appearance/location is accepted.
- **PASS:** with an S3 already audible, changing song/PLAY LIST from a muted phone changes shared playback while the phone stays muted.
- **PASS:** Bluetooth audio connect automatically unmutes the HOUSE phone output.
- **PASS:** Bluetooth audio disconnect automatically mutes the HOUSE phone output.
- **PASS, route-specific:** **+400 ms** timing correction audibly aligns the currently tested phone/output route with the S3. Keep the adjustment available until other devices/routes are measured.
- **PASS:** physical home-LAN departure is detected even when cellular/Tailscale can still reach the Pi.
- **FAIL:** HOUSE -> STANDALONE same-song continuation still does not occur after departure; the app remains HOUSE-reconnecting instead of continuing through SMB/Tailscale.
- **FAIL:** STANDALONE -> HOUSE live return transition does not reliably occur when the qualifying home LAN returns; the app can remain on its private SMB player.
- **PASS only after close/reopen:** after that failed live return, reopening can qualify HOUSE and adopt the shared Now Playing state.
- **FAIL:** if Bluetooth is already connected during HOUSE reopen/reattach while the HOUSE/S3 session is already playing, the phone can attach muted. Current route state must be evaluated on attachment.
- **Observed split-brain consequence:** while stranded in STANDALONE after returning home, private SMB playback can run while powering an S3 starts/joins a separate authoritative HOUSE queue.
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
   - Return home: immediately probe the qualifying LAN, stop private standalone ownership, and adopt the authoritative HOUSE session without overwriting its queue.
   - Confirm the split-brain state cannot occur.

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

5. **Queue/control preservation**
   - Now Playing sort preserves current song, exact position, transport, Shuffle/Repeat and session-policy state.
   - Seek/Next/Previous/PLAY LIST and selected-track behavior obey the existing pre-command audible-output rules.

6. **Standalone regression**
   - Preserve v0.3.8 Country Buffer/recovery, Android Auto/Garmin, metadata, fade, search X, portrait layout, scroll restoration, Repeat All and SMB tuning.
   - Add the approved standalone Bluetooth-output behavior: disconnect pauses/silences and retains exact local queue/song/position; reconnect resumes; already-connected Bluetooth is evaluated on app/mode entry; explicit Stop/Quit wins; no retained session means no Bluetooth-triggered start.

7. **Device compatibility**
   - Capture the Galaxy S8 crash/stack trace before changing compatibility code.

## Install/config notes

Install the exact v0.4.2 artifact identified in [RELEASE_0.4.2.md](RELEASE_0.4.2.md). The current CI debug-signing scheme can require uninstall/reinstall between builds; stable upgrade signing remains open work.

MPD must listen on localhost **and** the configured LAN interface. HOUSE qualification uses the physical non-VPN Wi-Fi/Ethernet route plus a real Pi identity check; normal Android routing carries HOUSE control/audio so Tailscale may remain connected. VPN-only reachability never qualifies HOUSE.

The optional multi-second HOUSE buffer experiment is prepared in the server repository but is not automatically deployed.
