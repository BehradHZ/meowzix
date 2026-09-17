# Meowzix

Meowzix is a local-first Android music player that unifies device-local audio with Telegram-backed music, offline playback, true shuffle, and adaptive personal playback.

## Current development status

Development is proceeding increment-by-increment. The current implementation target is **Increment 1 — Local Music Library**.

See [`docs/increments/INCREMENT_01_LOCAL_MUSIC_LIBRARY.md`](docs/increments/INCREMENT_01_LOCAL_MUSIC_LIBRARY.md) for the executable implementation checklist.

## Planned stack

- Kotlin
- Jetpack Compose
- Room
- Hilt
- Media3 / ExoPlayer
- Android MediaStore
- TDLib in later increments

## Principles

- Local-first behavior
- Offline reliability
- Canonical tracks separated from physical/remote sources
- Pure Shuffle remains genuinely random
- Smart playback is a separate adaptive mode
- No custom backend is required for the core product
