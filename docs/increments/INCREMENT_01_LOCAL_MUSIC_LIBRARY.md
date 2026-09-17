# Increment 1 — Local Music Library

## Objective
Discover, persist, reconcile, and display device-local audio exposed through Android MediaStore.

## Prerequisite
Increment 0 is complete. Its checked implementation and acceptance list is recorded in `INCREMENT_00_PROJECT_FOUNDATION.md`.

## Implementation checklist

### A. Platform and permission boundary
- [x] Declare `READ_MEDIA_AUDIO` for Android 13+
- [x] Declare legacy `READ_EXTERNAL_STORAGE` only for Android versions that require it
- [x] Centralize API-level permission selection
- [x] Request permission contextually from the library screen
- [x] Handle denied and later-revoked permission without crashing
- [x] Provide a direct recovery path to app settings after denial

### B. Canonical persistence model
- [x] Implement `Track`, `TrackSource`, and `LocalMediaSource`
- [x] Implement source type and availability enums
- [x] Persist UUID/Instant values as canonical strings/epoch values and convert enums with Room converters
- [x] Add foreign keys and useful indexes
- [x] Enforce one row per physical local `contentUri`
- [x] Add observable and reconciliation DAO queries
- [x] Export versioned Room schemas and validate migration 1→2

### C. MediaStore ingestion
- [x] Query the merged external MediaStore audio volume off the main thread
- [x] Read title, artist, album, duration, MIME, size, display name, relative path, and modified time
- [x] Build stable `content://` URIs
- [x] Ignore invalid rows safely
- [x] Keep artwork loading lazy and off the main thread

### D. Idempotent reconciliation
- [x] Upsert discovered local tracks/sources
- [x] Repeated scans do not duplicate rows
- [x] Reconcile new, changed, and removed MediaStore rows on rescan
- [x] Mark disappeared MediaStore sources as missing
- [x] Preserve logical tracks for future alternate sources
- [x] Return explicit scan statistics

### E. Repository boundary
- [x] `observeTracks()`
- [x] `refreshLocalMusic()`
- [x] Keep MediaStore and DAOs out of Compose
- [x] Bind scanner and repository implementations with Hilt

### F. Library UI
- [x] Immutable ViewModel state
- [x] Track list with title, artist, duration and lazy artwork
- [x] Loading, permission-required, denied, empty and error states
- [x] Manual rescan

### G. Verification
- [x] DAO/repository device tests
- [x] Idempotence and metadata-update test
- [x] Missing-file reconciliation test
- [x] Permission helper tests
- [x] 1,000-track reconciliation test without O(n²) work
- [x] Real MP3, M4A, and FLAC MediaStore smoke test
- [x] App-restart index persistence smoke test
- [x] Permission-revocation smoke test
- [x] Assemble, lint, unit-test, and connected-device gates

## Acceptance criteria
1. MP3/M4A/FLAC files visible through MediaStore can appear in the library.
2. Permission denial produces a usable state.
3. Permission revocation does not crash.
4. Removing a file is reflected after rescan.
5. Scanning/reconciliation stay off the main thread.
6. 1,000+ tracks remain responsive by design/tests.
7. Repeated scans are idempotent.
8. UI never directly depends on MediaStore or Room DAO APIs.

## Verification snapshot — 2026-09-17

- 10 JVM tests passed.
- 4 Android emulator tests passed, including the Room migration and 1,000-track reconciliation.
- Real MP3, M4A, and FLAC fixtures appeared in the Android 16 emulator UI with title, artist, and duration.
- All three tracks remained visible after a cold restart.
- Deleting the MP3 externally and rescanning removed it from the visible library while the other formats remained.
- Revoking audio permission left the app resumed in a usable permission state without a crash.
- Debug assembly and Android lint completed with zero errors.

## Non-goals
- playback
- Telegram
- playlists
- recommendation
