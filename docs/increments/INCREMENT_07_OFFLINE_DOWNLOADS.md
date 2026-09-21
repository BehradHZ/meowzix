# Increment 7 — Offline Download Manager

## Status

Implemented on 2026-09-21. Live Telegram/network interruption validation still requires a configured test account.

## Implementation

- Added persistent Room download records and a non-destructive `3 -> 4` migration.
- Added TDLib pin, progress, cancel, retry, and remove operations.
- Pinned bytes are copied into app-owned internal storage and represented by an `APP_OFFLINE_COPY` source.
- Copies are finalized through a temporary file and SHA-256 is recorded.
- Added Pin Offline track action and Offline screen.
- Cache eviction only selects unpinned app records; MediaStore files are never deletion candidates.
- Removing a copy deletes only a validated child of the app offline directory and never deletes a Telegram message.

## Verification

- Unit coverage proves pinned records are not cache-eviction candidates.
- Migration instrumentation covers schema `3 -> 4`.
- Debug unit tests, Android-test compilation, and APK assembly pass.
