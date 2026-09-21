# Central playback integration plan

This is a future architecture direction, not part of the current local-player revision.

## Goal

Allow SMB Music to keep its existing folder-first browsing/search/sort workflow while optionally controlling a central playback system that can feed synchronized audio endpoints around the house.

The phone must remain optional to the audio path. Once central playback has started, closing the Android app must not stop playback.

## Two playback modes

The intended UI model is simple:

- **This Phone** — current behavior. Android Media3/ExoPlayer streams the selected SMB files locally.
- **House / Central** — the Android app becomes a remote controller. The central server owns the queue, playback state, decoding/streaming, and synchronization to audio nodes.

The same Browser UI should be reused for both modes. Folder structure remains the playlist structure.

## Architectural direction

Introduce a playback abstraction rather than teaching the Browser two unrelated code paths.

Conceptually:

```
Browser / Search / Sort
        |
        v
PlaybackBackend
   |          |
   v          v
Local       Central
Media3      server API
```

Candidate backend operations:

- load/play a queue with a selected start index
- play
- pause
- previous
- next
- seek
- set/read shuffle state
- read current track, queue, position, duration, and playback state

The local implementation should wrap the existing Media3 controller/service and preserve current behavior. The central implementation should speak the server protocol.

## Central-server authority

In central mode, the server is authoritative for:

- active queue and current item
- playback position and play/pause state
- shuffle/order state
- audio decoding and stream production
- synchronized output-node timing

The Android app should send commands and display server state; it should not be required to remain running.

Multiple controllers should be able to attach to the same session. A Windows controller/player and future control surfaces should use the same central protocol rather than creating separate ecosystems.

## Protocol direction

Exact protocol is intentionally not locked yet because the ESP32/server prototype still has to prove the audio path.

A likely split is:

- **HTTP or similarly simple request/response API** for browse-independent commands and queue submission.
- **WebSocket or equivalent persistent state channel** for current track, position, playback state, queue changes, node state, and controller synchronization.

Avoid rapid polling when a push/state channel can provide the same information.

## Queue and library semantics

The central mode must preserve the rules that make SMB Music useful:

- folders are playlists;
- search-filtered results can become an instant temporary queue;
- active sort order defines queue order;
- regular shuffle remains available;
- dirty or incomplete metadata must not prevent playback;
- filename remains a valid fallback identity.

The central server should not require a metadata-first database just to accept playback commands from SMB Music.

## ESP32 relationship

Synchronized ESP32 playback is a server/output-node problem, not an Android timing problem.

SMB Music should tell the central server **what to play and what command to perform**. The central server should decide how audio is buffered, timestamped, streamed, and synchronized across ESP32 endpoints.

This keeps the Android app independent of the eventual synchronization transport and lets the ESP32 Radio Audio Setup project evolve without forcing rewrites of the Browser UI.

## Integration sequence

1. Prove the central server and one ESP32 audio endpoint independently.
2. Define the smallest stable central-control API.
3. Add a `PlaybackBackend` boundary around the Android app's existing local Media3 control path without changing local behavior.
4. Implement `CentralPlaybackBackend` against the proven server API.
5. Add a simple playback-target selector such as **This Phone / House**.
6. Add live state synchronization so multiple controllers remain consistent.
7. Only then consider richer node/room selection in the Android UI.

## Non-goals for the first integration

- Replacing the existing local Media3 player.
- Making the phone a required relay for house audio.
- Moving ESP32 synchronization logic into Android.
- Replacing folder-first browsing with metadata-centric playlists.
- Changing Country Buffer™, SMB recovery, or other proven local-playback behavior merely to support central mode.

## Design rule

Central playback is an additional backend, not a replacement for the current player. Local playback must remain independently useful and proven.
