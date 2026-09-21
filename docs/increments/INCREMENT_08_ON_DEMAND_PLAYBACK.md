# Increment 8 — Telegram On-Demand Playback and Prefetch

## Status

Implemented on 2026-09-21. Live network-loss and metered-network device scenarios require a configured Telegram test account.

## Implementation

- Remote Play keeps the existing high-priority download-on-play flow and automatic playback.
- Player exposes Downloading and actionable failure states.
- Next queue item is prefetched at low TDLib priority and retained as a local TDLib source.
- Queue changes cancel the previous prefetch request.
- DataStore persists offline mode, Wi-Fi-only downloads, prefetch, and metered-prefetch settings.
- Network policy blocks automatic downloads in offline mode, without connectivity, or against metered policy.
- Offline settings are user-visible on the Offline screen.

## Verification

- Unit tests cover offline mode, no-network, Wi-Fi-only, and metered-prefetch policy.
- Pure Shuffle code is unchanged.
- Debug unit tests, Android-test compilation, and APK assembly pass.
