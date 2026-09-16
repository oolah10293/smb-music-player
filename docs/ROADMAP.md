# Roadmap / ideas

These are ideas, not commitments. Proven playback behavior takes priority over feature count.

## Near-term candidates

- **Next revision:** restore the Browser search field's previous visual height. v0.3.6's plain white background removed the default EditText drawable/padding and made the field look vertically shorter. Keep the white background, but give the field an explicit minimum height (target: 48dp) without changing the rest of the Browser layout.

- `.m3u` / `.m3u8` playlist-file support.
- Continue validating v0.3.6 vehicle/Android Auto audio focus behavior.
- Optional library filename-cleanup script that reads metadata, performs a dry run, sanitizes filenames, prevents collisions, and logs reversible renames. This should remain separate from the player so the player itself stays read-only toward the music library.

## Later candidates

### Smart Shuffle

Potential design:

- strong positive weight for time since last play
- weak play-count influence
- temporary discovery bonus for new tracks
- recent-artist penalty
- weaker recent-album penalty
- soft, decaying penalty for repeated early skips
- randomness retained so the result is not deterministic

Regular Shuffle should remain available as a separate mode.

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

A richer MediaLibrary browse tree could be added later. Current playback already uses a `MediaLibraryService`; v0.3.6 additionally enables explicit media audio focus.
