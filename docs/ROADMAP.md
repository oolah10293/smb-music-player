# Roadmap / ideas

This file contains **open work and future features only**. Completed implementation history belongs in release/validation documents, and normative behavior belongs in [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).

## Current Android priorities

1. **Validate standalone SMB Music v0.5.1 and House Music v0.1.0**
   - **User decision, 2026-10-01:** no connection between the apps. This supersedes automatic LAN/cellular/LAN handoff, the proposed coordinator redesign, and the briefly discussed manual transfer. The normative boundary is [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md).
   - SMB Music owns only its standalone SMB/ExoPlayer session. The HOUSE app controls/renders only the Pi's shared MPD/Snapcast session. No shared queue/song/position, SMB-triggered HOUSE reset, automatic or manual transfer, app-specific launch/stop coordination, or background synchronization.
   - Use the last pre-HOUSE standalone source as the SMB reference, inventory subsequent improvements, and extract the current HOUSE player into its own app. Produce separate installable apps and remove the combined mode-switching machinery from their playback paths. The verified references and carry-forward inventory are below.
   - Both independent source trees are now implemented: SMB Music v0.5.0 at the repository root and **House Music v0.1.0** in the independent `house-app` Gradle root. Verify exact build/artifact status in [RELEASE_0.5.0.md](RELEASE_0.5.0.md) and [House Music's release record](../house-app/docs/RELEASE_0.1.0.md). The preserved combined checkpoint remains `house-music-pre-split`. Physical acceptance of each extraction remains pending. Preserve historical failed-handoff evidence without treating handoffs as a new release gate.
   - Keep server v0.9.0 and S3 firmware unchanged for these releases. After the old combined phones are retired, remove server-only handoff imports, reservations and transfer bookkeeping while retaining ordinary controller/output lifecycle and passive-node startup. Neither independent app calls that legacy API.
   - Validate that playing/pausing/quitting SMB Music or changing Wi-Fi/cellular never touches HOUSE. HOUSE launch/reconnection adopts current server state without importing SMB music or controlling that app. Each app must recover its own connection independently.
   - Address reader shutdown and measure SMB data use within the standalone app. The screenshot's 72.19 MB app counter does not establish excess use; Mobile Services' 2.23 GB is separately attributed.
   - **Approved diagnostic space retained:** the Now Playing album-art area may show a bounded, readable, timestamped mini-log during diagnosis. Show each app's own connection, audio-reader lifecycle, actual errors and retries; label measured SMB payload bytes accurately. Preserve surrounding controls and omit credentials. Transfer/mode-switch events are obsolete with the split; the log must not create a connection between apps.
   - **SMB Music v0.5.1 field gate:** retest Tailscale auto-connect after the split and Bluetooth resume without unlocking/foregrounding the app, including long pauses and service recreation. Source corrections are implemented, not hardware-accepted; see [VALIDATION_STATE.md](VALIDATION_STATE.md) and [TESTING.md](TESTING.md).
   - **House Music v0.1.0 feedback remains open:** missing mute icon, apparently inert gear, newly added files absent from the browser, and rejected Next leaving the app stuck until Quit/reopen. See [House field reports](../house-app/docs/TESTING.md#reported-field-issues--2026-10-02).

2. **Fix the manual mute button; preserve Bluetooth automation**
   - **Queued on 2026-10-01; implementation deferred at the user's request.** v0.4.3 blocks manual Unmute when Bluetooth is absent, preventing use of a third phone's headphone jack. Make the icon a normal manual Mute/Unmute control for the phone's current Android audio output, including wired headphones, without requiring Bluetooth.
   - Keep the existing Bluetooth automatic mute/unmute and connection behavior unchanged. This request adds a working manual override; it does not remove Bluetooth functionality.
   - Recheck the reported two-node Pause/Resume failure: shared transport must preserve local mute and must never independently unmute the phone. An explicit press of Unmute is a separate user action.
   - Check pre-connected Bluetooth on HOUSE attachment, manual mute across transport commands, and retained standalone connect/disconnect/reconnect behavior.
   - Complete HOUSE Quit/reopen, screen-off heartbeat/expiry, muted-only pause/resume, and active-queue-sort acceptance.
   - Preserve standalone/vehicle behavior in the independent SMB Music service.

3. **Release reliability**
   - Stable APK signing so upgrades do not require uninstall/reconfiguration.
   - Retest each app's own retained state and recovery after process death; determine installation/settings migration without retaining a live connection between apps.

4. **Reliability experiments**
   - Field-test the prepared multi-second HOUSE Country Buffer without assuming stale-buffer flush behavior.
   - Continue correlating intermittent S3 dropouts with diagnostics.
   - Complete the dedicated standalone prolonged-outage hardening test.

## Split references and carry-forward inventory — reviewed 2026-10-01

The user proposed using the version before HOUSE, checking later upgrades, and peeling off the HOUSE player. Repository history confirms **v0.3.8** is that standalone baseline:

- Standalone implementation: [`a3aeeff`](https://github.com/oolah10293/smb-music-player/commit/a3aeeff417bcace357b91ac07874a5cfb1a33663).
- Immediate pre-HOUSE checkpoint: [`3bb2655`](https://github.com/oolah10293/smb-music-player/commit/3bb2655c2f2b280df4625a336822c66df82715ca). The comparison from the v0.3.8 implementation to this checkpoint changes documentation and source-archive CI only; Android runtime source is unchanged.
- First HOUSE implementation: [`f0171da`](https://github.com/oolah10293/smb-music-player/commit/f0171da7c07c0d1b538e77af686a30854f099b34), v0.4.0. Latest combined implementation reviewed: [`d56881f8`](https://github.com/oolah10293/smb-music-player/commit/d56881f8a87b3aa88d6e679b89b5d0f17ff5502f), v0.4.3. Later commits through that inventory checkpoint were documentation only; the v0.5.0 standalone extraction follows this inventory.

Use the baseline to recover a standalone service/browser startup path, then selectively port the later improvements. Do not revert the whole repository or discard current requirements/history. Extract HOUSE from the latest implementation so its subsequent fixes survive.

| Area | Evidence since the pre-HOUSE checkpoint | Split treatment |
| --- | --- | --- |
| SMB transport and core playback | `SmbClient`, `SmbDataSource`, SMB URL/credentials code and Media3/jcifs versions have no changes in the comparison. Country Buffer thresholds remain unchanged. | Preserve the v0.3.8 transport, buffering, outage recovery, repeat/sort/search, metadata, fade and vehicle controls. Later service lifecycle changes still need separation and regression checks. |
| Browser polish, v0.4.0 | Button rows aligned to Search, SMB/Parent Folder and Sort/PLAY LIST positions swapped, folder-only path label. | Carry the accepted layout into the independent apps; remove HOUSE fields and branching from SMB settings. |
| Retained standalone playback, v0.4.3 | Added saved queue/index/position/Shuffle/Stop intent and Bluetooth connect/disconnect/reconnect handling. | Keep in SMB Music after removing transfer dependencies. `StandaloneSessionStore` mixes standalone state with a HOUSE journal; retain only its standalone responsibility. Keep Stop/Quit authoritative. These newer behaviors still need the pending phone checks. |
| General lifecycle/build safeguards | Notification permission request, browser destroyed-Activity guards, service-owned immediate Quit/release, unit-test CI and API-26/NewApi lint. | Carry applicable safeguards into each app without copying HOUSE mode epochs, transfer commands or native packaging into SMB. Retain the standalone Quit cleanup and external controls. |
| HOUSE playback, v0.4.0–0.4.3 | Pi browse/queue/transport, Snapcast receiver, leases/heartbeat, normal routing with Tailscale, output controls, reconnect/Quit fixes, MP3s/Rap default selector and route-specific timing correction. | Keep in the separate HOUSE app, including its native builds/licenses. Its service owns only HOUSE; strip the unused SMB engine/settings and handoff paths. Preserve existing evidence and unresolved output checks. |
| Older Android compatibility, v0.4.3 | Guarded newer `RouteInfo` API calls inside `HouseConnection`; added compatibility lint. | Preserve the guards in HOUSE and run the compatibility gate for both apps. SMB needs no HOUSE route detector solely to inherit the S8 fix. |
| Cross-mode machinery | Live mode monitor/player swaps, heard-position departure, library-root mapping, reservation/commit/status/cancel, journal, temporary controller and transfer notification. | Remove from both independent app playback paths. Neither app calls the legacy handoff API; do not recreate it between apps. |
| Network-event recovery | v0.4.3 changed the service from default-network observation to physical-network callbacks and added HOUSE mode probes. | Preserve useful app-local SMB retry triggers where justified; remove HOUSE monitoring entirely from SMB. Test Wi-Fi/cellular recovery without any change of player authority. |

The manual mute override and album-art mini-log are **queued work**, not upgrades already implemented in v0.4.3. Keep their separate status above. Source comparison establishes what to preserve, not a successful new build or device pass.

## Next major HOUSE features

- **Internet radio through MPD:** tracked in [house-audio-server Issue #5](https://github.com/oolah10293/house-audio-server/issues/5). MPD remains the source authority.
- **Dedicated synchronized subwoofer node:** tracked in [house-audio-esp32 Issue #4](https://github.com/oolah10293/house-audio-esp32/issues/4).

## Later candidates

- `.m3u` / `.m3u8` playlist-file support.
- Smart Shuffle / listening-history weighting while preserving ordinary Shuffle.
- Metadata-assisted filename cleanup as a separate library-maintenance tool.
- Recursive or metadata-indexed search if ever needed.
- Album-art fallback/cache improvements.
- Richer Android Auto browse tree.

## References

- Current physical results: [HOUSE_VALIDATION.md](HOUSE_VALIDATION.md)
- Historical validation: [VALIDATION_STATE.md](VALIDATION_STATE.md)
- Normative HOUSE architecture: [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md)
- Current standalone release record: [RELEASE_0.5.0.md](RELEASE_0.5.0.md)


### House Music v0.1.1 — implemented, physical acceptance pending

Pull-down/automatic library refresh with server v0.9.1; visible mute beside Shuffle/Repeat with wired manual override and retained Bluetooth automation; gear directly opens ±1000 ms route sync; rejected-command recovery refreshes authoritative state without replaying writes. See `house-app/docs/RELEASE_0.1.1.md`. The original field reports remain evidence, not new-build passes.
