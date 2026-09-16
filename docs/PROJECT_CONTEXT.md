# Project context

This document records the product-level reasons behind the code so future changes do not optimize away the behavior that made the player useful.

## Why this player exists

The project started because a conventional Android music player was close to the desired workflow but had two important failure modes:

1. the desired folder playback order could not be expressed cleanly, especially modified-date ordering;
2. a temporary SMB/network interruption could be treated like a sequence of bad media files, causing the player to skip forward repeatedly instead of waiting for the connection to recover.

SMB Music was built around the opposite assumptions: the remote file is probably fine, the network may be temporary garbage, and the correct response is usually to hold the current track and recover.

## Core product goals

- Stream directly from an SMB library rather than require a local library sync.
- Keep the music library **read-only from the player**.
- Make folder browsing and modified-date sorting useful on large, imperfectly organized collections.
- Preserve the current track and position through transient network failures.
- Buffer aggressively when bandwidth is available.
- Keep normal media controls familiar by using stock Media3 behavior where possible.
- Prefer simple, visible behavior over opaque heuristics.
- Keep regular Shuffle available even if smarter shuffle modes are added later.

## Search as a temporary playlist builder

The current-folder search became more useful than a conventional search screen because it directly filters the playable queue.

A typical workflow is:

1. open a large mixed folder;
2. type one or more filename terms;
3. see matching audio files/subfolders immediately;
4. press **PLAY LIST**;
5. play only the matching audio files in the active sort order.

Multi-term searches are AND searches. For example, `blink 182` requires both terms but does not care whether the filename spells the separator as a space, hyphen, underscore, punctuation, or nothing at all.

The search intentionally does not recurse or build a database yet. Its speed and simplicity come from filtering the entries already loaded for the current folder.

## Library metadata is not assumed to be clean

Real-world files can have missing, partial, or incorrect tags. The player therefore:

- lets extractor metadata provide title/artist/album when present;
- falls back to the filename for title display;
- hides missing secondary fields rather than inventing values;
- never writes metadata back to the library.

A separate metadata/filename cleanup utility may be useful, but it should remain outside the playback app.

## Network philosophy

There are two independent forms of buffering:

- **SMB read-ahead** at the byte-stream layer to avoid turning small extractor reads into high-latency SMB round trips.
- **Country Buffer™** at the playback layer to store a large amount of playable audio whenever the network is healthy.

Do not collapse these into one setting; they solve different problems.

## Failure philosophy

A network error is not a reason to advance the queue. Unless there is evidence the media file itself is invalid, the player should retain:

- the media item;
- the queue position;
- the playback position;
- the user's play/pause intent.

Then it should wait for the transport to return and resume that same track.

## UI philosophy

The interface is intentionally small:

- Browser for connection/path, sort, search, and queue creation.
- Now Playing for metadata, artwork, stock playback controls, active-queue sorting, Browse, and Quit.

Several layout choices exist because stock Media3 behavior was tested and preferred. In particular, keep the dedicated controller region large enough that Media3 does not enter minimal mode and hide Previous/Next.

## Future features should earn their complexity

Good candidates include playlist-file support and an optional Smart Shuffle history database. They should not compromise direct SMB playback, exact-track recovery, regular Shuffle, or the current fast search/queue workflow.
