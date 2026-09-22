# Increment 8 — Telegram On-Demand Playback and Prefetch

## Status

Implemented on 2026-09-21. Performance/cache refinements added on 2026-09-22. Live network-loss and metered-network device scenarios require a configured Telegram test account.

## Implementation

- Remote Play keeps the existing high-priority download-on-play flow and automatic playback when no valid local source exists.
- Playback resolution is cache-first: `LOCAL_MEDIASTORE`, `APP_OFFLINE_COPY`, or a complete `TDLIB_LOCAL` source MUST be used before network policy checks, Telegram account checks, `GetMessage`, or `DownloadFile`.
- A stale app-managed local-source row is invalidated and only then may resolution fall back to Telegram.
- Explicit offline download is also cache-first. Existing owned offline files are reused; a complete TDLib playback-cache file is promoted into the offline store without re-downloading it from Telegram.
- A track already available through a valid local source must not perform a Telegram availability probe merely because its canonical Track also has a Telegram source.
- Player exposes Downloading and actionable failure states only when remote work is actually necessary.
- Next queue item is prefetched at low TDLib priority only when no valid local source already satisfies it.
- Queue changes cancel the previous prefetch request.
- DataStore persists offline mode, Wi-Fi-only downloads, prefetch, and metered-prefetch settings.
- Network policy blocks automatic downloads in offline mode, without connectivity, or against metered policy.
- Offline settings are user-visible on the Offline screen.

## Telegram refresh discipline

- UI recomposition, navigation, and tab changes MUST NOT trigger chat discovery or a full Telegram source refresh.
- Automatic chat discovery occurs on the first real transition into an authenticated TDLib `AuthorizationStateReady` session.
- Repeated `AuthorizationStateReady` updates during the same authenticated session are de-duplicated before repository initialization, preventing repeated `GetMe`, `GetChats`, and `GetChat` work.
- Leaving the Ready authorization state resets that guard so a future authenticated session initializes normally.
- User-requested Refresh remains allowed and explicitly performs discovery.
- Incremental message updates and selected-source sync remain event/checkpoint driven; they must not be replaced with repeated full `GetChats`/`GetChat` scans.

## Verification

- Unit tests cover offline mode, no-network, Wi-Fi-only, and metered-prefetch policy.
- Unit tests cover duplicate Ready suppression and reset on a real authorization transition.
- Playback/download paths short-circuit before Telegram when a valid local cache/source exists.
- Pure Shuffle remains source-agnostic; queue materialization does not imply downloading the whole materialized window.
- Debug unit tests, lint, APK assembly, and connected instrumentation are required before merge.
