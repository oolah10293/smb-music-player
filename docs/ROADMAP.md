# Roadmap / ideas

Unless explicitly linked to approved requirements, these are ideas, not commitments. Proven playback behavior takes priority over feature count.

## v0.3.8 field validation

The main standalone behavior is now confirmed on the real phone:

- app runs normally;
- 40dp search field and clear X are correct;
- Browser/Now Playing sort state stays synchronized;
- current/selected track remains item zero after sorting;
- Repeat All wraps correctly;
- no regressions were observed in Android Auto, Garmin/Bluetooth controls, Play/Resume fade, Tailscale startup, metadata, Country Buffer, or the preserved SMB tuning.

The remaining targeted v0.3.8 validation is prolonged SMB outage/recovery:

- prolonged SMB loss with the screen both on and off;
- visible transition through `Waiting for SMB`, `Checking SMB`, and `Rebuilding buffer`;
- automatic recovery after a failed/timed-out individual probe;
- recovery after connectivity returns during a scheduled wait;
- another outage during the buffer-rebuild phase;
- a slow but progressing refill that must not be killed by the no-progress watchdog;
- explicit Pause, Stop, Quit, track change, and queue replacement during recovery.

See [TESTING.md](TESTING.md) and [OUTAGE_RECOVERY_PLAN.md](OUTAGE_RECOVERY_PLAN.md).

## Next standalone UI polish

Preserve all confirmed v0.3.8 playback behavior and make only these Browser layout/content changes:

- align the left/right outer edges of **both button rows** with the left/right outer edges of the search bar;
- change the path display to show **only the current folder name**, not the full SMB path;
- swap the **SMB** and **Parent Folder** buttons, including their functions;
- swap the **PLAY LIST** and **Sort** buttons, including their functions.

Do not reopen the confirmed search-X behavior, search height, shared sort, current-track-first queue behavior, Repeat All, portrait lock, fade, vehicle/Bluetooth behavior, Tailscale behavior, metadata handling, Country Buffer, or SMB tuning while doing this UI pass.

## Next coordinated Android iteration

This is the next planned Android change set. **Do not split the HOUSE work from the already-approved Browser polish and do not disturb the proven v0.3.8 standalone transport behavior.**

### Preserve the v0.3.8 standalone baseline

STANDALONE continues to use the existing SMB/Tailscale -> Media3/ExoPlayer path, including Country Buffer, SMB read-ahead, outage recovery, metadata fallback, Android Auto/Bluetooth/Garmin behavior, explicit Play/Resume fade, Repeat All, search, and shared sort behavior.

### Apply the already-approved Browser polish

- align the left/right outer edges of both Browser button rows with the search bar;
- show only the current folder name in the path display;
- swap the SMB and Parent Folder button positions/functions;
- swap the PLAY LIST and Sort button positions/functions.

### Automatic HOUSE / STANDALONE startup

On app launch, determine HOUSE before starting the ordinary standalone startup path:

1. enumerate Android networks;
2. choose a Wi-Fi or Ethernet `Network` that is **not VPN**;
3. through that specific network, connect to the locally configured house LAN address on MPD port 6600;
4. require the normal `OK MPD ` greeting;
5. valid greeting over that bound physical-LAN path => HOUSE;
6. otherwise use STANDALONE behavior and request/use Tailscale as the existing app does.

Tailscale may already be connected when SMB Player opens. That must not affect the decision: HOUSE detection is explicitly bound to the non-VPN Wi-Fi/Ethernet network rather than the system/default route.

### HOUSE control/UI behavior

Keep the existing Browser and Now Playing UI model. In HOUSE, the playback authority changes from the phone's local Media3 player to the Pi's MPD session through `house-audio-server`.

- Browser/search/sort semantics remain the same.
- Tapping a song or PLAY LIST sends the corresponding ordered relative paths to the house queue instead of building a local SMB queue.
- Play/Pause, Previous/Next, Seek, Shuffle, Repeat, and deliberate queue sort operate on MPD.
- HOUSE Quit detaches the phone and must not Stop/Clear the house session.
- Merely opening the app, browsing, sorting, or attaching to an existing house session must not mutate playback.

### HOUSE phone-output rule

The phone's synchronized HOUSE output starts **muted** when the app enters HOUSE.

The phone automatically unmutes only when that phone itself initiates playback by:

- tapping a song;
- tapping PLAY LIST;
- pressing Play while MPD is paused or stopped.

Merely opening the app, browsing, sorting, or pressing Next/Previous while music is already playing leaves the phone's output mute state unchanged.

Provide an explicit HOUSE-only **Mute Output / Unmute Output** control in the lower Now Playing control strip. It controls only the phone renderer, never MPD/global audio.

### Passive-node default playlist selector

In HOUSE, reuse the Browser button position that is SMB in STANDALONE as the passive-node default selector.

- button displays `MP3s` or `Rap`;
- tapping toggles the Pi's persisted passive-node default between those two folders;
- the setting belongs to the Pi because it controls S3 behavior even when no phone is connected;
- changing it must **not** replace or restart the currently playing queue;
- it applies to the next genuinely fresh passive-S3 auto-start session.

STANDALONE keeps the normal SMB button and settings behavior.

## Near-term candidates

- `.m3u` / `.m3u8` playlist-file support.
- Optional library filename-cleanup script that reads metadata, performs a dry run, sanitizes filenames, prevents collisions, and logs reversible renames. This should remain separate from the player so the player itself stays read-only toward the music library.

## Later candidates

### House-audio-server integration — approved behavior, implementation pending

The Android Browser/search/sort and Now Playing UI must control the Pi's MPD session in automatic **HOUSE** mode while preserving the existing SMB/ExoPlayer path in **STANDALONE** mode. HOUSE presence is detected by binding a short probe to a non-VPN Wi-Fi/Ethernet network, connecting to the locally configured house LAN address on MPD port 6600, and requiring the normal `OK MPD ...` greeting. VPN-only reachability does not count. A temporary home outage is reconnection, not automatic independent playback. No mDNS/custom discovery handshake is planned unless testing shows it is actually needed.

Required additions include live shared-queue/state display and control, a separate synchronized phone receiver, HOUSE-only **Mute output / Unmute output**, independent controller/renderer presence, and same-song SMB/Tailscale continuation when an unmuted playing phone leaves home. Muted/paused/stopped phones remain silent. A phone attaching to fresh idle does not auto-start a track; only passive nodes do that. HOUSE Quit detaches the phone without sending global Stop/Clear. The Pi owns final-node finish/stop, muted-controller pause/retention, and a new random shuffle of the configured passive default for each genuinely fresh session. Completed drains end the old session; returns before track end preserve it. Chance repeats of the first song are allowed. The default folder setting persists, not the completed session's shuffled order/progress.

The browser controller and Windows player share the same Pi control contract. The basic `house-audio-server` HTTP API is runtime-proven on the permanent Pi for browse, queue/state, queue replacement/start index, Play/Pause/Stop, Seek, Previous/Next, Shuffle/Random, and Repeat. Renderer presence is runtime-proven through abrupt ESP32 hard power-off, and the passive-radio policy now works end to end: fresh-idle power-on starts default music, active-session join/rejoin preserves the queue, and v0.5.1 resumes an existing paused session when a passive radio appears.

Two independent ESP32-S3 + PCM5102A nodes have also passed the real audible synchronization test through different analog systems. Android should target the bridge rather than use MPD's native port as its normal control API. Server v0.7.0 implements persisted runtime passive-default selection through GET/POST `/settings` (42 local tests pass; Pi installation pending). Controller/output presence policy remains the main server prerequisite. Server v0.6.2 corrects completed-drain handling and fresh-session shuffle and is now deployed. Initial radio tests recorded same-song return after about 10 seconds unplugged and a different new song after about five minutes. The supplied `/session` action confirms early-return cancellation; a specific manually selected CD/Rap queue-to-default drain check remains open. Android Snapcast receiver integration and the remaining phone-specific edge decisions still need implementation/testing. These capabilities are not implemented by v0.3.8.

A small reliability issue is being tracked separately: occasional few-second silence on one ESP32 node or the other during otherwise synchronized playback. v0.6.0 adds unattended server-side diagnostics so a future occurrence can be correlated without babysitting ping windows.

Android requirements, current code hooks, and acceptance checklist: [CENTRAL_PLAYBACK.md](CENTRAL_PLAYBACK.md). Cross-project authority: [server session behavior](https://github.com/oolah10293/house-audio-server/blob/main/docs/SESSION_BEHAVIOR.md). Tracking: [Issue #1](https://github.com/oolah10293/smb-music-player/issues/1).

### Smart Shuffle

Potential design:

- strong positive weight for time since last play
- weak play-count influence
- temporary discovery bonus for new tracks
- recent-artist penalty
- weaker recent-album penalty
- soft, decaying penalty for repeated early skips
- randomness retained so the result is not deterministic

Regular Shuffle must remain available as a separate mode.

### Search

Possible future additions only if needed:

- metadata search (title / artist / album)
- recursive search
- persistent search index/database

The current current-folder filename/folder-name filter is intentionally simple and fast.

### Artwork

Possible fallback/cache improvements without changing audio files:

- embedded artwork first
- folder artwork (`cover.jpg`, `folder.jpg`) as fallback
- local cache for remote artwork

### Android Auto

A richer MediaLibrary browse tree could be added later. Current playback already uses a `MediaLibraryService`; v0.3.6 enabled explicit media audio focus, and vehicle routing plus steering-wheel track skip were confirmed in real use. v0.3.8 must preserve that behavior.



## v0.7.0 passive-default backend validation

The server dependency for the planned HOUSE `MP3s` / `Rap` Browser button is now field-proven on the permanent Pi. Changing the default did not interrupt the active song, and after a completed drain the next S3 startup used the saved `Rap` choice with a fresh session. The first observed track was Ludacris — *Southern Hospitality*.

The next server prerequisite for Android HOUSE work is controller presence/output-state handling.
