# SMB Music next iteration — hands-free outage recovery + independent internet radio
**Planning checkpoint: 2026-10-18. Status: PLAN ONLY; not implemented, built, packaged, installed, or released by this planning request.**

**Release authorization:** The user explicitly requires a separate go-ahead before **building, packaging, or sending any APK/release/installer**. A discussion, design approval, “sounds good,” or approval of documentation is **not** build authorization. Do not run an APK build (including CI-triggering changes) or deliver an artifact without that go-ahead. Code implementation is likewise not authorized by this documentation request. Read-only review and documentation/issue updates are authorized here.

This is the **canonical handoff plan** for the next *standalone SMB Music* iteration. Keep task-specific observations in [Issue #7](https://github.com/oolah10293/smb-music-player/issues/7) and [radio Issue #16](https://github.com/oolah10293/smb-music-player/issues/16), and do not recreate competing plans elsewhere. Refer to [ROADMAP.md](ROADMAP.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DESIGN_NOTES.md](DESIGN_NOTES.md), [VALIDATION_STATE.md](VALIDATION_STATE.md), and [TESTING.md](TESTING.md) for current source boundaries and field evidence.

## 1. Confirmed baseline and architectural boundaries

**Verified from the linked GitHub source (not new phone validation):**

- The default branch contains standalone **SMB Music v0.5.1**, package `com.smbmusic.player`, Android API 26+, with a single Media3/ExoPlayer `PlaybackService`/`MediaLibrarySession`, `SmbDataSource`/jcifs, and `StandaloneSessionStore`.
- Current file playback and SMB recovery use `app/src/main/java/com/smbmusic/player/PlaybackService.kt`; browse/queue creation are in `MainActivity.kt`; Now Playing is in `NowPlayingActivity.kt`.
- The accepted **Country Buffer** values for file playback are 120 s minimum, 600 s maximum, 32 MiB target, 3 s normal start and ~20 s recovery refill, with 64 KiB SMB read-ahead. Preserve them unless specifically authorized and field-tested.
- `StandaloneSessionStore` currently **rejects non-`smb://` URIs** when loading its queue. `DefaultMediaSourceFactory` currently receives **only** `SmbDataSource.Factory`. Both need intentional source-aware handling before radio can work.
- The v0.5.1 field report dated 2026-10-09 says **Bluetooth and Tailscale connectivity were working** and data/battery use was acceptable. It separately reports **two failures of automatic cellular-outage recovery**. This is field feedback, *not* a captured Android trace or proof of either suspected cause.
- The independent House Music **v0.4.0 radio implementation** is visible on [`house-v0.4.0-radio-controls`](https://github.com/oolah10293/smb-music-player/tree/house-v0.4.0-radio-controls) in **this same GitHub repository**, particularly `house-app/src/main/java/com/housemusic/player/{HousePageActivity,RadioFragment,NowPlayingFragment,RadioNowPlayingView,RadioStationOrder}.kt` and `house-app/src/main/res/layout/activity_radio.xml`. It is a **UI/behavior reference**; its Pi-specific `HouseRuntime`, controller API, Snapcast client, audio sync, and phone renderer logic must **not** be copied into standalone SMB Music.
- The separate [house-audio-server](https://github.com/oolah10293/house-audio-server) already serves shared HOUSE radio stations to House Music. **SMB Music radio must play directly on the Android phone** and have *no runtime dependence* on that server, the Pi, MPD, Snapserver, or HOUSE session state.

**Confirmed product rules:** folders are playlists; SMB Music and House Music remain **two independent apps** with no handoff, shared queue, playback transfer, or ability for SMB Music to reset HOUSE. Keep existing vehicle/lock-screen/Garmin/Android Auto/Bluetooth media controls, Tailscale SMB behavior, sort/search, Repeat All, explicit Quit, and radio/file source boundaries. The requested work comprises (A) *hands-free recovery as highest priority* and (B) *internet radio similar to House Music*. The precise radio UI layout and optional import workflow below are **proposals**, not separately approved designs.

## 2. Priority A — resolve Issue #7 BEFORE adding radio

**Original issue:** [#7 — cellular outage requires screen unlock or manual Play](https://github.com/oolah10293/smb-music-player/issues/7). The 2026-10-09 road report identifies **two distinct failures**: one recovery only after **unlocking** the phone with the app already open, and another requiring **manual Play**. Reproduction variables (installed APK hash, exact OS, audio focus, Bluetooth route events and Android process/service lifetime) were not measured in that report; do not claim a root cause was proven.

### A1. Existing implementation and hypotheses to investigate (not diagnoses)

1. **Sleep/timer scheduling:** `beginOutageRecovery()` internally pauses ExoPlayer; the retry/watchdog `Handler.postAtTime` calls use `SystemClock.uptimeMillis()`. A foreground media service is not itself proof that CPU scheduling proceeds during deep sleep. Media3's player-managed `WAKE_MODE_NETWORK` locks apply only while the player is READY/BUFFERING **and** `playWhenReady=true`; internal pause may drop them. The service currently has **no dedicated recovery wakelock** visible in the reviewed source. This is a plausible explanation for unlock-triggered progress, **not a measured cause**.
2. **Resume-intent loss:** During recovery, `onPlayWhenReadyChanged(false)` may call `pauseRecovery()` for AUDIO_FOCUS_LOSS, AUDIO_BECOMING_NOISY or SUPPRESSED_TOO_LONG; that method sets `resumeShouldPlay=false`, marks `recoveryPaused=true`, and cancels retries. All subsequent network-return probes exit if `recoveryPaused`. Investigate which events are genuine user/output holds and which may be incidental. **Do not** override legitimate audio-focus loss or play through the wrong output.
3. **Background/service state:** Inspect `onStartCommand`, `keepStandaloneReady`, `updateStandbyNotification`, service/process restoration and `StandalonePlaybackIntent`. Differentiate a stopped/paused service from a currently running one, Android background-start denial, and Bluetooth reconnect. `BluetoothResumeReceiver` cannot be the only wake trigger when the **same audio device remains connected** throughout a cellular outage.
4. `NowPlayingActivity.onResume` does not explicitly trigger recovery, so **do not assume** an Activity foreground callback explains the unlocking observation.

Android reference material: [ExoPlayer.Builder.setWakeMode](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder), [Handler](https://developer.android.com/reference/android/os/Handler), and [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager). Review current Android API/Doze/foreground-service limitations before locking in any timer strategy.

### A2. Implementation approach to investigate and test

- Make a service-owned **playback-intent/state model** that distinguishes explicit user Play/Pause/Stop/Quit, automatic Bluetooth route hold, audio-focus suppression, network outage, *internal buffering pause*, and source switch. Internal `player.pause()` for buffer rebuilding must **not** erase a prior authorized intent to resume. Explicit Pause/Stop/Quit remain authoritative; route/focus holds may resume **only** when eligible.
- Preserve the existing **same song, same position, same sorted/filtered queue, Shuffle and Repeat All** during SMB outages. Continue retry schedule `1, 2, 5, 10, 15 s recurring`, probe the actual SMB file, and silently restore ~20 s buffer before audio resumes. Keep probe-generation rejection, timeouts, no-progress watchdog, and interrupted-refill retry.
- Build a **screen-off reliable recovery strategy**, owned by `PlaybackService` and not the Activity. Test a dedicated, tightly lifecycle-controlled **partial wake lock for active recovery work** and an appropriate wake/retry mechanism for sleeping intervals as alternatives/complements. Do not assume `Handler` scheduling or a foreground notification alone meets this requirement; conversely **do not hold a wake lock indefinitely** through hours of no coverage without understanding battery cost. AlarmManager/Doze timing, permission requirements and throttling need investigation; avoid promising a 15 s wall-clock retry during Doze without proof.
- Drive an early *actual SMB probe* from relevant network-return events; do not equate Android's connected/VPN notification with successful SMB access. Continue using existing paced Tailscale connect requests **for SMB only**; don't roll back the user-accepted Bluetooth/Tailscale fixes.
- Ensure recovery survives extended locked-screen time and **multiple sequential outages**, including a new failure during refill. Keep any pending intent if a recoverable foreground promotion/focus attempt is blocked, and log why a retry awaits eligibility; do not silently require another tap.
- Add bounded, privacy-safe diagnostic events: when source/outage/attempt begins, elapsed vs uptime scheduling, probe result/timeouts, buffer refill progress, wake/service/focus/route status, recovery-paused reason, and explicit vs internal intent changes. Redact file paths, server hosts, full station URLs, tokens and credentials. Avoid high-volume polling/logging and preserve accepted data/battery use.
- Implement small, deterministic tests of the **state transitions and cancel/resume eligibility**, plus clock/sleep/retry scheduling where practical, before device tests. Treat simulated tests as nonproof of actual locked-phone behavior.

### A3. Non-negotiable acceptance

While **stationary**, start an SMB folder over the usual cellular/Tailscale path, leave Bluetooth audio connected and the screen **locked**, remove the actual network/SMB route until the Country Buffer is exhausted, then restore service. It must recover the **same track and position** after the usual buffer rebuild, **without unlocking, foregrounding SMB Music or pressing Play**. Repeat after a prolonged screen-off interval, through several outages, with return during refill, and without any new Bluetooth connection event. Verify no accidental speaker playback, no resume following explicit Pause/Stop/Quit, and no skip to the next file. Record phone/OS, timeline, diagnostic output, and battery/data deltas; use controlled stationary tests, never phone interaction while driving. **The issue remains OPEN until physical locked-screen acceptance passes.**

## 3. Priority B — stand-alone internet radio analogous to House Music

### B1. Playback architecture

- Reuse **the existing SMB Music Media3 session/player/service**, rather than adding a competing media session. Introduce a source classification (`SMB_FILE` versus `LIVE_RADIO`) and route media loading by URI/source: `smb://` through existing `SmbDataSource`, HTTP/HTTPS streams through an HTTP data source. Investigate the simplest reliable integration with `DefaultMediaSourceFactory` and a scheme-routing `DataSource.Factory`; do not change SMB transport behavior.
- Support **direct** HTTP/HTTPS MP3/AAC/other decoder-supported radio streams and HLS live URLs (Media3 HLS module if required). Check extensionless stream MIME handling and redirects. A station's home page is **not** the stream URL. House Music explicitly rejects non-HLS M3U/PLS directories; do not silently promise those formats. Explain invalid/unsupported links without corrupting existing playback.
- Radio is **live**, with a radio-appropriate small/fresh buffer and independent retry policy. Do not blindly apply the SMB 120–600 s Country Buffer and 20 s same-file seek/refill algorithm to radio, nor allow recovery to resume minutes behind the live broadcast. If HLS offers a live edge, rejoin it as appropriate; for raw streams open the current stream afresh.
- **Radio errors must never invoke `smb.probeFile`, Tailscale reconnect just because an HTTP station failed, or SMB same-position seeking.** Conversely file errors must not follow the radio retry path. Stream interruption, screen lock, Bluetooth changes and network restoration should reconnect while eligible, with explicit Stop/Quit/Pause and audio focus still controlling audibility. No busy-spin/retry storms when a station is dead.
- On switching from radio back to a selected SMB folder/track, close/cancel radio-specific callbacks and adopt the newly selected file queue; never let a delayed stream response hijack a subsequent selection. Preserve app/notification/lockscreen/Garmin controls appropriate to each source.

### B2. Familiar Radio page and station management (UI target)

Reuse *behavior/design* from House Music v0.4.0 radio UI as a reference, **not its Pi integration**:

- Proposed navigation: **Browse ↔ Now Playing ↔ Radio**, using the accepted House Music horizontal swipe behavior. Preserve SMB Browser scroll position, current folder/search/sort, player lifetime, current Android layout controls, and keyboard gestures. The existing SMB application has two Activities rather than the House pager; choose a low-regression conversion strategy, not a gratuitous rewrite.
- Radio page: direct URL entry at the current 40 dp search-field-style height; **Add & Play**; saved-station list; tap to play, long-press rename, × delete and temporary Undo; pull-down refresh if useful for local state. Highlight active station. New additions should appear at the top **for the current Radio visit**.
- Persist saved station name, URL and durable ID **on the phone**, independently from House/Pi. Detect duplicate URLs and validate input (HTTP/HTTPS only, no malformed URLs, no embedded credentials). Start from useful stream-provided station names when available, otherwise a clearly labeled URL/host fallback; allow user rename.
- House-style **listening-time ranking** is a proposed parity feature: count only actually audible/advancing playback (not paused, stalled or disconnected), sort highest-first then alphabetical on opening Radio, keep row positions stable during the same visit, and do not invent historical totals. Android can only count playback it observes; **it cannot reconstruct House's Pi-based listening history when SMB Music was not running**.
- Optional, **one-time user-initiated import** from House Music's server `GET /radio/stations` on home LAN, or a manual paste/export path if needed. No live synchronization, shared writes, permanent Pi setting, requirement to be home, or server deployment is part of normal SMB radio playback. Confirm this optional UI before implementing; do **not** silently read/copy Pi bookmarks.

### B3. Now Playing / metadata / controls

- Use a clear **LIVE** radio view with saved station name, current broadcast-supplied title/artist/album **when present**, broadcast/station identification and bitrate/format **only when measured or supplied**. Missing fields stay visibly unavailable; do not guess. Existing SMB file Now Playing artwork, title priority and controls remain unchanged.
- Review ExoPlayer Media3 ICY metadata and HLS timed ID3 handling; the House server's WXDX/iHeart string normalization in [`radio_metadata.py`](https://github.com/oolah10293/house-audio-server/blob/main/radio_metadata.py) is a useful **parser/behavior reference**, not guaranteed plug-and-play for Android metadata event types. Test with real stream captures. Do not mislabel station slogans, ads, or mixed metadata as a song.
- **Last played:** proposed House parity is a single previous *identified* radio song, retained only after **more than ten seconds of confirmed advancing playback**, with station and local timestamp. Short/blank metadata does not erase the previous qualified song. Persist when feasible while app service is alive; unlike House, a phone cannot observe songs played by Pi/S3 nodes or while its own radio playback process is absent. Distinguish proposal from physical verification.
- During radio: Play/Pause and Quit function; hide or disable seek, Previous/Next, Shuffle, Repeat and file-queue Sort. Pause then Play rejoins **current live audio**. Avoid meaningless seek progress; center Play/Pause in the relevant control area (House has a mute/gear row, but SMB Music must **not acquire HOUSE-only mute/±1000 ms sync controls** by accident). Notification/vehicle media commands need honest radio capability advertisement.
- During file playback: preserve all familiar controls, shared file Sort, normal seeking, Shuffle, fixed Repeat All, queue rotation, audio metadata and Country Buffer.
- On radio/source errors, show **Connecting / Retrying / Paused / Live / Unavailable** and the latest bounded error diagnostics without overstating connection health.

### B4. **Resume Playlist** after listening to radio

- Before *first* switching an active SMB file session to radio, capture an immutable **SMB queue snapshot**: full ordered item identities including duplicates, current item/index, exact position, Shuffle, Repeat All policy, and whether playing/explicitly paused. Do **not** replace that snapshot when switching among radio stations.
- With a saved snapshot, show **Resume Playlist** beside Quit on radio Now Playing, as in House Music v0.4.0, instead of showing the file Sort control. No saved SMB queue => hide/disable that action. One tap stops/cancels radio and restores the queue and position; resume only subject to the user's stored intent and current audio route/focus eligibility. The radio LIVE-only controls revert to normal file controls.
- Selecting a new folder or track in Browse while radio plays is a **new playlist request** and replaces the previous snapshot/queue, not a spontaneous restore of an older queue.
- Extend `StandaloneSessionStore` through a **versioned, backward-compatible data model**: current active source (SMB/radio), station identity/live URL if suitable, preserved file queue/snapshot, explicit Stop/Quit, and enough state for process recreation without treating radio as an `smb://` file. Ensure v0.5.1 retained queues load correctly; do not silently discard old saved sessions. An explicit Quit must **not** auto-resurrect either source on restart.
- Handle source-switch races, async player errors, service recreation, Bluetooth disconnect/reconnect and app reopening without unexpectedly restarting the wrong source. Keep all state phone-local, not synchronized to House.

### B5. Android/compatibility/security decisions to verify

- Android `INTERNET` permission is already in the standalone manifest. Evaluate cleartext HTTP support: Android currently does not set `usesCleartextTraffic=true` for SMB Music, and some radio streams are HTTP-only. Choose and document an appropriate network-security/cleartext policy; retain HTTPS when offered.
- Match the current Android 8+ baseline and Media3 version unless a tested dependency change is needed; add the HLS dependency if implementation actually needs it. Verify MIME/redirect/ICY header handling across Android versions and known real stations.
- Do not leak stream URLs (which may embed query tokens), SMB credentials, NAS paths or station headers into notification diagnostics/issues. Station URLs are app-private bookmarks, not shared with House. No automatic Pi server changes, MPD/Snapserver changes, or ESP32 builds.

### B6. Acceptance to run after implementation authorization

1. On a device with House/Pi **off/unreachable**, paste a valid direct station URL, save, play, pause/rejoin live, rename, delete/Undo, restart SMB Music and play the saved station again.
2. Use at least a direct HTTP MP3/AAC stream and an HLS HTTPS station (subject to actually valid test URLs); verify wrong URLs, timeouts, redirects, invalid MIME and unsupported directories return a clear error without hangs.
3. Lock the screen, connect to Bluetooth/vehicle, simulate network loss/return repeatedly, verify **hands-free live rejoin** with same output and no action in the UI. Explicit Pause/Stop/Quit and Bluetooth disconnect must prevent accidental speaker output.
4. Play a large sorted/shuffled NAS folder at a measured position, switch among multiple radio stations, then **Resume Playlist**. Verify exact queue order, current track, position, sorting, Shuffle, Repeat All, metadata and no reset of House Music's session.
5. Inspect station/track info supplied by radio; verify absence isn't filled with guesses. Test one qualifying >10 s prior-song transition, an unqualified short title, missing metadata, and persistence behavior without inventing history.
6. Reopen the app, repeat source switches while the network fails, and restore any retained v0.5.1 file queue. Validate Android API-26 gates and the previously accepted Android Auto, Garmin, notification, screen-off, Browse and Country Buffer regressions.
7. Compare battery/data use in normal radio and prolonged SMB recovery; record exact counter windows and do not infer a per-hour rate from cumulative counters.

## 4. Sequence and release gates

0. **Planning only (this change):** commit this canonical plan, cross-reference it from roadmap/issues, then continue work in ChatGPT Work. No implementation/build authorization implied.
1. **Recovery analysis & minimal fix:** start from current `main`, reproduce or instrument Issue #7, choose tested sleep/retry/intent strategy, add deterministic unit tests; keep transport/audio behavior stable. Do this **before** adding radio. A clean compile or unit test alone does not close #7.
2. **Recovery physical gate:** stationary phone, screen locked, repeated outages with same Bluetooth output, automatic same-position resume; collect diagnostics and resource-use observations; only mark #7 resolved after actual device acceptance.
3. **Radio engine & storage:** introduce source routing, HTTP/HLS, isolated retries and versioned station/session persistence without touching HOUSE. Unit/integration tests first.
4. **Radio page & controls:** reuse accepted House radio UI patterns, station CRUD/ranking/metadata, and source-specific Now Playing behavior without HOUSE-only capabilities.
5. **Source-switch/Resume Playlist:** protect saved SMB queue/position and explicit intent, prevent stale async work/overwrites, test process recreation.
6. **Combined regression & field gate:** both modes, locked-screen cellular recovery, vehicle/Bluetooth controls, audio focus, privacy, battery/data, app separation, Android 8+ and large queues.
7. **Build/package/delivery GATE:** **STOP AND ASK** for explicit build authorization before building, invoking APK-generating CI, signing, packaging sources, publishing/uploading, or sending an APK. Even if design, code or tests were approved, no release/installer is authorized implicitly. Record actual version, tests, signatures and device results only after performed.

**Do not widen the scope** to House server radio rewrites, Pi deploys, ESP32 firmware, an app handoff system, directory/database changes, or unrelated UI features. If new evidence contradicts the proposed approach, update this plan/issue with the evidence before redesigning.

## 5. Key links for a fresh Work chat

- [SMB Music repository / default branch](https://github.com/oolah10293/smb-music-player)
- [Issue #16 — standalone radio planning](https://github.com/oolah10293/smb-music-player/issues/16)
- [Issue #7 — high-priority cellular recovery](https://github.com/oolah10293/smb-music-player/issues/7)
- [House Music v0.4.0 radio reference branch](https://github.com/oolah10293/smb-music-player/tree/house-v0.4.0-radio-controls)
- [House Music v0.4.0 radio release description](https://github.com/oolah10293/smb-music-player/blob/house-v0.4.0-radio-controls/house-app/docs/RELEASE_0.4.0.md)
- [House radio server source (reference only)](https://github.com/oolah10293/house-audio-server)
- [Existing field evidence](VALIDATION_STATE.md), [SMB recovery architecture](OUTAGE_RECOVERY_PLAN.md), [regression suite](TESTING.md)
