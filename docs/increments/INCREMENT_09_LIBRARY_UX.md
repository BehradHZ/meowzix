# Increment 9 — Library UX, Search, Favorites, and Playlists

## Status

Implemented on 2026-09-21. Recently Played is supplied by Increment 10's listening-history store.

## Implementation

- Unified Tracks, Artists, Albums, Favorites, Playlists, Offline, and availability-filtered views.
- Local title/artist/album search.
- Persistent favorite mutation.
- Persistent playlists with add, remove, reorder, queue-to-playlist, ordered playback, and Pure Shuffle playback.
- Visible Offline, Cloud, and Unavailable source status.
- Telegram and app-owned tracks remain visible when local audio permission is denied.
- Non-destructive Room migration `4 -> 5` adds stable playlist positions and foreign keys.

## Verification

- Instrumentation tests cover favorite persistence and playlist ordering/reordering.
- Migration instrumentation covers schema `4 -> 5`.
- Debug unit tests, Android-test compilation, and APK assembly pass.
