# Increment 6 — Unified Track Model and Deduplication

## Status

Implemented on 2026-09-21.

## Implementation

- Restored the source-agnostic library and local-first playback priority.
- Added exact SHA-256 matching when source hashes exist.
- Added conservative provisional matching on normalized title, artist, and duration.
- Ambiguous matches and title-only matches remain separate Tracks.
- Local and Telegram reconciliation reuse a canonical Track and remain idempotent.
- Missing sources do not delete their Track or sibling sources.
- Added a repository unmerge hook that moves one source to a new Track.

## Verification

- JVM matcher tests cover exact hash, metadata matching, title collision, and ambiguity.
- Room repository test covers one local plus one remote source on one Track and local playback priority.
- `:app:testDebugUnitTest` and `:app:assembleDebug` pass.
- Connected Room test requires an emulator or device and remains part of the full Android gate.
