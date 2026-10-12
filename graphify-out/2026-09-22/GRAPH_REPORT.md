# Graph Report - meowzix  (2026-09-21)

## Corpus Check
- 127 files · ~58,649 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 12 file(s) not represented in the graph (top: (none) 4, .properties 3, .xml 2)

## Summary
- 1730 nodes · 3198 edges · 159 communities (115 shown, 44 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 66 edges (avg confidence: 0.88)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `540f592a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- DataModule.kt
- HistoryDao
- TelegramRepository
- NowPlayingRoute.kt
- Meowzix UI/UX — Glass Music Redesign Backlog
- TelegramMusicSourcePicker.kt
- AndroidAudioVisualizer.kt
- PlaybackMode
- TelegramAuthRoute.kt
- TdLibClientAdapter.kt
- MediaController
- TrackArtwork.kt
- DataStoreSettingsRepository.kt
- PlaybackController
- MEOWZIX_SYSTEM_SPEC.md
- TdLibTelegramRepository
- PlaybackService.kt
- GlassSurface.kt
- PlaylistDao.kt
- TelegramAuthStep
- TdLibTelegramRepository.kt
- .plan
- LibraryViewModel.kt
- HistoryViewModel.kt
- LibraryDao
- TelegramAuthViewModel
- test
- MainActivity.kt
- Implementation checklist
- 18A. Source-Agnostic Model Training Pipeline
- QueueRoute.kt
- Implementation checklist
- SourceAvailability
- 7. Domain Model
- LocalMusicLibraryRepository.kt
- PlaybackServiceTest.kt
- dev
- DownloadsViewModel
- LibrarySection
- 4. Source-Agnostic Training and Data Provenance
- Implementation checklist
- LibraryRoute.kt
- Implementation checklist
- QueueRepository
- 18. Smart Shuffle / Adaptive Playback v1
- 32. AI Agent Execution Rules
- 10. Telegram Integration
- fontweight
- MeowzixApplication.kt
- DownloadRecordEntity
- gradlew
- Migrations.kt
- MeowzixApp.kt
- TdLibDownloadRepository
- ListeningHistoryRepository.kt
- RecommendationEngine.kt
- DataStoreSettingsRepository
- NowPlayingViewModel
- 25. Future Increments — Explicitly Out of Initial Scope
- NetworkPlaybackSettings
- 3. Core Product Decisions
- entity
- .normalize
- DatabaseModule
- PlaylistRepository
- 17. Listening Event Semantics
- tracksourcetype
- 31. Testing Strategy
- ResolvingPlaybackController
- MeowzixDatabase
- 16. Pure Shuffle Specification
- 21. UX Requirements
- 29. Settings
- 34. Suggested Initial User Flow
- locallibraryrefreshresult
- Increment 0 — Project Foundation and Architectural Skeleton
- Increment 1 — Local Music Library
- Increment 8 — Telegram On-Demand Playback and Prefetch
- Increment 12 — Source-Agnostic Learned Personalization
- 11. Unified Library and Deduplication
- 12. Playback Engine
- 14. Offline Storage Model
- 19. Smart Shuffle Safety and Cold Start
- 22. Security
- Increment 2 — Core Playback and Background Media Service
- Increment 4 — Telegram Client Foundation and Authentication
- Increment 14 — Release Readiness
- RTK - Rust Token Killer (Codex CLI)
- ListeningEventType
- 13. Remote Playback Strategy
- 9. Local Music Discovery
- Increment 3 — Queue, Repeat, and True Pure Shuffle
- Increment 5 — Telegram Music Source Selection and Indexing
- Increment 6 — Unified Track Model and Deduplication
- Increment 7 — Offline Download Manager
- Increment 9 — Library UX, Search, Favorites, and Playlists
- Increment 10 — Listening History and Local Behavioral Data
- Increment 11 — Smart Shuffle v1: Deterministic Adaptive Heuristics
- Increment 13 — Resilience, Privacy, and Storage Hardening
- AGENTS.md
- Implementation checklist
- .match
- LocalMusicLibraryRepositoryTest
- SettingsRepository
- PlaybackServiceTest
- TelegramAudioMessageMapper.kt
- MigrationTest
- MusicLibraryRepository
- RepeatMode
- ScannedLocalTrack
- LocalMusicLibraryRepository
- PlaybackStatus
- RoomListeningHistoryRepository
- AudioSpectrum.kt
- RoomPlaylistRepository
- bindMusicLibraryRepository
- TelegramChatKind
- RoomPlaylistRepositoryTest
- Increment 6 — Unified Track Model and Deduplication
- Increment 7 — Offline Download Manager
- Increment 8 — Telegram On-Demand Playback and Prefetch
- Increment 9 — Library UX, Search, Favorites, and Playlists
- nowplayingtrack
- .outcome
- PlaybackInitiator
- TimeBucket
- channel
- receiveasflow
- bindRemoteTrackPlaybackResolver
- RoomListeningHistoryRepositoryTest
- telegramchatkind
- telegramsyncresult
- bindTelegramRepository
- Increment 10 — Listening History and Local Behavioral Data
- .observePlaylists
- PlaybackMode
- PlaybackController
- INCREMENT_11_SMART_SHUFFLE.md
- dev
- Flow
- DownloadDao
- LibraryDao
- PlaylistDao
- TelegramDao
- Dp
- HazeState
- ViewModel
- Job
- PlaybackController
- PlaybackState
- QueueState
- StateFlow

## God Nodes (most connected - your core abstractions)
1. `TdLibTelegramRepository` - 36 edges
2. `PlaybackMode` - 29 edges
3. `ResolvingPlaybackController` - 28 edges
4. `AndroidPlaybackController` - 27 edges
5. `LibraryDao` - 24 edges
6. `TelegramRepository` - 23 edges
7. `Track` - 23 edges
8. `HistoryDao` - 21 edges
9. `RepeatMode` - 21 edges
10. `PlaybackController` - 19 edges

## Surprising Connections (you probably didn't know these)
- `B. Audio behavior and recovery` --references--> `PlaybackController`  [INFERRED]
  docs/increments/INCREMENT_02_CORE_PLAYBACK.md → app/src/main/java/dev/behradhz/meowzix/domain/playback/PlaybackController.kt
- `C. Architecture skeleton` --references--> `MusicLibraryRepository`  [INFERRED]
  docs/increments/INCREMENT_00_PROJECT_FOUNDATION.md → app/src/main/java/dev/behradhz/meowzix/domain/library/MusicLibraryRepository.kt
- `A. TDLib integration` --references--> `TelegramRepository`  [INFERRED]
  docs/increments/INCREMENT_04_TELEGRAM_AUTH.md → app/src/main/java/dev/behradhz/meowzix/domain/telegram/TelegramRepository.kt
- `4.1 Default training policy` --references--> `Track`  [INFERRED]
  docs/MEOWZIX_SYSTEM_SPEC.md → app/src/main/java/dev/behradhz/meowzix/core/model/Track.kt
- `6. Repository / Module Structure` --references--> `Track`  [INFERRED]
  docs/MEOWZIX_SYSTEM_SPEC.md → app/src/main/java/dev/behradhz/meowzix/core/model/Track.kt

## Import Cycles
- None detected.

## Communities (159 total, 44 thin omitted)

### Community 0 - "DataModule.kt"
Cohesion: 0.09
Nodes (29): AndroidAudioVisualizer, bindAudioVisualizerRepository(), bindDownloadRepository(), bindListeningHistoryRepository(), bindLocalMediaScanner(), bindPlaylistRepository(), bindQueueRepository(), bindSettingsRepository() (+21 more)

### Community 1 - "HistoryDao"
Cohesion: 0.13
Nodes (6): HistoryDao, Flow, ListeningEventEntity, ListeningSessionEntity, TrackPreferenceStatsEntity, TrackTimePreferenceEntity

### Community 2 - "TelegramRepository"
Cohesion: 0.13
Nodes (4): StateFlow, TelegramAuthState, TelegramRepository, TelegramSyncResult

### Community 3 - "NowPlayingRoute.kt"
Cohesion: 0.06
Nodes (47): activityresultcontracts, animate, PlaybackState, ArtworkGestureAxis, HORIZONTAL, UNDECIDED, VERTICAL, ArtworkPage() (+39 more)

### Community 4 - "Meowzix UI/UX — Glass Music Redesign Backlog"
Cohesion: 0.08
Nodes (23): 10. Real audio-reactive spectrum / “equalizer” visualization, 11. Actual DSP equalizer controls — later audio feature, 12. Queue, 13. Telegram, 14. Future music features, 15. Accessibility and performance, 16. Immediate implementation sequence on `ui`, 1. Direction (+15 more)

### Community 5 - "TelegramMusicSourcePicker.kt"
Cohesion: 0.11
Nodes (29): alignment, DownloadsRoute(), SettingToggle(), arrangement, button, checkbox, circularprogressindicator, collectasstatewithlifecycle (+21 more)

### Community 6 - "AndroidAudioVisualizer.kt"
Cohesion: 0.07
Nodes (23): AudioPermission, AudioPermissionStatus, DENIED, GRANTED, REQUIRED, AudioSpectrumState, AudioVisualizerRepository, StateFlow (+15 more)

### Community 7 - "PlaybackMode"
Cohesion: 0.06
Nodes (34): PlaybackMode, ORDERED, PURE_SHUFFLE, SMART_SHUFFLE, RepeatMode, ALL, OFF, ONE (+26 more)

### Community 8 - "TelegramAuthRoute.kt"
Cohesion: 0.09
Nodes (31): TelegramMusicSourceState, HazeState, MessageState(), OtherDeviceConfirmation(), ProgressState(), ReadyState(), RegistrationForm(), SingleValueForm() (+23 more)

### Community 9 - "TdLibClientAdapter.kt"
Cohesion: 0.17
Nodes (13): TdLibClientAdapterTest, Flow, TdApi, TdLibClientAdapter, TdLibException, assharedflow, Client, Exception (+5 more)

### Community 11 - "TrackArtwork.kt"
Cohesion: 0.10
Nodes (23): Dp, Modifier, rememberArtworkBitmap(), TrackArtwork(), TrackArtworkBackdrop(), asimagebitmap, background, bitmapfactory (+15 more)

### Community 12 - "DataStoreSettingsRepository.kt"
Cohesion: 0.36
Nodes (6): applicationcontext, booleanpreferenceskey, edit, map, preferencesdatastore, stringpreferenceskey

### Community 13 - "PlaybackController"
Cohesion: 0.18
Nodes (4): bindPlaybackController(), StateFlow, NowPlayingTrack, PlaybackController

### Community 14 - "MEOWZIX_SYSTEM_SPEC.md"
Cohesion: 0.08
Nodes (23): 15. Queue Semantics, 1. Purpose, 20. Search and Library Organization, 23. Performance Targets, 24. Increment Plan, 26. Recommendation Evaluation, 28. Failure Handling Matrix, 2. Product Vision (+15 more)

### Community 15 - "TdLibTelegramRepository"
Cohesion: 0.09
Nodes (8): TelegramDao, TelegramSelectedSourceEntity, TelegramTrackSourceEntity, StateFlow, TdApi, MutableSyncResult, TdLibTelegramRepository, toDomainStep()

### Community 16 - "PlaybackService.kt"
Cohesion: 0.08
Nodes (24): T, PureShuffleEngine, ShuffleCycle, AudioVisualizerRepository, PlaybackCatalog, PlaybackService, toPlayerRepeatMode(), PureShuffleEngineTest (+16 more)

### Community 17 - "GlassSurface.kt"
Cohesion: 0.17
Nodes (13): GlassSurface(), Color, HazeState, Modifier, box, boxscope, clip, hazeeffect (+5 more)

### Community 18 - "PlaylistDao.kt"
Cohesion: 0.22
Nodes (8): LocalPlaybackRow, Flow, PlaylistSummaryRow, dao, insert, onconflictstrategy, query, upsert

### Community 19 - "TelegramAuthStep"
Cohesion: 0.14
Nodes (14): Closed, Closing, ConfigurationRequired, Initializing, LoggingOut, TelegramAuthStep, Unsupported, WaitCode (+6 more)

### Community 20 - "TdLibTelegramRepository.kt"
Cohesion: 0.09
Nodes (36): NetworkPolicy, NetworkUse, PREFETCH, USER_REQUEST, toSummary(), TelegramChatSummary, asstateflow, await (+28 more)

### Community 21 - ".plan"
Cohesion: 0.32
Nodes (5): ExistingLocalSourceSnapshot, LocalLibraryReconciler, LocalLibraryReconciliationPlan, MatchedLocalTrack, LocalLibraryReconcilerTest

### Community 22 - "LibraryViewModel.kt"
Cohesion: 0.16
Nodes (8): Track, Job, StateFlow, ViewModel, LibraryUiState, LibraryViewModel, catch, update

### Community 23 - "HistoryViewModel.kt"
Cohesion: 0.24
Nodes (8): HistoryRoute(), HistoryRow, HistoryViewModel, combine, hiltviewmodel, sharingstarted, statein, viewmodelscope

### Community 24 - "LibraryDao"
Cohesion: 0.13
Nodes (5): Flow, LibraryDao, LocalMediaSourceEntity, TrackEntity, TrackSourceEntity

### Community 26 - "test"
Cohesion: 0.11
Nodes (13): TdLibAuthorizationStateMapperTest, assertequals, assertfalse, assertnotequals, assertnull, assertsame, assertthrows, asserttrue (+5 more)

### Community 27 - "MainActivity.kt"
Cohesion: 0.21
Nodes (10): androidentrypoint, Bundle, MainActivity, MeowzixTheme(), ComponentActivity, enableedgetoedge, fillmaxsize, modifier (+2 more)

### Community 28 - "Implementation checklist"
Cohesion: 0.10
Nodes (18): A. TDLib integration, B. Authorization state machine, C. Session and logout behavior, D. Login UI and setup, E. Verification, External validation still required, Implementation checklist, Increment 4 — Telegram Client Foundation and Authentication (+10 more)

### Community 29 - "18A. Source-Agnostic Model Training Pipeline"
Cohesion: 0.11
Nodes (18): 18A.10 Model output is not the final queue score, 18A.11 Exploration during learned ranking, 18A.12 Model evaluation, 18A.13 Reset and reproducibility, 18A.1 Training objective, 18A.2 Reward construction, 18A.3 Feature groups, 18A.4 Initial learned model family (+10 more)

### Community 30 - "QueueRoute.kt"
Cohesion: 0.11
Nodes (23): QueueItem, QueueState, EmptyQueue(), QueueHeader(), QueueRoute(), QueueScreen(), QueueStatusPill(), SwipeableQueueItem() (+15 more)

### Community 31 - "Implementation checklist"
Cohesion: 0.14
Nodes (13): A. Platform and permission boundary, Acceptance criteria, C. MediaStore ingestion, D. Idempotent reconciliation, E. Repository boundary, F. Library UI, G. Verification, Implementation checklist (+5 more)

### Community 32 - "SourceAvailability"
Cohesion: 0.13
Nodes (14): SourceAvailability, AVAILABLE_LOCAL, DOWNLOADING, ERROR, MISSING, PARTIAL, REMOTE_ONLY, TrackSourceType (+6 more)

### Community 33 - "7. Domain Model"
Cohesion: 0.14
Nodes (14): 7.10 PreferenceStats, 7.11 AudioFeatureVector, 7.12 TrainingSample, 7.13 ModelState, 7.1 Track, 7.2 TrackSource, 7.3 TelegramTrackSource, 7.4 LocalMediaSource (+6 more)

### Community 34 - "LocalMusicLibraryRepository.kt"
Cohesion: 0.18
Nodes (8): TextNormalizer, instant, mutex, normalizer, uuid, withlock, withtransaction, zoneid

### Community 35 - "PlaybackServiceTest.kt"
Cohesion: 0.11
Nodes (26): after, androidjunit4, MediaItem, applicationprovider, atomicreference, before, bytebuffer, byteorder (+18 more)

### Community 37 - "DownloadsViewModel"
Cohesion: 0.20
Nodes (3): DownloadRow, DownloadsViewModel, ViewModel

### Community 38 - "LibrarySection"
Cohesion: 0.33
Nodes (6): LibrarySection, ALBUMS, ARTISTS, FAVORITES, PLAYLISTS, TRACKS

### Community 39 - "4. Source-Agnostic Training and Data Provenance"
Cohesion: 0.17
Nodes (12): 4.1 Default training policy, 4.2 Data classes that may be used for training, 4.3 Source provenance, 4.4 Deduplicate before learning, 4.5 Local-first training, 4.6 Model lifecycle, 4.7 No provider-specific model contract, 4. Source-Agnostic Training and Data Provenance (+4 more)

### Community 40 - "Implementation checklist"
Cohesion: 0.18
Nodes (10): A. Reproducible Android project, B. Foundation libraries, C. Architecture skeleton, D. Configuration and safety, E. Acceptance verification, Implementation checklist, Increment 0 — Project Foundation and Architectural Skeleton, Non-goals (+2 more)

### Community 41 - "LibraryRoute.kt"
Cohesion: 0.06
Nodes (49): album, AlbumsSection(), ArtistsSection(), AvailabilityFilter, ALL, CLOUD, OFFLINE, formatDuration() (+41 more)

### Community 42 - "Implementation checklist"
Cohesion: 0.18
Nodes (10): A. Playback engine and Android service, Acceptance verification — 2026-09-17, B. Audio behavior and recovery, C. Queue construction and state persistence, D. Now Playing UI, E. Verification, Implementation checklist, Increment 2 — Core Playback and Background Media Service (+2 more)

### Community 43 - "QueueRepository"
Cohesion: 0.07
Nodes (14): StateFlow, QueueRepository, ViewModel, QueueViewModel, A. First-class queue, Acceptance verification — 2026-09-17, B. Repeat and Pure Shuffle, C. Persistence and recovery (+6 more)

### Community 44 - "18. Smart Shuffle / Adaptive Playback v1"
Cohesion: 0.18
Nodes (11): 18.10 Explainability, 18.1 Goal, 18.2 Candidate generation, 18.3 Affinity statistics, 18.4 Time affinity, 18.5 Recency penalty, 18.6 Artist repetition penalty, 18.7 Exploration bonus (+3 more)

### Community 45 - "32. AI Agent Execution Rules"
Cohesion: 0.20
Nodes (10): 32.1 Work one increment at a time, 32.2 Do not invent product behavior silently, 32.3 Keep hard boundaries, 32.4 Avoid premature abstraction, 32.5 Preserve local-first behavior, 32.6 Preserve Pure Shuffle semantics, 32.7 Database migrations are mandatory, 32.8 Security (+2 more)

### Community 46 - "10. Telegram Integration"
Cohesion: 0.22
Nodes (9): 10.1 Use TDLib, not Bot API, 10.2 Authentication, 10.3 Credentials, 10.4 Source selection, 10.5 Telegram message eligibility, 10.6 Telegram metadata, 10.7 Telegram file lifecycle, 10.8 Sync (+1 more)

### Community 47 - "fontweight"
Cohesion: 0.40
Nodes (4): fontweight, sp, textstyle, typography

### Community 48 - "MeowzixApplication.kt"
Cohesion: 0.67
Nodes (3): MeowzixApplication, Application, hiltandroidapp

### Community 49 - "DownloadRecordEntity"
Cohesion: 0.21
Nodes (5): DownloadDao, Flow, DownloadRecordEntity, CacheEvictionPolicy, CacheEvictionPolicyTest

### Community 50 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 52 - "MeowzixApp.kt"
Cohesion: 0.10
Nodes (24): TelegramAuthRoute(), FloatingDock(), GlassMiniPlayer(), PlaybackState, MeowzixApp(), TopLevelDestination, clickable, currentbackstackentryasstate (+16 more)

### Community 54 - "TdLibDownloadRepository"
Cohesion: 0.06
Nodes (16): Flow, TdLibDownloadRepository, Job, TdLibRemoteTrackPlaybackResolver, DownloadRepository, DownloadStatus, CANCELED, COMPLETED (+8 more)

### Community 55 - "ListeningHistoryRepository.kt"
Cohesion: 0.16
Nodes (8): Flow, Flow, PlaybackMode, ListeningEvent, ListeningHistoryRepository, TrackPreferenceStats, dayofweek, zoneddatetime

### Community 56 - "RecommendationEngine.kt"
Cohesion: 0.13
Nodes (14): bindRecommendationEngine(), HeuristicRecommendationEngine, TimeBucket, AdaptiveScorer, AdaptiveScoreWeights, CandidateGenerator, TimeBucket, PreferenceSnapshot (+6 more)

### Community 57 - "DataStoreSettingsRepository"
Cohesion: 0.22
Nodes (3): androidx, DataStoreSettingsRepository, Flow

### Community 60 - "NowPlayingViewModel"
Cohesion: 0.20
Nodes (3): NowPlayingViewModel, AudioVisualizerRepository, ViewModel

### Community 61 - "25. Future Increments — Explicitly Out of Initial Scope"
Cohesion: 0.25
Nodes (8): 25. Future Increments — Explicitly Out of Initial Scope, Future A — Progressive Telegram Streaming, Future B — Cross-device App State Sync, Future C — iOS, Future D — Desktop, Future E — Metadata Enrichment, Future F — Audio Analysis, Future G — Advanced Personalization Models

### Community 62 - "NetworkPlaybackSettings"
Cohesion: 0.35
Nodes (4): NetworkPolicyRules, NetworkPlaybackSettings, NetworkPolicyRulesTest, assertnotnull

### Community 63 - "3. Core Product Decisions"
Cohesion: 0.15
Nodes (13): TrackSource, B. Canonical persistence model, 27. Database Constraints and Invariants, 3.1 Local-first, not Telegram-first, 3.2 Android-first, 3.3 No custom backend in the initial architecture, 3.4 Telegram must be modeled as a source, not as the Track itself, 3.5 Pure Shuffle and Smart Shuffle are separate features (+5 more)

### Community 64 - "entity"
Cohesion: 0.38
Nodes (4): entity, foreignkey, index, primarykey

### Community 66 - "DatabaseModule"
Cohesion: 0.24
Nodes (6): DatabaseModule, DownloadDao, LibraryDao, MeowzixDatabase, PlaylistDao, TelegramDao

### Community 67 - "PlaylistRepository"
Cohesion: 0.21
Nodes (3): Flow, PlaylistRepository, PlaylistSummary

### Community 68 - "17. Listening Event Semantics"
Cohesion: 0.29
Nodes (7): 17.1 Completion, 17.2 Early skip, 17.3 Late skip, 17.4 Manual selection, 17.5 Replay, 17.6 Seek, 17. Listening Event Semantics

### Community 70 - "31. Testing Strategy"
Cohesion: 0.29
Nodes (7): 31.1 Unit tests, 31.2 Repository tests, 31.3 Playback tests, 31.4 Telegram integration tests, 31.5 Offline scenario tests, 31.6 Scale tests, 31. Testing Strategy

### Community 71 - "ResolvingPlaybackController"
Cohesion: 0.15
Nodes (5): QueueRepository, QueueState, StateFlow, ResolvingPlaybackController, PlaybackInitiator

### Community 72 - "MeowzixDatabase"
Cohesion: 0.17
Nodes (8): DownloadDao, LibraryDao, PlaylistDao, TelegramDao, MeowzixDatabase, database, RoomDatabase, typeconverters

### Community 73 - "16. Pure Shuffle Specification"
Cohesion: 0.33
Nodes (6): 16.1 Definition, 16.2 Algorithm, 16.3 No personalization, 16.4 Queue mutation, 16.5 Test properties, 16. Pure Shuffle Specification

### Community 74 - "21. UX Requirements"
Cohesion: 0.33
Nodes (6): 21.1 Primary player states, 21.2 Track availability badges, 21.3 Modes, 21.4 Now Playing, 21.5 Errors, 21. UX Requirements

### Community 75 - "29. Settings"
Cohesion: 0.33
Nodes (6): 29. Settings, Offline, Playback, Privacy, Shuffle, Telegram

### Community 76 - "34. Suggested Initial User Flow"
Cohesion: 0.33
Nodes (6): 34. Suggested Initial User Flow, First launch, Pinning offline, Playing a remote track, Smart Shuffle learning, Telegram setup

### Community 78 - "Increment 0 — Project Foundation and Architectural Skeleton"
Cohesion: 0.33
Nodes (6): Acceptance criteria, Deliverables, Goal, Increment 0 — Project Foundation and Architectural Skeleton, Non-goals, Required interfaces

### Community 79 - "Increment 1 — Local Music Library"
Cohesion: 0.33
Nodes (6): Acceptance criteria, Behavior, Deliverables, Goal, Increment 1 — Local Music Library, Non-goals

### Community 80 - "Increment 8 — Telegram On-Demand Playback and Prefetch"
Cohesion: 0.33
Nodes (6): Acceptance criteria, Deliverables, First implementation, Goal, Increment 8 — Telegram On-Demand Playback and Prefetch, Optional enhancement

### Community 81 - "Increment 12 — Source-Agnostic Learned Personalization"
Cohesion: 0.33
Nodes (6): Acceptance criteria, Deliverables, Goal, Increment 12 — Source-Agnostic Learned Personalization, Initial model, Required source behavior

### Community 82 - "11. Unified Library and Deduplication"
Cohesion: 0.40
Nodes (5): 11.1 Exact deduplication, 11.2 Provisional metadata matching, 11.3 Merge confidence, 11.4 Source priority for playback, 11. Unified Library and Deduplication

### Community 83 - "12. Playback Engine"
Cohesion: 0.40
Nodes (5): 12.1 Media3, 12.2 Audio focus, 12.3 Resume, 12.4 Supported file formats, 12. Playback Engine

### Community 84 - "14. Offline Storage Model"
Cohesion: 0.40
Nodes (5): 14.1 Pinned offline, 14.2 Temporary cache, 14.3 Local shared file, 14.4 Storage cleanup, 14. Offline Storage Model

### Community 85 - "19. Smart Shuffle Safety and Cold Start"
Cohesion: 0.40
Nodes (5): 19.1 Cold start, 19.2 Negative feedback, 19.3 Session adaptation, 19.4 Reset controls, 19. Smart Shuffle Safety and Cold Start

### Community 86 - "22. Security"
Cohesion: 0.40
Nodes (5): 22.1 Secrets, 22.2 Local database, 22.3 TDLib local data, 22.4 Debug logging, 22. Security

### Community 87 - "Increment 2 — Core Playback and Background Media Service"
Cohesion: 0.40
Nodes (5): Acceptance criteria, Deliverables, Goal, Increment 2 — Core Playback and Background Media Service, Non-goals

### Community 88 - "Increment 4 — Telegram Client Foundation and Authentication"
Cohesion: 0.40
Nodes (5): Acceptance criteria, Deliverables, Goal, Increment 4 — Telegram Client Foundation and Authentication, Non-goals

### Community 89 - "Increment 14 — Release Readiness"
Cohesion: 0.40
Nodes (5): Acceptance criteria, Deliverables, Goal, Increment 14 — Release Readiness, Telegram-specific release checks

### Community 90 - "RTK - Rust Token Killer (Codex CLI)"
Cohesion: 0.40
Nodes (4): Meta Commands, RTK - Rust Token Killer (Codex CLI), Rule, Verification

### Community 91 - "ListeningEventType"
Cohesion: 0.17
Nodes (12): ListeningEventType, AUTO_SELECTED, FAVORITED, MANUAL_SELECTED, PLAY_COMPLETED, PLAY_STARTED, PLAY_STOPPED, REPLAYED (+4 more)

### Community 92 - "13. Remote Playback Strategy"
Cohesion: 0.50
Nodes (4): 13.1 Initial strategy: download-on-play, 13.2 Progressive playback is an enhancement, 13.3 Prefetch, 13. Remote Playback Strategy

### Community 93 - "9. Local Music Discovery"
Cohesion: 0.50
Nodes (4): 9.1 MediaStore, 9.2 Local-source import behavior, 9.3 Local availability, 9. Local Music Discovery

### Community 94 - "Increment 3 — Queue, Repeat, and True Pure Shuffle"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 3 — Queue, Repeat, and True Pure Shuffle

### Community 95 - "Increment 5 — Telegram Music Source Selection and Indexing"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 5 — Telegram Music Source Selection and Indexing

### Community 96 - "Increment 6 — Unified Track Model and Deduplication"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 6 — Unified Track Model and Deduplication

### Community 97 - "Increment 7 — Offline Download Manager"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 7 — Offline Download Manager

### Community 98 - "Increment 9 — Library UX, Search, Favorites, and Playlists"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 9 — Library UX, Search, Favorites, and Playlists

### Community 99 - "Increment 10 — Listening History and Local Behavioral Data"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 10 — Listening History and Local Behavioral Data

### Community 100 - "Increment 11 — Smart Shuffle v1: Deterministic Adaptive Heuristics"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 11 — Smart Shuffle v1: Deterministic Adaptive Heuristics

### Community 101 - "Increment 13 — Resilience, Privacy, and Storage Hardening"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 13 — Resilience, Privacy, and Storage Hardening

### Community 103 - "Implementation checklist"
Cohesion: 0.15
Nodes (12): Ready, A. Source selection, B. Telegram persistence, C. Eligibility and metadata, D. Initial and incremental sync, E. Unified library UX, F. Automated verification, Implementation checklist (+4 more)

### Community 104 - ".match"
Cohesion: 0.28
Nodes (6): abs, IncomingTrackIdentity, TrackMatchCandidate, UnifiedTrackMatcher, UnifiedTrackMatcherTest, max

### Community 107 - "PlaybackServiceTest"
Cohesion: 0.38
Nodes (3): MediaController, T, PlaybackServiceTest

### Community 108 - "TelegramAudioMessageMapper.kt"
Cohesion: 0.29
Nodes (7): bestTitle(), cleanOrNull(), isSupportedAudioDocument(), TelegramAudioCandidate, toAudioCandidate(), TelegramAudioMessageMapperTest, locale

### Community 110 - "MusicLibraryRepository"
Cohesion: 0.19
Nodes (8): Flow, LibraryTrack, LibraryTrackAvailability, CLOUD, OFFLINE, UNAVAILABLE, LocalLibraryRefreshResult, MusicLibraryRepository

### Community 112 - "ScannedLocalTrack"
Cohesion: 0.31
Nodes (4): FakeLocalMediaScanner, LocalMediaScanner, MediaStoreScanner, ScannedLocalTrack

### Community 113 - "LocalMusicLibraryRepository"
Cohesion: 0.29
Nodes (3): Flow, LocalMusicLibraryRepository, toDomain()

### Community 114 - "PlaybackStatus"
Cohesion: 0.25
Nodes (8): PlaybackStatus, BUFFERING, DOWNLOADING, ERROR, IDLE, PAUSED, PLAYING, PREPARING

### Community 115 - "RoomListeningHistoryRepository"
Cohesion: 0.23
Nodes (8): ListeningEventEntity, ListeningSessionEntity, TrackPreferenceStatsEntity, TrackTimePreferenceEntity, ActivePlayback, PlaybackMode, RoomListeningHistoryRepository, Session

### Community 116 - "AudioSpectrum.kt"
Cohesion: 0.31
Nodes (9): AudioSpectrum(), Color, Modifier, SpectrumCanvas(), canvas, cornerradius, FloatArray, offset (+1 more)

### Community 117 - "RoomPlaylistRepository"
Cohesion: 0.16
Nodes (4): PlaylistDao, PlaylistEntity, PlaylistTrackEntity, RoomPlaylistRepository

### Community 118 - "bindMusicLibraryRepository"
Cohesion: 0.40
Nodes (5): bindMusicLibraryRepository(), bindPlaybackCatalog(), PlaybackCatalog, LocalMusicLibraryRepository, MusicLibraryRepository

### Community 119 - "TelegramChatKind"
Cohesion: 0.33
Nodes (6): TelegramChatKind, BASIC_GROUP, PRIVATE, SAVED_MESSAGES, SECRET, SUPERGROUP_OR_CHANNEL

### Community 121 - "Increment 6 — Unified Track Model and Deduplication"
Cohesion: 0.40
Nodes (4): Implementation, Increment 6 — Unified Track Model and Deduplication, Status, Verification

### Community 122 - "Increment 7 — Offline Download Manager"
Cohesion: 0.40
Nodes (4): Implementation, Increment 7 — Offline Download Manager, Status, Verification

### Community 123 - "Increment 8 — Telegram On-Demand Playback and Prefetch"
Cohesion: 0.40
Nodes (4): Implementation, Increment 8 — Telegram On-Demand Playback and Prefetch, Status, Verification

### Community 124 - "Increment 9 — Library UX, Search, Favorites, and Playlists"
Cohesion: 0.40
Nodes (4): Implementation, Increment 9 — Library UX, Search, Favorites, and Playlists, Status, Verification

### Community 126 - ".outcome"
Cohesion: 0.27
Nodes (3): EventTimeContext, ListeningEventSemantics, ListeningEventSemanticsTest

### Community 127 - "PlaybackInitiator"
Cohesion: 0.33
Nodes (6): PlaybackInitiator, PURE_SHUFFLE, QUEUE, SMART_SHUFFLE, SYSTEM_RESUME, USER

### Community 128 - "TimeBucket"
Cohesion: 0.29
Nodes (7): TimeBucket, AFTERNOON, EARLY_MORNING, EVENING, LATE_NIGHT, MORNING, NIGHT

### Community 131 - "bindRemoteTrackPlaybackResolver"
Cohesion: 0.67
Nodes (3): bindRemoteTrackPlaybackResolver(), RemoteTrackPlaybackResolver, TdLibRemoteTrackPlaybackResolver

### Community 135 - "bindTelegramRepository"
Cohesion: 0.67
Nodes (3): bindTelegramRepository(), TdLibTelegramRepository, TelegramRepository

### Community 136 - "Increment 10 — Listening History and Local Behavioral Data"
Cohesion: 0.40
Nodes (4): Implementation, Increment 10 — Listening History and Local Behavioral Data, Status, Verification

## Knowledge Gaps
- **394 isolated node(s):** `IDLE`, `PREPARING`, `DOWNLOADING`, `BUFFERING`, `PLAYING` (+389 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 723 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **44 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Track` connect `LibraryViewModel.kt` to `LocalMusicLibraryRepository.kt`, `PlaylistRepository`, `4. Source-Agnostic Training and Data Provenance`, `.observePlaylists`, `MusicLibraryRepository`, `MEOWZIX_SYSTEM_SPEC.md`, `LocalMusicLibraryRepository`, `9. Local Music Discovery`, `3. Core Product Decisions`?**
  _High betweenness centrality (0.213) - this node is a cross-community bridge._
- **Why does `27. Database Constraints and Invariants` connect `3. Core Product Decisions` to `MEOWZIX_SYSTEM_SPEC.md`, `LibraryViewModel.kt`?**
  _High betweenness centrality (0.099) - this node is a cross-community bridge._
- **Why does `6. Repository / Module Structure` connect `MEOWZIX_SYSTEM_SPEC.md` to `LibraryViewModel.kt`?**
  _High betweenness centrality (0.092) - this node is a cross-community bridge._
- **What connects `IDLE`, `PREPARING`, `DOWNLOADING` to the rest of the system?**
  _394 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `DataModule.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09425287356321839 - nodes in this community are weakly interconnected._
- **Should `HistoryDao` be split into smaller, more focused modules?**
  _Cohesion score 0.12554112554112554 - nodes in this community are weakly interconnected._
- **Should `TelegramRepository` be split into smaller, more focused modules?**
  _Cohesion score 0.1323529411764706 - nodes in this community are weakly interconnected._