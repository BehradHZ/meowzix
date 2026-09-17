# Increment 1 — Local Music Library

## Objective
Discover, persist, reconcile, and display device-local audio exposed through Android MediaStore.

## Prerequisite
The repository initially contained no Android project, so the minimum Increment 0 foundation must be established first: Kotlin, Compose, Coroutines/Flow, Room, Hilt, DataStore, Media3 dependencies, tests, and package/domain boundaries.

## Implementation checklist

### A. Platform and permission boundary
- [ ] Declare `READ_MEDIA_AUDIO` for Android 13+
- [ ] Declare legacy `READ_EXTERNAL_STORAGE` only for Android versions that require it
- [ ] Centralize API-level permission selection
- [ ] Request permission contextually from the library screen
- [ ] Handle denied and later-revoked permission without crashing

### B. Canonical persistence model
- [ ] Implement `Track`, `TrackSource`, and `LocalMediaSource`
- [ ] Implement source type and availability enums
- [ ] Add Room converters for UUID/Instant/enums
- [ ] Add foreign keys and useful indexes
- [ ] Add observable and reconciliation DAO queries

### C. MediaStore ingestion
- [ ] Query MediaStore off the main thread
- [ ] Read title, artist, album, duration, MIME, size, display name, relative path, and modified time
- [ ] Build stable `content://` URIs
- [ ] Ignore invalid rows safely
- [ ] Keep artwork loading lazy

### D. Idempotent reconciliation
- [ ] Upsert discovered local tracks/sources
- [ ] Repeated scans must not duplicate rows
- [ ] Mark disappeared MediaStore sources as missing
- [ ] Preserve logical tracks for future alternate sources
- [ ] Return explicit scan statistics

### E. Repository boundary
- [ ] `observeTracks()`
- [ ] `refreshLocalMusic()`
- [ ] Keep MediaStore and DAOs out of Compose
- [ ] Bind implementation with Hilt

### F. Library UI
- [ ] Immutable ViewModel state
- [ ] Track list with title, artist, duration and lazy artwork
- [ ] Loading, permission-required, denied, empty and error states
- [ ] Manual rescan

### G. Verification
- [ ] DAO/repository tests
- [ ] Idempotence test
- [ ] Missing-file reconciliation test
- [ ] Permission helper tests
- [ ] 1,000-track reconciliation test without O(n²) work
- [ ] Assemble debug build
- [ ] Run unit tests

## Acceptance criteria
1. MP3/M4A/FLAC files visible through MediaStore can appear in the library.
2. Permission denial produces a usable state.
3. Permission revocation does not crash.
4. Removing a file is reflected after rescan.
5. Scanning/reconciliation stay off the main thread.
6. 1,000+ tracks remain responsive by design/tests.
7. Repeated scans are idempotent.
8. UI never directly depends on MediaStore or Room DAO APIs.

## Non-goals
- playback
- Telegram
- playlists
- recommendation
