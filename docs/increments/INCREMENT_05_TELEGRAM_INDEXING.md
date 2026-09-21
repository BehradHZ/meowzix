# Increment 5 — Telegram Music Source Selection and Indexing

**Status:** implementation complete; live Telegram/account validation remains external.

## Objective

Import music metadata from explicitly selected Telegram chats into Meowzix's canonical library without making playback/download behavior a requirement of this increment or adding cross-source deduplication.

## Implementation checklist

### A. Source selection

- [x] Load selectable Telegram chats only after authorization is ready
- [x] Include Saved Messages explicitly
- [x] Support private chats, basic groups, supergroups/channels, and secret chats exposed by TDLib
- [x] Persist selected sources per Telegram account
- [x] Keep Telegram source selection behind the domain `TelegramRepository`
- [x] Provide source picker, refresh, sync, and deselection UI
- [x] Wire the source picker into the current post-login Telegram `Ready` UI

### B. Telegram persistence

- [x] Add `TelegramTrackSourceEntity`
- [x] Preserve account ID, chat ID, message ID, TDLib file ID, persistent remote file ID, filename, title, performer, and revision hook
- [x] Enforce unique `(accountId, chatId, messageId)` source identity
- [x] Add persisted per-chat sync checkpoint and initial-scan state
- [x] Add Room migration 2→3
- [x] Keep Telegram source rows linked to canonical `TrackSource`

### C. Eligibility and metadata

- [x] Import Telegram music/audio messages
- [x] Import document messages with an `audio/*` MIME type
- [x] Fall back to supported audio filename extensions when MIME is missing/unreliable
- [x] Keep voice notes excluded by default
- [x] Prefer Telegram audio title/performer metadata and filename fallback
- [x] Preserve remote-only sources as `TELEGRAM_REMOTE` / `REMOTE_ONLY`

### D. Initial and incremental sync

- [x] Page chat history instead of loading an entire source at once
- [x] Perform a complete first scan for a newly selected source
- [x] Persist the newest scanned message ID after a completed scan
- [x] Stop later refreshes at the stored checkpoint rather than rebuilding the database
- [x] Consume `UpdateNewMessage` for selected sources
- [x] Consume `UpdateMessageContent` to refresh indexed metadata
- [x] Consume `UpdateDeleteMessages` and mark known deleted Telegram sources missing
- [x] Repeated sync upserts the existing Telegram source instead of creating a duplicate
- [x] Deselecting a source marks its imported Telegram sources missing without deleting Telegram data

### E. Unified library UX

- [x] Display indexed Telegram tracks in the same library as local tracks
- [x] Display explicit source-aware library behavior for Telegram-imported tracks
- [x] Keep local MediaStore scanning optional when Telegram tracks already exist
- [x] Keep Telegram source selection and indexing independent from playback as an Increment 5 requirement

> Note: on-demand Telegram download-on-play has since landed early on `main`. That work is beyond the original Increment 5 acceptance boundary and does not change the indexing requirements above.

### F. Automated verification

- [x] JVM tests cover audio MIME and filename-extension eligibility/rejection
- [x] Project unit tests and debug assembly pass in CI
- [x] Android lint is part of the CI gate for this increment
- [x] Room schema v3 is generated and archived by CI
- [ ] Run migration 2→3 instrumentation test on an Android device/emulator

## Live Telegram validation still required

The following acceptance checks require a user-owned Telegram account and valid private Telegram API credentials and therefore are not claimed as completed by repository CI:

- [ ] select Saved Messages and confirm supported audio appears in the library
- [ ] select a private chat/channel and confirm its supported audio appears
- [ ] confirm a voice note remains excluded
- [ ] add a new music message and confirm incremental import without a full rescan
- [ ] delete an indexed Telegram message and confirm its source becomes missing
- [ ] repeat sync and confirm no duplicate rows appear

These checks must be performed without committing API credentials, authentication codes, or account secrets.

## Phase boundary

Increment 5 itself does **not require**:

- local + Telegram cross-source deduplication (Increment 6)
- explicit Telegram offline pinning/download management (Increment 7)
- Smart Shuffle / recommendation behavior

On-demand download-on-play was implemented early after the original Increment 5 plan and is already present on `main`.
