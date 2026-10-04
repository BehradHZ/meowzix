# Product Improvements Status

This ledger tracks the 20-item product-improvement extension against the authoritative `docs/MEOWZIX_SYSTEM_SPEC.md`.

## Baseline

- Baseline branch: `main`
- Baseline commit: `3272c4b4657acae4c2e60aa749e0b2b9cdf3c4ad`
- Baseline Android CI: workflow run `37195760947` — passed on the exact baseline commit (unit tests, lint/debug build, schema staging, and instrumentation jobs).
- Recommendation integration: the local recommendation/model/audio-feature implementation is already present on `main`; no separate recommendation-branch merge is needed as a starting step.
- Existing playlist redesign: present and must be preserved.
- Test cadence for this extension: per explicit user instruction, cumulative verification is reviewed after five extension increments (A–E), then again after F–H. GitHub may still start its push-triggered workflow for intermediate commits; those runs are not treated as the requested verification gate.

## Historical leads re-checked

| Lead | Current finding |
|---|---|
| `TdLibStreamingDataSource` Int/Unit compiler failure | Already resolved in current source. `readFromGrowingFile` has an explicit `Int` return contract and the exact baseline CI is green. |
| Recommendation branch had local model/Mixes/explanations/vibe | Integrated on `main` (`RecommendationEngine`, heuristic/model stack, recommendation sections/actions). |
| Playback-cache accounting incomplete | To verify/implement in Increment B. |
| Bulk downloads lacked centralized concurrency | To verify/implement in Increment B. |
| Smart queues reranked only at creation/mode change | To verify/implement in Increment C. |
| Time Mix and For You Now too similar | To verify/implement in Increment C. |
| Non-library screens load full collection | Targeted performance audit in A/H. |
| `LibraryRouteV2/V3/V4` coexist | Confirmed; `LibraryRoute` currently delegates to V4. V2/V3 will only be removed if full reference tracing proves them dead. |
| Spectrum uses `Visualizer` + microphone permission | Confirmed. Resolve in Increment D without preserving a microphone permission solely for decorative visualization. |

## Increment map

| Increment | Scope | Status |
|---|---|---|
| A — Verified baseline | Item 1 + targeted Item 18 | **in progress** |
| B — Reliable storage | Items 2–3 | not started |
| C — Controllable intelligence | Items 4–7 | not started |
| D — Audio controls | Item 8 | not started |
| E — Lyrics | Items 9–10 | not started |
| F — Daily library tools | Items 11–15 | not started |
| G — Advanced playback | Items 16–17 | not started |
| H — Ecosystem and polish | Item 20 + complete Items 18–19 | not started |

## Twenty-item status

| # | Work item | Increment | Status | Acceptance / current evidence |
|---:|---|---|---|---|
| 1 | Playback stability/build/resilience | A | implemented awaiting verification | Baseline build is green; queue persistence, audio focus/noisy handling, bounded remote retries already exist. Progressive read cancellation and remaining failure-state audit are being hardened. |
| 2 | Unified storage accounting/cache safety | B | not started | Need managed-file accounting, protection leases, low-space policy, reconciliation and settings surface. |
| 3 | Persistent bounded download scheduling | B | not started | Need one coordinated queue, concurrency/priority/dedupe and recovery verification. |
| 4 | Live Smart Shuffle adaptation | C | not started | Must preserve manual/current items, revision-gate stale results and prove Pure Shuffle isolation. |
| 5 | Recommendation feedback + undo | C | not started | Need canonical persisted feedback, expiry, undo/rebuild and UI actions. |
| 6 | Distinct explainable Mix policies | C | not started | Existing mixes must be re-checked with synthetic fixtures and genuine policy differences. |
| 7 | Better audio features/evaluation | C | not started | Existing extractor/model lifecycle will be extended rather than replaced. |
| 8 | Real EQ separate from spectrum | D | not started | Current spectrum is `Visualizer`-based and requests RECORD_AUDIO; real session-bound EQ and truthful spectrum capability are required. |
| 9 | Lyrics presentation/morph | E | not started | No current lyrics feature files found in repository tree. |
| 10 | Lyrics acquisition/parser/persistence/sync | E | not started | Need local provider boundary, LRC/plain support and canonical persistence. |
| 11 | Unified settings | F | not started | Existing typed DataStore settings will be reused/extended. |
| 12 | Better Persian search | F | not started | Existing `TextNormalizer` is minimal; FTS/query stack exists and will be extended. |
| 13 | Local backup/restore | F | not started | Android OS backup rules are not the requested user-controlled state export/import. |
| 14 | Metadata overrides/duplicates | F | not started | Existing canonical/source matching is a foundation; user overrides/merge journal are missing. |
| 15 | Rule-based playlists | F | not started | Manual playlists exist; typed live rules are missing. |
| 16 | Sleep Timer | G | not started | Playback-service-owned timer is missing. |
| 17 | Loudness/gapless/crossfade | G | not started | Must be implemented as actual audio behavior with truthful capability fallback. |
| 18 | Scale/performance/maintainability | A/H | in progress | Paging/performance infrastructure exists; legacy-route and all-track-load audit remains. |
| 19 | Accessibility | H/cross-cutting | not started | Applied cross-cutting as features land; final audit in H. |
| 20 | Widget + Android Auto browsing | H | not started | Widget exists; current service is a `MediaSessionService`, not yet a full MediaLibrary browse implementation. |

## Validation ledger

Status values are `passed`, `failed`, or `not run`.

| Check | Status | Evidence / reason |
|---|---|---|
| Baseline Android CI on `3272c4b` | passed | GitHub Actions run `37195760947`. |
| Baseline release workflow | passed | Release workflow run `37196376453`. |
| A–E cumulative gate | not run | Scheduled after Increment E by user instruction. |
| F–H cumulative/final gate | not run | Scheduled after Increment H. |
| Real-device audible EQ | not run | Requires compatible Android audio path/device; record separately in D checklist. |
| Real-device gapless/crossfade | not run | Requires supported encoded fixtures/audio device; record separately in G checklist. |
| Android Auto DHU/car host | not run | Requires host/DHU environment; service-level controller tests will be separate. |
| TalkBack/large-font/RTL visual pass | not run | Device/emulator validation scheduled with H accessibility gate. |

## Device-validation checklist

The following must not be marked verified from host tests alone:

- airplane mode with local, pinned Telegram and remote-only tracks;
- network loss during progressive read/download/seek/transition;
- process recreation with queue, partial transfer, lyrics, EQ and timer;
- deleted/revoked/unavailable sources and low disk space;
- audio focus, becoming-noisy/headset disconnect and background controls;
- supported audible EQ, gapless and crossfade behavior;
- large fonts, Persian/English mixed RTL, TalkBack and rapid lyric morph reversal;
- widget lifecycle and Android Auto host browsing.

## Decision notes

- Canonical `Track` identity remains authoritative across local/Telegram sources and all personalization work.
- Pure Shuffle remains isolated from recommendation/feedback/model state; every queue-generation change must retain regression coverage for this invariant.
- No online lyrics provider, remote recommendation service, LLM behavior API, telemetry backend, microphone/location/contacts/SMS/call-log requirement, or destructive Room migration is introduced by this extension.
- Device-only acceptance criteria remain `not run` until actually exercised; compile/test success is not treated as proof of audible or host-specific behavior.
