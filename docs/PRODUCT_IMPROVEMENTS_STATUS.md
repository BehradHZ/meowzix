# Meowzix — Product Improvements Status (Items 1–20)

> Evidence-based ledger. Repository: `BehradHZ/meowzix`. The authoritative source is `docs/MEOWZIX_SYSTEM_SPEC.md`.
>
> Last reconciled: 2026-10-09, Increment H working branch. **Not a production-release certification.**

## Baseline and verification distinction

- Starting Increment H `main`: `2e6fd83cc50ef80a2d1035e1c2bdfd7911da6fc7` (Merge Increment G: advanced playback).
- Exact starting SHA CI: [Android run 37822880813](https://github.com/BehradHZ/meowzix/actions/runs/37822880813) **passed**; [Release run 37824432119](https://github.com/BehradHZ/meowzix/actions/runs/37824432119) **passed**.
- Work branch: `increment-h`. The H branch is not promoted to verified until its final Android workflow (including emulator instrumentation) is green.
- H compilation error (widget Uri parsing) from [Android 37833683881](https://github.com/BehradHZ/meowzix/actions/runs/37833683881) was fixed by `209c9067`. Hilt duplicate-binding compilation failure was separately fixed in `47daada8`.
- [Android 37847557034](https://github.com/BehradHZ/meowzix/actions/runs/37847557034) passed build/unit/lint and 85/87 instrumentation tests; the two remaining MediaBrowser tests had an incorrect test-thread caller, fixed in `057f13fe`.
- [Android 37849133416](https://github.com/BehradHZ/meowzix/actions/runs/37849133416) passed build/unit/lint and 86/87 instrumentation tests; `ScaleTest` was rejected by JUnit for an inferred Int return type after adding timing logs. Fixed with explicit Unit test signature in `0898cee7`.
- [Android 37904820763](https://github.com/BehradHZ/meowzix/actions/runs/37904820763) **passed all 88/88 connected Android instrumentation tests (no skips)**, but `lintDebug` failed because the new Android Auto discovery metadata made voice-search registration mandatory. `MEDIA_PLAY_FROM_SEARCH` was registered in `3091df43`. Search/play voice commands are now routed to the existing bounded local search/track resolver, with another emulator test added. A complete, fully green H workflow on the final SHA is the merge gate.
- `AGENTS.md` is absent on `main`. The original improvement-status file was an outdated historical ledger, not evidence that implemented A–G items were still missing.
- Status vocabulary: `verified`, `implemented awaiting device verification`, `partially verified`, `blocked`. “Implemented awaiting device verification” means that code and regression test coverage exist; it does **not** mean hardware UX/acoustics have been manually certified.
- CI is the automated-verification source. Device and car-host checks have a separate three-valued status matrix (`passed`, `failed`, `not run`).

## Increment overview

| Increment | Work | Current status |
|---|---|---|
| A | 1 and baseline performance | implemented awaiting device verification |
| B | 2–3: storage + transfers | implemented awaiting device verification |
| C | 4–7: recommendation controls | implemented awaiting device verification |
| D | 8: real EQ | implemented awaiting device verification |
| E | 9–10: lyrics | implemented awaiting device verification |
| F | 11–15: daily library features | implemented awaiting device verification |
| G | 16–17: advanced playback | implemented awaiting device verification |
| H | 18–20: scale + accessibility + ecosystem | code implemented; 88/88 H instrumentation passed on an intermediate SHA; final combined CI and manual gates pending |

## All twenty work items

| # | Item | Increment | Status | Implementation evidence | Automated validation evidence | Device/manual | Specific limitation |
|---:|---|---|---|---|---|---|---|
| 1 | Playback resilience and recovery | A | **implemented awaiting device verification** | PlaybackService, TdLibStreamingDataSource, persisted PlaybackStateStore; audio-focus and retry paths | PlaybackServiceTest, PlaybackSessionCodecTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Network-loss / process tests require device execution |
| 2 | Managed storage / cache safety | B | **implemented awaiting device verification** | ManagedStorageManager and ManagedFileLeaseRegistry with low-space behavior | CacheEvictionPolicyTest, ManagedFileLeaseRegistryTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Storage pressure and real file locks unverified on device |
| 3 | Bounded transfer scheduler | B | **implemented awaiting device verification** | PriorityTransferScheduler, ResilientDownloadRepository; dedupe/priority/recovery | TransferSchedulerTest, Room download instrumentation — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Telegram transfer interruption requires live account/device |
| 4 | Adaptive Smart Queue | C | **implemented awaiting device verification** | ProgressiveQueue provenance/revision and SmartQueueLiveAdapter | ProgressiveQueueSmartAdaptationTest, ProgressiveQueueTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Real-time UI adaptation unverified on device |
| 5 | Feedback, undo and expiry | C | **implemented awaiting device verification** | DataStoreRecommendationFeedbackRepository and RecommendationActionsViewModel | RecommendationPolicyTest, RecommendationUiTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Accessibility interaction not yet manually checked |
| 6 | Differentiated recommendation Mixes | C | **implemented awaiting device verification** | RecommendationSections, HeuristicRecommendationEngine, MediaBrowseMixProvider | RecommendationPolicyTest, SmartSelectorTest, MeowzixMediaLibraryTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | User-facing quality evaluation remains subjective |
| 7 | Local feature extraction and evaluation | C | **implemented awaiting device verification** | AndroidPcmAudioFeatureExtractor, PersonalizationTrainer, ModelState lifecycle | PcmAudioAnalysisTest, ModelArtifactCodecTest, RecommendationEvaluationTest, AudioFeatureCacheTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Codec/device extraction coverage incomplete |
| 8 | Real session EQ | D | **implemented awaiting device verification** | AndroidEqualizerRepository bound to playback audio-session lifecycle | EqualizerPresetMapperTest, PlaybackServiceTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Audible effects and routing support require device |
| 9 | Lyrics panel and morph | E | **implemented awaiting device verification** | MorphingPlayerHero, LyricsPanels, LyricsFollowMachine | LyricsFollowMachineTest, RecommendationUiTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Large-text/RTL/TalkBack visual tests outstanding |
| 10 | Lyrics parser/persistence/synchronization | E | **implemented awaiting device verification** | RoomLyricsRepository, LyricsDao, playback position observer | LrcParserTest, Lyrics migration/instrumentation — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Real imported lyric variations not exhaustively tested |
| 11 | Unified typed settings | F | **implemented awaiting device verification** | DataStoreSettingsRepository, UnifiedSettingsSheet; persisted reduceMotion | AdvancedPlaybackSettingsTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Device font-scale and settings UI tests outstanding |
| 12 | Persian-aware search | F | **implemented awaiting device verification** | TextNormalizer, FuzzyTrackSearch, effective FTS/search indexer | TextNormalizerTest, EffectiveTrackSearchIntegrationTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Mixed Persian/English UX must be checked visually |
| 13 | Local backup / restore | F | **implemented awaiting device verification** | BackupFormat and RoomLocalBackupRepository | BackupFormatTest, RoomLocalBackupRepositoryTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | External SAF provider / large export device test outstanding |
| 14 | Metadata / duplicate tools | F | **implemented awaiting device verification** | RoomLibraryToolsRepository, TrackMergeDao, merge journal, playback remap | TrackMergeIntegrationTest, PlaybackMergeRemapTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Real multi-source merge recovery needs device coverage |
| 15 | Rule-based playlists | F | **implemented awaiting device verification** | RulePlaylistCodec, RulePlaylistQueryDao, SmartPlaylistsUi | RulePlaylistCodecTest, RulePlaylistIntegrationTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Edge cases of collection changes need device |
| 16 | Sleep Timer | G | **implemented awaiting device verification** | SleepTimerManager and PlaybackService timer-aware listening signals | SleepTimerPolicyTest, SleepTimerListeningSignalTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Timing/acoustic effects on device not run |
| 17 | Loudness / gapless / real crossfade | G | **implemented awaiting device verification** | RoomLoudnessNormalizationRepository, PlaybackGainCoordinator, dual-player handoff | LoudnessAndGaplessPolicyTest, PlaybackGainCoordinatorTest, PlaybackServiceTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Audible validation with matching encoded audio fixtures outstanding |
| 18 | Scale, performance, maintainability | H | **implemented; final CI pending** | Bounded browse/playlist/history/download reads; legacy V2/V3 removed; BaselineProfile infrastructure retained | ScaleTest 10k/1k/100/100k fixture, query plans and timing assertions — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Android frame/scrolling and before-after profiler measurements not yet run |
| 19 | Accessibility and reduced motion | H | **implemented; final CI pending** | Persisted reduceMotion integrated into player, lyric, navigation and waveform; download/settings/EQ semantics | Existing Compose/instrumentation semantics + widget state tests — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | TalkBack, landscape, high-font scale and bilingual RTL manual audit not run |
| 20 | Widget and Android Auto | H | **implemented; final CI pending** | Single PlaybackService upgraded to MediaLibrarySession; MeowzixMediaLibrary bounded browse and real widget metadata/buttons | MeowzixMediaLibraryTest, PlaybackWidgetStateTest, PlaybackServiceTest — main baseline CI 37822880813 passed; H instrumentation 37904820763 passed 88/88; final whole-workflow SHA pending | not run | Android Auto DHU, actual widget lifecycle and vehicle UI not run |

**Important:** 88/88 H emulator tests passed on commit `74b551f5`, but its Android workflow was **not green** because lint failed on new car-media metadata. The additional voice-search implementation and updated intent filter require a new whole-workflow pass on the final SHA. The manual matrix and merge status remain separate from automated results.

## Increment H architecture and decisions

### Performance and code cleanup

The original V2/V3 Library screens were removed only after reference tracing; active navigation keeps V4. Track-row keys and paged library query flow remain in place. Downloads now observe a bounded, joined display projection rather than the entire track collection; History uses a bounded `observeRecentDisplayEvents` query; playlist append avoids reading all entries. The existing Room paging and baseline-profile modules are preserved.

The synthetic Android Room scale test seeds **10,000 tracks, 1,000 artists, 100 playlists and 100,000 listening events**; it exercises indexed search, recent history, bounded browse windows, playlist browsing and bounded candidate selection/scoring. Its performance budgets are CI-emulator guardrails, *not* evidence of smooth visual scrolling or audio startup.

### Before/after scale measurements

Identical Pixel 6 emulator profile, Android API 35, x86_64, GitHub Actions debug instrumentation and 10k/1k/100/100k seeded Room fixture. Both runs warm the same search/history/recommendation operations once before recording elapsed-realtime millisecond timings, then assert their result counts. **Each number is one timing sample**; submillisecond measurements truncate to `0 ms`. Thus differences are indicative only, not statistically validated improvements. Comparability for the four common operations is stronger than for new H-only projections.

| Operation | Pre-H `main` baseline | Increment H | Comparable? |
|---|---:|---:|---|
| Indexed track search | 1 ms | 0 ms | same fixture / operation |
| 100 recent history events | 1 ms | 0 ms | same fixture / operation |
| Reduce 10k IDs to 800 candidates | 11 ms | 7 ms | same fixture / seeded shuffle |
| Rank/score 800 candidates | 18 ms | 12 ms | same fixture / seeded scores |
| Bounded history-display UI projection (500) | not measured | 1 ms | H-only |
| Late bounded track browse page (100) | not measured | 6 ms | H-only |
| 100-playlist browse page | not measured | 0 ms | H-only |
| Native memory profiling | not run | not run | unavailable |
| Cold startup profiler | not run | not run | unavailable |
| UI scroll-frame/jank tracing | not run | not run | unavailable |

**Evidence:** Pre-H [Android CI 37833562131](https://github.com/BehradHZ/meowzix/actions/runs/37833562131) on temporary benchmark PR #36 (closed without merge); H [Android CI 37904820763](https://github.com/BehradHZ/meowzix/actions/runs/37904820763), instrumentation artifact's `MeowzixScaleTest` log entries. The latter job passed but its companion lint job failed (the later car discovery intent filter addresses that). Query timing is not a frame-time measurement and does not certify smooth UI scrolling.

### Accessibility

The existing `AppearanceSettings.reduceMotion` preference is persisted in DataStore and consumed by Now Playing/lyric transitions and navigation, rather than introducing a second motion control. Slider, EQ, widget, download and settings semantics have targeted code coverage. Animated lyrics should not announce each playback-position tick; manual reading must remain user-controlled. Real TalkBack traversal, large fonts, orientation changes and RTL visual validation have **not** been executed merely because the code compiles.

### Widget and Android Auto

`PlaybackWidgetProvider` displays the actual authoritative Media3 metadata, artwork if safely decodable, and previous/play-pause/next controls; it uses `MediaButtonReceiver` to reach the existing service. Updates come from meaningful session state changes; no per-second widget poll or second audio player was added. Artwork decoding is bounded and failure falls back to a deterministic Meowzix image.

The existing `PlaybackService` is now a `MediaLibraryService` exposing exactly one `MediaLibrarySession`. `MeowzixMediaLibrary` provides stable canonical Track IDs, tracks/artists/albums/playlists/favorites, and genuine eligible Mixes where available. `RoomPlaybackCatalog` provides bounded browse projections instead of duplicating the library repository. Explicit source selection/authentication, chat browsing, backup and metadata editing are **not** exposed to the car client. Requests to play catalog items use canonical IDs and the same source resolver/queue player used on the phone.

Car discovery is registered via `com.google.android.gms.car.application` and `automotive_app_desc.xml`; `PlaybackService` also declares `android.media.action.MEDIA_PLAY_FROM_SEARCH`. The phone-only Telegram-forward overflow command is excluded from Android Auto/Automotive controllers (including on button-state refresh). Browsers can search the existing index with a limit of 200 and retrieve bounded canonical track metadata; legacy Media3 voice search requests resolve up to 20 matched candidates via the same playback-source resolver. `AndroidAutoManifestTest` passed in the 88-test run; the newer `MediaBrowser` search callback test and voice-query path require their own final CI verification. Real car-voice focus extras and DHU playback are **not run**.

Offline or expired Telegram playback can still require phone-side recovery, and neither session tests nor widget tests constitute proof of a rendered Android Auto screen.

## Non-negotiable architecture invariants

- **One authoritative playback service/session/player**: `PlaybackService`; crossfade may temporarily use a second ExoPlayer instance for *overlap only*, coordinated by the same service and session, not a duplicate independent playback engine.
- **Canonical `Track.id`** remains the identity for library, history, lyrics, feedback, merge and media-browse tracks; physical Telegram and MediaStore references remain source details.
- **Pure Shuffle** remains permutation-based and isolated from recommendation feedback, model ranking and history. Smart queues retain manual entries and use provenance/revisions.
- **Local-first personalization**: feature extraction, training, evaluation and model fallback happen on-device; no new recommendation cloud service, LLM call or listening-history telemetry.
- **Storage**: managed cache has its own accounting, protection leases, low-space limits and non-eviction of pinned/user-owned MediaStore files. The transfer scheduler handles dedupe/priority/recovery.
- **User controls**: feedback undo/expiry, distinct Mix policies, lyrics sync/manual-follow, unified settings, backup format/security, metadata merge journal and Sleep Timer remain independent, preserved A–G behaviors.
- **Gain lifecycle**: the session EQ, normalization, timer fades and bounded crossfade use coordinated gain accounting. This does not certify distortion-free audio on untested hardware.
- **Privacy/permissions**: no new location, contacts, SMS, call-log, microphone or arbitrary provider browsing permission; no private Telegram app cache access or destructive Room migration.

## Validation commands and CI

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

The Android workflow in `.github/workflows/android.yml` executes equivalent unit, lint and debug build tasks plus startup/migration/device instrumentation on a GitHub-hosted Android emulator. Room export schema is preserved at `app/schemas`; migration tests cover versions through 16. The system spec's scale test and MediaLibrary/widget instrumentation are included in the default connected suite.

**Current verification evidence at this revision:** A–G baseline Android **passed** on `2e6fd83`; H instrumentation **passed 88/88** on `74b551f5` ([run 37904820763](https://github.com/BehradHZ/meowzix/actions/runs/37904820763)), but accompanying lint **failed**. After fixing the lint issue and adding indexed car voice/search callbacks, the **final exact-HEAD combined Android workflow must be green** prior to merge. PR [#35](https://github.com/BehradHZ/meowzix/pull/35) is the canonical source for the latest checks and merge state. Room migrations are covered by the connected suite. Code implementation, automated validation, manual validation, merge integration, and release certification remain five distinct statuses.

## Divergent-branch reconciliation

Compared the named divergent branches with both `main` and `increment-h`, then checked representative current files and feature paths. Divergent commit SHAs did not by themselves establish missing functionality. No older branch was bulk-merged.

| Branch | Unique historical functionality | Already in current code? | Action / reason |
|---|---|---|---|
| `f-h-product-improvements` | normalization, backup, rule/metadata tooling | current F-era alternatives exist | leave out: older model/DAO revisions |
| `release/fh-validation` | migration/search, smart playlists and metadata | newer main variants/tests exist | leave out: incompatible older migration/repository revisions |
| `release/0.4` | earlier release packaging, contextual search, Telegram changes | main includes newer implementations | leave out: old navigation/version |
| `release/playlist-glass-20261003` | frosted glass playlists | `PlaylistGlass.kt`, V4 and tests present | preserve current glass UI; no wholesale merge |
| `feat/reliable-telegram-send` | WorkManager send queue and dedupe | send worker and forward infrastructure present | no duplicate send pipeline |
| `fix/context-search-telegram-retry` | contextual search and stream retry | later Library/Queue/navigation/stream implementations | no regression demonstrated that warrants cherry-pick |
| `fix/telegram-progressive-autoplay` | earlier TDLib stream continuation | main contains newer streaming code | no stale playback reimplementation |
| `notification-forward` | Telegram notification forwarding overlay | `TelegramForwardActivity` exists | preserve phone control; exclude car-specific exposure |
| `compat/android-8-plus` | OS-dependent read permission | `AudioPermission` handles pre-Tiramisu reads | no duplicate compatibility patch |
| `increment-h-baseline-benchmark` | one-off performance timing logger | baseline captured in CI | benchmark PR #36 closed without merge |
| `perf/deep-overhaul-20260929` and `fix/library-playlist-performance` | performance changes | 0 commits ahead of `main` in comparison | no action; already incorporated/superseded |

This reconciliation is not an exhaustive cross-device behavioral equivalence test.

## Device, emulator and DHU matrix

Only use `passed`, `failed` or `not run`. These are manual checks; GitHub JVM/Android instrumentation successes do not count as real device or car-host manual verification.

| Check | Status | Reason |
|---|---|---|
| Airplane mode: local | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Airplane mode: pinned Telegram | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Airplane mode: remote-only Telegram | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Network loss: progressive playback | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Network loss: active download | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Seek during remote playback | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Process recreation: queue | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Process recreation: partial download | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Process recreation: lyrics | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Process recreation: EQ | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Process recreation: Sleep Timer | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Revoked audio permission | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Deleted or inaccessible audio source | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Low-storage behavior | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Audio-focus interruption | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Headset disconnect / becoming noisy | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Audible EQ | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Gapless transition | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Audible crossfade | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Large font scaling | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Landscape / compact layout | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| RTL | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Persian + English mixed lyrics | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| TalkBack and semantics | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Reduced-motion visual behavior | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Widget lifecycle / artwork/actions | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |
| Android Auto DHU / car host | not run | Requires manual/emulator/device verification; no claim is made from a JVM test |

## Remaining gates

1. Confirm final build, unit tests, lint, Room schema/migrations and Android instrumentation on **the same final Increment H SHA**, then update this ledger with links/results.
2. Record actual before/after scale numbers and identified emulator/device build; do not conflate Room SQL timings with scrolling frames.
3. Execute the manual 27-scenario device matrix, including Android Auto DHU/car host, TalkBack, RTL and audible DSP. Leave unavailable scenarios `not run` rather than inventing a pass.
4. Merge into `main` only after the completion gate and regression results support it.
