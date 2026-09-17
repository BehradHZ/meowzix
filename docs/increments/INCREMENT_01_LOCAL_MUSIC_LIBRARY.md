# Increment 1 — Local Music Library

**Status:** In progress  
**Target:** Android  
**Primary outcome:** Meowzix discovers device-local audio through Android MediaStore, persists a local index with Room, and displays the library in a permission-aware Compose UI.

## 1. Preconditions

The repository started without an Android application skeleton. Increment 1 therefore requires the minimum project foundation before feature work begins.

- [ ] Create buildable Kotlin Android application.
- [ ] Configure Jetpack Compose.
- [ ] Configure Room and KSP.
- [ ] Configure Hilt dependency injection.
- [ ] Add Coroutines/Flow and lifecycle-aware Compose state collection.
- [ ] Add Media3, DataStore, WorkManager, and navigation dependencies required by the system architecture, even though they are not used by Increment 1 yet.
- [ ] Configure test dependencies.
- [ ] Establish package boundaries so UI does not access Room or MediaStore directly.
- [ ] Keep production Telegram credentials out of source control.

## 2. Permission contract

- [ ] Declare `READ_MEDIA_AUDIO` for Android 13+.
- [ ] Declare `READ_EXTERNAL_STORAGE` only for Android 12L and below using `maxSdkVersion=32`.
- [ ] Request only the permission required by the running API level.
- [ ] Show a usable permission-required state instead of a blank or broken screen.
- [ ] Handle denial without crashing.
- [ ] Re-check permission when the screen/app resumes so revocation is handled safely.
- [ ] Trigger a library refresh after permission is granted.
- [ ] Never reconcile the database against an empty scan caused by missing permission; otherwise a revoked permission could incorrectly erase the local index.

## 3. Canonical local-library data model

- [ ] Add canonical `Track` model with stable internal UUID.
- [ ] Add `TrackSource` model so local files are represented as sources rather than as track identity.
- [ ] Support at least `LOCAL_MEDIASTORE`, `TELEGRAM_REMOTE`, `TDLIB_LOCAL`, and `APP_OFFLINE_COPY` source types for forward compatibility.
- [ ] Add source availability states.
- [ ] Add `LocalMediaSource` extension data for MediaStore identity.
- [ ] Store timestamps as epoch milliseconds in Room and expose domain-friendly values at boundaries.
- [ ] Enforce foreign keys and useful unique indexes.
- [ ] Ensure deleting a physical source does not conceptually require deleting a canonical Track when another source may exist later.

## 4. Room persistence

- [ ] Create `TrackEntity`.
- [ ] Create `TrackSourceEntity`.
- [ ] Create `LocalMediaSourceEntity`.
- [ ] Create Room enum/type converters.
- [ ] Create DAOs for observing tracks and reconciling sources.
- [ ] Create `MeowzixDatabase`.
- [ ] Provide database/DAOs through Hilt.
- [ ] Keep Room types out of the Compose UI.
- [ ] Do not use destructive production migrations as the long-term migration strategy.

## 5. MediaStore scanner

- [ ] Query `MediaStore.Audio.Media` through `ContentResolver`.
- [ ] Run all scanning work off the main thread.
- [ ] Read `_ID`, title, artist, album, album ID, duration, track number, year, display name, MIME type, size, modification time, and relative path when supported.
- [ ] Build a stable MediaStore content URI from `_ID`.
- [ ] Ignore unusable zero-duration rows.
- [ ] Normalize Android's `<unknown>` metadata to `null`.
- [ ] Fall back from a missing title to a filename-derived title.
- [ ] Generate album-art URI lazily from album ID when available.
- [ ] Do not copy local audio into app storage.

## 6. Metadata normalization

- [ ] Trim leading/trailing whitespace.
- [ ] Collapse repeated whitespace.
- [ ] Unicode-normalize text used for matching/search.
- [ ] Lowercase normalized comparison fields with a locale-stable strategy.
- [ ] Keep display metadata separate from normalized metadata.
- [ ] Unit-test normalization edge cases.

## 7. Idempotent reconciliation

This is required even though the short Increment 1 deliverable list only says "incremental rescan". Without an explicit reconciliation contract, rescans can duplicate tracks or delete valid state.

- [ ] Match existing local sources by MediaStore content URI.
- [ ] Preserve the existing canonical Track UUID on rescan.
- [ ] Insert newly discovered files.
- [ ] Update metadata for existing files.
- [ ] Remove stale local-source rows after a successful authoritative scan.
- [ ] Delete an orphan canonical Track only when it has no remaining sources.
- [ ] Make repeated identical scans produce no duplicate rows.
- [ ] Return a refresh summary: scanned, added, updated, removed.
- [ ] Never perform stale-source removal after a failed or unauthorized scan.

## 8. Domain/repository boundary

- [ ] Define `MusicLibraryRepository` in the domain layer.
- [ ] Expose tracks as `Flow<List<Track>>`.
- [ ] Expose an explicit local-library refresh operation.
- [ ] Implement the repository in the data layer.
- [ ] Add use cases for observing tracks and refreshing the library.
- [ ] UI/ViewModel must not access DAO or `ContentResolver` directly.

## 9. Tracks screen

- [ ] Add lifecycle-aware `LibraryViewModel`.
- [ ] Represent loading, permission-required, empty, content, refreshing, and error states.
- [ ] Render tracks with `LazyColumn` so large libraries do not eagerly compose all rows.
- [ ] Show title, artist fallback, duration, and artwork when available.
- [ ] Use a stable track ID as the lazy-list key.
- [ ] Add manual Refresh action.
- [ ] Show a useful empty state after a valid zero-result scan.
- [ ] Show non-destructive refresh errors while preserving already indexed content.

## 10. Artwork

- [ ] Use the MediaStore album-art URI when album ID exists.
- [ ] Load artwork asynchronously.
- [ ] Use a lightweight fallback when artwork is absent or corrupt.
- [ ] Artwork failures must never block track listing.

## 11. Removal and external-change behavior

- [ ] A successful rescan discovers new files.
- [ ] A successful rescan updates changed metadata.
- [ ] A successful rescan removes sources for externally deleted files.
- [ ] Permission revocation does not crash or erase the index.
- [ ] Regranting permission and refreshing reconciles current device state.

## 12. Testing

- [ ] Unit-test metadata normalization.
- [ ] Unit-test reconciliation/idempotency planning.
- [ ] Test newly added source behavior.
- [ ] Test existing source update behavior.
- [ ] Test stale source removal behavior.
- [ ] Test repeated identical scan behavior.
- [ ] Test that an orphan Track is removed only after its final source disappears.
- [ ] Verify production code performs scan/reconciliation off the UI thread.
- [ ] Keep a synthetic 1,000-track dataset test/manual validation path; UI must use lazy rendering.

## 13. CI and build verification

- [ ] Add GitHub Actions build workflow.
- [ ] Build Debug APK / run assemble task.
- [ ] Run unit tests.
- [ ] Run Android lint.
- [ ] Keep the repository buildable after every meaningful commit.

## 14. Increment 1 definition of done

Increment 1 is complete when all of the following are true:

1. Local MP3/M4A/FLAC files exposed by MediaStore can appear in Meowzix.
2. Permission denial and revocation are safe and understandable.
3. The Room index survives app restart.
4. A successful rescan discovers additions, reconciles metadata changes, and removes deleted local sources.
5. Rescanning is idempotent and does not create duplicates.
6. Scanning and persistence work do not run on the main thread.
7. The Tracks UI remains structurally suitable for 1,000+ rows through lazy rendering.
8. Unit tests pass.
9. Android lint passes.
10. The app builds successfully in CI.

## Explicit non-goals for this increment

- Audio playback.
- Queue management.
- Pure Shuffle implementation.
- Telegram authentication or indexing.
- Offline Telegram downloads.
- Playlists.
- Smart Shuffle or model training.
